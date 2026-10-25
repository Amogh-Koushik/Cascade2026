# Small shape checks shared by the validators for LLM output. Each returns a list of problems,
# written so they can be shown to a person or fed back to the model as-is.

import math
from typing import Any


def is_number(value: Any) -> bool:
    # bool is a subclass of int in Python, and true/false is never a valid number here.
    return isinstance(value, int | float) and not isinstance(value, bool) and math.isfinite(value)


def key_problems(value: Any, expected: tuple[str, ...], where: str) -> list[str]:
    if not isinstance(value, dict):
        return [f"{where}: must be an object"]
    problems = [f"{where}: missing {key!r}" for key in expected if key not in value]
    problems += [f"{where}: unknown key {key!r}" for key in value if key not in expected]
    return problems


def text_problems(value: Any, where: str, max_length: int) -> list[str]:
    if not isinstance(value, str) or not value.strip():
        return [f"{where}: must be a non-empty string"]
    if len(value) > max_length:
        return [f"{where}: longer than {max_length} characters"]
    return []


def bool_problems(record: dict[str, Any], keys: tuple[str, ...], where: str) -> list[str]:
    return [
        f"{where}.{key}: must be true or false" for key in keys if not isinstance(record[key], bool)
    ]
