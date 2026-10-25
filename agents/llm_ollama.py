# Ollama backend: a model running locally on this laptop.

import os

import httpx

from agents.llm_errors import LLMError

DEFAULT_MODEL = "qwen2.5:14b"
DEFAULT_HOST = "http://localhost:11434"

# Local generation of a long text bank can take minutes on a laptop GPU.
REQUEST_TIMEOUT_S = 600.0


def model_name() -> str:
    return os.environ.get("PRATIBIMB_MODEL", DEFAULT_MODEL)


def ollama_host() -> str:
    return os.environ.get("OLLAMA_HOST_URL", DEFAULT_HOST).rstrip("/")


def ask(
    system: str,
    prompt: str,
    *,
    json_output: bool,
    temperature: float,
    context_tokens: int,
) -> str:
    body: dict[str, object] = {
        "model": model_name(),
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ],
        "stream": False,
        # Ollama's default context window is small; persona and text-bank prompts need more.
        "options": {"temperature": temperature, "num_ctx": context_tokens},
    }
    if json_output:
        # Constrains the reply to valid JSON, so agents can parse it without cleanup.
        body["format"] = "json"

    try:
        response = httpx.post(f"{ollama_host()}/api/chat", json=body, timeout=REQUEST_TIMEOUT_S)
    except httpx.ConnectError as error:
        raise LLMError(
            f"Cannot reach Ollama at {ollama_host()}. Start the Ollama app and try again."
        ) from error
    except httpx.TimeoutException as error:
        raise LLMError(f"Ollama did not answer within {REQUEST_TIMEOUT_S:.0f} s") from error

    if response.status_code == 404:
        raise LLMError(f"Model '{model_name()}' is not downloaded. Run: ollama pull {model_name()}")
    if response.status_code != 200:
        raise LLMError(f"Ollama returned {response.status_code}: {response.text[:300]}")

    reply = response.json()
    if reply.get("done_reason") == "length":
        raise LLMError("Output was cut off at the context limit; raise context_tokens or split it")
    return str(reply["message"]["content"])
