# Gemini backend: Google's hosted models through the Generative Language REST API, usable on
# the free tier. Rate-limit and overload replies are retried after the delay the server asks
# for, so a long run pauses instead of failing.

import os
import re
import time
from collections.abc import Callable
from typing import Any

import httpx

from agents.llm_errors import LLMError

DEFAULT_MODEL = "gemini-3.5-flash"
API_ROOT = "https://generativelanguage.googleapis.com/v1beta/models"

REQUEST_TIMEOUT_S = 300.0
MAX_OUTPUT_TOKENS = 32_768
MAX_RETRIES = 5
RETRY_STATUSES = frozenset({429, 500, 503})
DEFAULT_RETRY_S = 10.0
MAX_RETRY_S = 60.0

# A wait longer than this means the daily quota is used up, so waiting would not help.
GIVE_UP_AFTER_S = 120.0

BLOCKED_FINISH_REASONS = frozenset(
    {"SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "OTHER"}
)


def model_name() -> str:
    return os.environ.get("GEMINI_MODEL", DEFAULT_MODEL)


def _api_key() -> str:
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not key:
        raise LLMError(
            "GEMINI_API_KEY is not set. Put it in .env and run with: uv run --env-file .env ..."
        )
    return key


def request_body(system: str, prompt: str, json_output: bool, temperature: float) -> dict[str, Any]:
    config: dict[str, Any] = {"temperature": temperature, "maxOutputTokens": MAX_OUTPUT_TOKENS}
    if json_output:
        config["responseMimeType"] = "application/json"
    return {
        "systemInstruction": {"parts": [{"text": system}]},
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": config,
    }


def retry_delay_s(response: httpx.Response, attempt: int) -> float:
    # The server says how long to wait, either in the body ("retryDelay": "23s") or in a
    # Retry-After header. Without either, back off exponentially.
    match = re.search(r'"retryDelay":\s*"(\d+(?:\.\d+)?)s"', response.text)
    if match:
        return float(match.group(1))
    header = response.headers.get("retry-after")
    if header is not None:
        try:
            return float(header)
        except ValueError:
            pass
    return min(DEFAULT_RETRY_S * 2**attempt, MAX_RETRY_S)


def reply_text(data: dict[str, Any]) -> str:
    block_reason = data.get("promptFeedback", {}).get("blockReason")
    if block_reason:
        raise LLMError(f"Gemini blocked the prompt ({block_reason})")

    candidates = data.get("candidates") or []
    if not candidates:
        raise LLMError("Gemini returned no candidates")
    candidate = candidates[0]
    finish = candidate.get("finishReason")
    if finish == "MAX_TOKENS":
        raise LLMError("Gemini's output was cut off at the token limit; split the request")
    if finish in BLOCKED_FINISH_REASONS:
        raise LLMError(f"Gemini stopped the reply ({finish})")

    # Thought summaries, when present, are marked and are not part of the answer.
    parts = candidate.get("content", {}).get("parts", [])
    text = "".join(part.get("text", "") for part in parts if not part.get("thought"))
    if not text.strip():
        raise LLMError("Gemini returned an empty reply")
    return text


def ask(
    system: str,
    prompt: str,
    *,
    json_output: bool,
    temperature: float,
    client: httpx.Client | None = None,
    sleep: Callable[[float], None] = time.sleep,
) -> str:
    url = f"{API_ROOT}/{model_name()}:generateContent"
    headers = {"x-goog-api-key": _api_key()}
    body = request_body(system, prompt, json_output, temperature)
    http = client or httpx.Client(timeout=REQUEST_TIMEOUT_S)

    try:
        for attempt in range(MAX_RETRIES + 1):
            response = _post(http, url, headers, body)
            if response.status_code == 200:
                return reply_text(response.json())
            if response.status_code in RETRY_STATUSES and attempt < MAX_RETRIES:
                delay = retry_delay_s(response, attempt)
                if delay > GIVE_UP_AFTER_S:
                    raise LLMError(
                        f"Gemini asks to wait {delay:.0f} s, so the free-tier quota is likely "
                        "used up for today. Try later or switch PRATIBIMB_LLM to ollama."
                    )
                sleep(delay)
                continue
            raise LLMError(_failure_message(response))
    finally:
        if client is None:
            http.close()
    raise LLMError("Gemini kept refusing the request after every retry")


def _post(
    http: httpx.Client, url: str, headers: dict[str, str], body: dict[str, Any]
) -> httpx.Response:
    try:
        return http.post(url, headers=headers, json=body)
    except httpx.ConnectError as error:
        raise LLMError("Cannot reach the Gemini API. Check the internet connection.") from error
    except httpx.TimeoutException as error:
        raise LLMError(f"Gemini did not answer within {REQUEST_TIMEOUT_S:.0f} s") from error


def _failure_message(response: httpx.Response) -> str:
    status = response.status_code
    if status in (401, 403):
        return f"Gemini rejected the API key ({status}). Check GEMINI_API_KEY in .env."
    if status == 404:
        return (
            f"Gemini cannot use model '{model_name()}': {_api_message(response)} "
            "Set GEMINI_MODEL in .env."
        )
    if status == 429:
        return "Gemini rate limit still hit after every retry. Wait a minute and run again."
    return f"Gemini returned {status}: {_api_message(response)}"


def _api_message(response: httpx.Response) -> str:
    # Google explains most failures in error.message, for example a retired model's successor.
    try:
        message = response.json()["error"]["message"]
    except (ValueError, KeyError, TypeError):
        return response.text[:300]
    return str(message)[:300]
