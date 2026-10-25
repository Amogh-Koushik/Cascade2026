import copy
from pathlib import Path
from typing import Any

import pytest

from contracts.params import load_params
from engine.engine import TwinEngine

EXAMPLE_PARAMS_PATH = (
    Path(__file__).resolve().parent.parent / "contracts" / "params" / "twin_params_example.json"
)


@pytest.fixture(scope="session")
def base_params() -> dict[str, Any]:
    return load_params(EXAMPLE_PARAMS_PATH)


@pytest.fixture
def params(base_params: dict[str, Any]) -> dict[str, Any]:
    # A fresh copy per test, so a test can tweak a constant without leaking into the next.
    return copy.deepcopy(base_params)


@pytest.fixture
def twin(params: dict[str, Any]) -> TwinEngine:
    return TwinEngine(params)
