import json
from collections.abc import Callable
from typing import Any

import httpx
import pytest

from agents import llm, llm_gemini
from agents.llm_errors import LLMError


def ok(text: str, finish: str = "STOP") -> httpx.Response:
    body = {"candidates": [{"content": {"parts": [{"text": text}]}, "finishReason": finish}]}
    return httpx.Response(200, json=body)


def rate_limited(delay: str = "2s") -> httpx.Response:
    details = [{"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": delay}]
    return httpx.Response(429, json={"error": {"code": 429, "details": details}})


class FakeGemini:
    # Answers each request with the next scripted response and keeps what was sent.
    def __init__(self, *responses: httpx.Response) -> None:
        self.responses = list(responses)
        self.requests: list[httpx.Request] = []
        self.client = httpx.Client(transport=httpx.MockTransport(self.handle))

    def handle(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        return self.responses.pop(0)


@pytest.fixture
def gemini_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PRATIBIMB_LLM", "gemini")
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    monkeypatch.delenv("GEMINI_MODEL", raising=False)


def ask(fake: FakeGemini, sleeps: list[float] | None = None) -> str:
    record: Callable[[float], None] = sleeps.append if sleeps is not None else lambda _: None
    return llm_gemini.ask(
        "system", "prompt", json_output=True, temperature=0.5, client=fake.client, sleep=record
    )


def test_a_reply_is_returned_and_the_request_is_well_formed(gemini_env: None) -> None:
    fake = FakeGemini(ok('{"n": 1}'))

    assert ask(fake) == '{"n": 1}'
    request = fake.requests[0]
    body = json.loads(request.content)
    assert request.url.path.endswith("/gemini-3.5-flash:generateContent")
    assert request.headers["x-goog-api-key"] == "test-key"
    assert "key=" not in str(request.url)
    assert body["systemInstruction"]["parts"][0]["text"] == "system"
    assert body["generationConfig"]["responseMimeType"] == "application/json"
    assert body["generationConfig"]["temperature"] == 0.5


def test_rate_limits_wait_the_delay_the_server_asks_for(gemini_env: None) -> None:
    fake = FakeGemini(rate_limited("7s"), rate_limited("3.5s"), ok("done"))
    sleeps: list[float] = []

    assert ask(fake, sleeps) == "done"
    assert sleeps == [7.0, 3.5]


def test_overload_without_a_delay_backs_off_exponentially(gemini_env: None) -> None:
    fake = FakeGemini(httpx.Response(503), httpx.Response(503), ok("done"))
    sleeps: list[float] = []

    assert ask(fake, sleeps) == "done"
    assert sleeps == [10.0, 20.0]


def test_a_retry_after_header_is_honoured(gemini_env: None) -> None:
    fake = FakeGemini(httpx.Response(429, headers={"retry-after": "4"}), ok("done"))
    sleeps: list[float] = []

    assert ask(fake, sleeps) == "done"
    assert sleeps == [4.0]


def test_a_very_long_wait_means_the_daily_quota_is_gone(gemini_env: None) -> None:
    fake = FakeGemini(rate_limited("3600s"))

    with pytest.raises(LLMError, match="quota is likely used up"):
        ask(fake)


def test_retries_stop_after_the_limit(gemini_env: None) -> None:
    fake = FakeGemini(*[rate_limited("1s") for _ in range(llm_gemini.MAX_RETRIES + 1)])

    with pytest.raises(LLMError, match="rate limit still hit"):
        ask(fake)
    assert len(fake.requests) == llm_gemini.MAX_RETRIES + 1


@pytest.mark.parametrize(
    ("response", "message"),
    [
        (httpx.Response(403), "rejected the API key"),
        (
            httpx.Response(404, json={"error": {"message": "Use models/gemini-next instead."}}),
            "cannot use model .* Use models/gemini-next instead",
        ),
        (httpx.Response(400, text="bad request"), "returned 400"),
        (ok("partial", finish="MAX_TOKENS"), "cut off"),
        (ok("nope", finish="SAFETY"), "stopped the reply"),
        (ok("   "), "empty reply"),
        (httpx.Response(200, json={"promptFeedback": {"blockReason": "SAFETY"}}), "blocked"),
        (httpx.Response(200, json={"candidates": []}), "no candidates"),
    ],
)
def test_failures_become_clear_errors(
    gemini_env: None, response: httpx.Response, message: str
) -> None:
    with pytest.raises(LLMError, match=message):
        ask(FakeGemini(response))


def test_thought_parts_are_left_out_of_the_answer() -> None:
    data: dict[str, Any] = {
        "candidates": [
            {
                "content": {"parts": [{"text": "thinking...", "thought": True}, {"text": "{}"}]},
                "finishReason": "STOP",
            }
        ]
    }

    assert llm_gemini.reply_text(data) == "{}"


def test_a_missing_key_says_how_to_fix_it(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)

    with pytest.raises(LLMError, match=r"--env-file \.env"):
        llm_gemini.ask("s", "p", json_output=True, temperature=0.5)


def test_the_backend_switch_names_the_model(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PRATIBIMB_LLM", "gemini")
    monkeypatch.setenv("GEMINI_MODEL", "gemini-test")
    assert llm.model_name() == "gemini:gemini-test"

    monkeypatch.setenv("PRATIBIMB_LLM", "ollama")
    monkeypatch.setenv("PRATIBIMB_MODEL", "qwen2.5:7b")
    assert llm.model_name() == "ollama:qwen2.5:7b"


def test_an_unknown_backend_is_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PRATIBIMB_LLM", "chatgpt")

    with pytest.raises(LLMError, match="PRATIBIMB_LLM must be one of"):
        llm.ask("s", "p")
