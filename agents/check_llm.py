# One-call check that the local Ollama model answers before the agents depend on it.
#
# Run: uv run python -m agents.check_llm

import sys
import time

from agents.llm import LLMError, ask, model_name, ollama_host

EXPECTED_REPLY = "pratibimb-ok"


def main() -> int:
    print(f"Model: {model_name()} at {ollama_host()}")
    started = time.perf_counter()
    try:
        reply = ask(
            system="You are a connectivity check. Follow the instruction exactly.",
            prompt=f"Reply with exactly this text and nothing else: {EXPECTED_REPLY}",
            temperature=0.0,
        )
    except LLMError as error:
        print(f"Failed: {error}")
        return 1

    elapsed = time.perf_counter() - started
    if EXPECTED_REPLY not in reply:
        print(f"Model answered, but not as asked: {reply!r}")
        return 1
    print(f"OK: reply received in {elapsed:.1f} s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
