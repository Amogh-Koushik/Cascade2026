import json
from collections import Counter
from pathlib import Path
from typing import Any

import pytest

from contracts.params import PARAMS_DIR, load_params
from engine.make_test_vectors import OUTPUT_PATH, build_document
from engine.vectors import VectorError, apply_patches, compare, run_vector

DOCUMENT: dict[str, Any] = json.loads(Path(OUTPUT_PATH).read_text(encoding="utf-8"))
VECTORS: list[dict[str, Any]] = DOCUMENT["vectors"]


@pytest.fixture(scope="module")
def vector_params() -> dict[str, Any]:
    return load_params(PARAMS_DIR / DOCUMENT["params_file"])


def test_vector_file_is_up_to_date() -> None:
    assert json.loads(json.dumps(build_document())) == DOCUMENT


def test_there_are_at_least_fifty_uniquely_named_vectors() -> None:
    ids = [vector["id"] for vector in VECTORS]

    assert len(ids) >= 50
    assert len(set(ids)) == len(ids)


@pytest.mark.parametrize("vector", VECTORS, ids=[vector["id"] for vector in VECTORS])
def test_replaying_a_vector_reproduces_its_expected_outputs(
    vector: dict[str, Any], vector_params: dict[str, Any]
) -> None:
    assert compare(vector["expected"], run_vector(vector, vector_params)) == []


def test_vectors_exercise_every_decision_rule() -> None:
    rules = Counter(vector["expected"]["decision"]["rule"] for vector in VECTORS)

    assert set(rules) == {
        "OVERRIDE",
        "FREE_STATE",
        "OBSERVE_MODE",
        "MIGHT_MATTER",
        "HOLD_BETTER",
        "DELIVER_BETTER",
        "EXPLORATION",
    }


def test_vectors_include_both_worth_paths_and_learned_counts() -> None:
    used_sender = {vector["expected"]["intermediate"]["used_sender"] for vector in VECTORS}
    with_events = [vector for vector in VECTORS if vector["events"]]

    assert used_sender == {True, False}
    assert len(with_events) >= 15


def test_the_epsilon_boundary_is_strict() -> None:
    at_epsilon = next(vector for vector in VECTORS if vector["id"] == "explore_at_epsilon")

    assert at_epsilon["expected"]["decision"]["explored"] is False


def test_compare_allows_small_float_drift_and_skips_explanations() -> None:
    expected = {"p_cs": 0.25, "l_mean": 3.0, "explanation": "Held: ...", "rule": "HOLD_BETTER"}
    actual = {
        "p_cs": 0.25 + 5e-7,
        "l_mean": 3.0005,
        "explanation": "different",
        "rule": "HOLD_BETTER",
    }

    assert compare(expected, actual) == []


@pytest.mark.parametrize(
    ("actual", "mismatch"),
    [
        ({"p_cs": 0.2501, "l_mean": 3.0, "rule": "HOLD_BETTER"}, "p_cs"),
        ({"p_cs": 0.25, "l_mean": 3.01, "rule": "HOLD_BETTER"}, "l_mean"),
        ({"p_cs": 0.25, "l_mean": 3.0, "rule": "MIGHT_MATTER"}, "rule"),
        ({"p_cs": 0.25, "l_mean": 3.0}, "keys differ"),
    ],
)
def test_compare_reports_real_differences(actual: dict[str, Any], mismatch: str) -> None:
    expected = {"p_cs": 0.25, "l_mean": 3.0, "rule": "HOLD_BETTER"}

    problems = compare(expected, actual)

    assert len(problems) == 1
    assert mismatch in problems[0]


def test_compare_treats_true_and_one_as_different() -> None:
    assert compare({"explored": True}, {"explored": 1}) != []


def test_patches_must_point_at_existing_constants(vector_params: dict[str, Any]) -> None:
    with pytest.raises(VectorError):
        apply_patches(vector_params, [{"path": ["policy", "lambada"], "value": 2.0}])


def test_patches_never_change_the_original_params(vector_params: dict[str, Any]) -> None:
    before = vector_params["policy"]["w"]

    apply_patches(vector_params, [{"path": ["policy", "w"], "value": 99.0}])

    assert vector_params["policy"]["w"] == before
