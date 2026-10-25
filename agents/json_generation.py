# The loop every agent uses: ask the model for JSON, validate it, and if it is rejected, ask
# again with the exact problems appended, up to a few attempts. Nothing the model writes is used
# until it passes its validator. The model is passed in as a plain function, so tests can swap
# in a scripted fake and the agents never need a running model to be tested.

import json
import logging
from collections.abc import Callable
from typing import Any

from agents import llm

logger = logging.getLogger(__name__)

# (system prompt, user prompt) -> the model's reply text.
AskFn = Callable[[str, str], str]

MAX_ATTEMPTS = 3
MAX_FEEDBACK_PROBLEMS = 15


class GenerationError(RuntimeError):
    pass


def model_json(temperature: float = 0.7, context_tokens: int = 8192) -> AskFn:
    def ask(system: str, prompt: str) -> str:
        return llm.ask(
            system,
            prompt,
            json_output=True,
            temperature=temperature,
            context_tokens=context_tokens,
        )

    return ask


def extract_json(reply: str) -> Any:
    # Models sometimes wrap JSON in ``` fences or add a sentence; keep only the outermost object.
    start, end = reply.find("{"), reply.rfind("}")
    if start == -1 or end < start:
        raise json.JSONDecodeError("No JSON object in the reply", reply, 0)
    return json.loads(reply[start : end + 1])


def _problems_of(error: ValueError) -> list[str]:
    problems = getattr(error, "problems", None)
    if isinstance(problems, list) and problems:
        return [str(problem) for problem in problems]
    return [str(error)]


def generate_json[T](
    ask: AskFn,
    system: str,
    prompt: str,
    validate: Callable[[Any], T],
    label: str,
    max_attempts: int = MAX_ATTEMPTS,
) -> T:
    feedback = ""
    problems: list[str] = []
    for attempt in range(1, max_attempts + 1):
        reply = ask(system, prompt + feedback)
        try:
            return validate(extract_json(reply))
        except ValueError as error:
            # json.JSONDecodeError and every validator error are ValueErrors.
            problems = _problems_of(error)
            logger.warning("%s: attempt %d rejected (%d problems)", label, attempt, len(problems))
            shown = problems[:MAX_FEEDBACK_PROBLEMS]
            feedback = (
                "\n\nYour previous reply was rejected for these reasons:\n- "
                + "\n- ".join(shown)
                + "\nReply again with the complete corrected JSON object and nothing else."
            )
    raise GenerationError(
        f"{label}: no valid reply after {max_attempts} attempts. Last problems:\n  "
        + "\n  ".join(problems)
    )
