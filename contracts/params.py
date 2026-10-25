# Loading and validating twin_params files against the shared JSON schema.
# Used by the tests, the persona and fit tools before they write a params file, and anyone
# who wants to check a hand-edited file: uv run python -m contracts.params <file.json>

import json
import sys
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

CONTRACTS_DIR = Path(__file__).resolve().parent
SCHEMA_PATH = CONTRACTS_DIR / "twin_params.schema.json"
PARAMS_DIR = CONTRACTS_DIR / "params"
DRAW_BANK_PATH = CONTRACTS_DIR / "draw_bank.json"


SCHEMA: dict[str, Any] = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))

# Read from the schema so every Python tool shares one list with the params files.
CATEGORIES: tuple[str, ...] = tuple(SCHEMA["$defs"]["category"]["enum"])
PHONE_STATES: tuple[str, ...] = tuple(
    SCHEMA["properties"]["pull"]["properties"]["state"]["required"]
)


class ParamsError(ValueError):
    pass


def _validator() -> Draft202012Validator:
    return Draft202012Validator(SCHEMA)


def validate_params(params: dict[str, Any]) -> None:
    errors = sorted(_validator().iter_errors(params), key=lambda error: list(error.path))
    if errors:
        lines = [f"{'/'.join(map(str, e.path)) or '<root>'}: {e.message}" for e in errors]
        raise ParamsError("Invalid twin params:\n  " + "\n  ".join(lines))


def load_params(path: Path) -> dict[str, Any]:
    try:
        params = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ParamsError(f"{path} is not valid JSON: {error}") from error
    validate_params(params)
    return params


def load_draw_bank() -> list[list[float]]:
    bank = json.loads(DRAW_BANK_PATH.read_text(encoding="utf-8"))
    return bank["draws"]


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python -m contracts.params <twin_params.json>")
        return 2
    try:
        load_params(Path(sys.argv[1]))
    except ParamsError as error:
        print(error)
        return 1
    print("Valid")
    return 0


if __name__ == "__main__":
    sys.exit(main())
