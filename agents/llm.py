# The one entry point every laptop-side agent (persona, text bank, red-team, coach) uses to
# reach a model. PRATIBIMB_LLM picks the backend: "ollama" for a local model, "gemini" for
# Google's hosted API. These agents only run at development time; no LLM is ever on the
# phone's decision path ("LLMs advise, maths decides").

import os

from agents import llm_gemini, llm_ollama
from agents.llm_errors import LLMError

BACKENDS = ("ollama", "gemini")

__all__ = ["BACKENDS", "LLMError", "ask", "backend", "model_name"]


def backend() -> str:
    name = os.environ.get("PRATIBIMB_LLM", "ollama").strip().lower()
    if name not in BACKENDS:
        raise LLMError(f"PRATIBIMB_LLM must be one of {BACKENDS}, got {name!r}")
    return name


def model_name() -> str:
    module = llm_gemini if backend() == "gemini" else llm_ollama
    return f"{backend()}:{module.model_name()}"


def ask(
    system: str,
    prompt: str,
    *,
    json_output: bool = False,
    temperature: float = 0.7,
    context_tokens: int = 8192,
) -> str:
    if backend() == "gemini":
        return llm_gemini.ask(system, prompt, json_output=json_output, temperature=temperature)
    return llm_ollama.ask(
        system,
        prompt,
        json_output=json_output,
        temperature=temperature,
        context_tokens=context_tokens,
    )
