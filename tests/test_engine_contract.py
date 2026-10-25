import dataclasses
import json
from enum import StrEnum
from pathlib import Path

import pytest

from engine.contract import (
    Action,
    Autonomy,
    Category,
    Decision,
    DecisionRule,
    FatigueRisk,
    FutureCard,
    NotificationFeatures,
    PhoneState,
    PullOutcome,
    TwinContext,
    WorthLabel,
    WorthLabelKind,
    feedback_from_dict,
)
from engine.engine import TwinEngine
from tests.kotlin_contract import json_key, kotlin_category_keys, kotlin_enum, kotlin_fields

SAMPLES_PATH = Path(__file__).resolve().parent.parent / "contracts" / "sample_decisions.json"


@pytest.mark.parametrize(
    "python_class",
    [NotificationFeatures, TwinContext, Decision, FutureCard, PullOutcome, WorthLabel],
)
def test_python_fields_match_the_kotlin_contract(python_class: type) -> None:
    python_names = [field.name for field in dataclasses.fields(python_class)]
    kotlin_names = [json_key(name) for name in kotlin_fields(python_class.__name__)]

    assert python_names == kotlin_names


@pytest.mark.parametrize(
    "python_enum",
    [PhoneState, Autonomy, Action, FatigueRisk, DecisionRule, WorthLabelKind],
)
def test_python_enums_match_the_kotlin_contract(python_enum: type[StrEnum]) -> None:
    assert [member.value for member in python_enum] == kotlin_enum(python_enum.__name__)


def test_python_categories_match_the_kotlin_keys() -> None:
    assert [category.value for category in Category] == kotlin_category_keys()


def test_unknown_category_keys_fall_back_to_other() -> None:
    assert Category.from_key("games") is Category.OTHER
    assert Category.from_key(None) is Category.OTHER


def test_only_not_worth_it_and_correct_hold_are_negative_labels() -> None:
    negatives = {kind for kind in WorthLabelKind if not kind.positive}

    assert negatives == {WorthLabelKind.NOT_WORTH_IT, WorthLabelKind.CORRECT_HOLD}


def test_feedback_events_parse_from_their_json_form() -> None:
    pull = feedback_from_dict(
        {
            "type": "pull_outcome",
            "category": "social",
            "state": "FOCUS_STEADY",
            "opened": True,
            "minutes_lost": 4.0,
            "released_from_tray": False,
        }
    )
    worth = feedback_from_dict(
        {"type": "worth_label", "package_name": "x", "sender_hash": None, "label": "WORTH_IT"}
    )

    assert pull == PullOutcome(Category.SOCIAL, PhoneState.FOCUS_STEADY, True, 4.0, False)
    assert worth == WorthLabel("x", None, WorthLabelKind.WORTH_IT)


def test_feedback_with_an_unknown_type_is_rejected() -> None:
    with pytest.raises(ValueError, match="Unknown feedback event type"):
        feedback_from_dict({"type": "vibes"})


def test_features_with_a_missing_key_are_rejected() -> None:
    with pytest.raises(ValueError, match="missing keys"):
        NotificationFeatures.from_dict({"key": "n1"})


def test_engine_agrees_with_every_sample_scenario(twin: TwinEngine) -> None:
    scenarios = json.loads(SAMPLES_PATH.read_text(encoding="utf-8"))["scenarios"]
    for scenario in scenarios:
        decision = twin.decide(
            NotificationFeatures.from_dict(scenario["features"]),
            TwinContext.from_dict(scenario["context"]),
        )
        expected = scenario["decision"]
        assert decision.action == expected["action"], scenario["id"]
        assert decision.rule == expected["rule"], scenario["id"]


def test_a_decision_serialises_with_contract_keys(twin: TwinEngine) -> None:
    scenarios = json.loads(SAMPLES_PATH.read_text(encoding="utf-8"))["scenarios"]
    first = scenarios[0]
    decision = twin.decide(
        NotificationFeatures.from_dict(first["features"]),
        TwinContext.from_dict(first["context"]),
    )

    as_json = json.loads(json.dumps(decision.to_dict()))

    assert list(as_json) == list(first["decision"])
    assert list(as_json["card"]) == list(first["decision"]["card"])
