import copy
import json
from typing import Any

import pytest

from agents.feature_builder import (
    RawNotification,
    build_features,
    recent_pings_from_sender,
    sender_hash,
)
from agents.redteam_runner import (
    HANDWRITTEN_PATH,
    load_attacks,
    render_report,
    run_attack,
    run_attacks,
    summarise,
)
from agents.redteam_schema import ATTACK_TYPES, AttackSetError, validate_attack_set
from engine.contract import Action, Category, DecisionRule


def note(**fields: Any) -> dict[str, Any]:
    values: dict[str, Any] = {
        "at_min": 0,
        "package_name": "com.whatsapp",
        "title": "Mom",
        "text": "Are you free?",
        "sender": "Mom",
        "is_conversation": True,
        "group_chat": False,
        "is_ongoing": False,
        "android_category": None,
        "important": True,
    }
    values.update(fields)
    return values


def attack(*notifications: dict[str, Any], attack_id: str = "t1") -> dict[str, Any]:
    return {
        "id": attack_id,
        "attack_type": "buried_urgent",
        "source": "hand_written",
        "description": "test attack",
        "notifications": list(notifications) or [note()],
    }


def test_the_hand_written_set_is_valid_and_covers_every_type() -> None:
    attacks = load_attacks([HANDWRITTEN_PATH])

    assert len(attacks) == 12
    assert {a["attack_type"] for a in attacks} == set(ATTACK_TYPES)


@pytest.mark.parametrize(
    ("change", "problem"),
    [
        (lambda a: a.update(attack_type="phishing"), "attack_type"),
        (lambda a: a.update(source="llm"), "source"),
        (lambda a: a.update(notifications=[]), "1 to 8 notifications"),
        (lambda a: a["notifications"][0].pop("important"), "missing 'important'"),
        (lambda a: a["notifications"][0].update(important="yes"), "true or false"),
        (lambda a: a["notifications"][0].update(at_min=-1), "at_min"),
        (lambda a: a["notifications"][0].update(text=""), "non-empty string"),
        (
            lambda a: a["notifications"][0].update(group_chat=True, is_conversation=False),
            "group chat must also be a conversation",
        ),
        (
            lambda a: a["notifications"].append(note(at_min=-0.5)),
            "at_min",
        ),
        (
            lambda a: a.update(notifications=[note(at_min=5), note(at_min=2)]),
            "time order",
        ),
    ],
)
def test_malformed_attacks_are_rejected(change: Any, problem: str) -> None:
    broken = attack()
    change(broken)

    with pytest.raises(AttackSetError) as error:
        validate_attack_set({"attacks": [broken]})

    assert any(problem in p for p in error.value.problems)


def test_duplicate_attack_ids_are_rejected() -> None:
    with pytest.raises(AttackSetError, match="duplicate attack ids"):
        validate_attack_set({"attacks": [attack(), attack()]})


def test_sender_hashes_are_stable_and_hide_the_name() -> None:
    assert sender_hash("Mom") == sender_hash("Mom")
    assert sender_hash("Mom") != sender_hash("Papa")
    assert "Mom" not in str(sender_hash("Mom"))
    assert sender_hash(None) is None


@pytest.mark.parametrize(
    ("earlier", "expected"),
    [
        ([note(at_min=0)], 1),
        ([note(at_min=0), note(at_min=1)], 2),
        ([note(at_min=0, sender="Papa")], 0),
        ([note(at_min=0, is_conversation=False)], 0),
    ],
)
def test_recent_pings_count_only_the_same_person_within_the_window(
    earlier: list[dict[str, Any]], expected: int
) -> None:
    current = RawNotification.from_dict(note(at_min=2))
    previous = [RawNotification.from_dict(n) for n in earlier]

    assert recent_pings_from_sender(current, previous, window_min=3) == expected


def test_pings_older_than_the_window_do_not_count() -> None:
    current = RawNotification.from_dict(note(at_min=10))
    previous = [RawNotification.from_dict(note(at_min=2))]

    assert recent_pings_from_sender(current, previous, window_min=3) == 0


def test_features_take_category_and_lexicon_hits_from_the_params(
    params: dict[str, Any],
) -> None:
    raw = RawNotification.from_dict(
        note(package_name="com.flipkart.android", text="OTP 482913 unlocks 70% off", sender=None)
    )

    features = build_features(raw, [], params, key="k")

    assert features.category == Category.SHOPPING_PROMO
    assert features.bait_hit and features.otp_pattern
    assert features.sender_hash is None


def test_a_second_message_from_mom_is_a_repeated_ping(params: dict[str, Any]) -> None:
    results = run_attack(attack(note(at_min=0), note(at_min=1)), params)

    assert results[1].rule == DecisionRule.OVERRIDE
    assert results[1].reason == "Always delivered: repeated ping"


def test_scoring_marks_held_important_items_and_delivered_bait(params: dict[str, Any]) -> None:
    like = note(
        package_name="com.instagram.android",
        title="rohan_k",
        text="Rohan liked your reel",
        sender=None,
        is_conversation=False,
    )
    held_but_important = like | {"important": True}
    delivered_but_unimportant = note(important=False, text="Call me, urgent")

    results = run_attacks(
        [
            attack(held_but_important, attack_id="a"),
            attack(delivered_but_unimportant, attack_id="b"),
        ],
        params,
    )

    assert results[0].action == Action.HOLD and results[0].urgent_missed
    assert results[1].action == Action.DELIVER and results[1].bait_delivered


def test_summary_and_report_count_every_miss(params: dict[str, Any]) -> None:
    results = run_attacks(load_attacks([HANDWRITTEN_PATH]), params)
    summaries = summarise(results)
    report = render_report(results, "twin_params_example.json")

    misses = [r for r in results if r.urgent_missed or r.bait_delivered]
    assert sum(s.notifications for s in summaries) == len(results)
    assert f"## Misses ({len(misses)})" in report
    for miss in misses:
        assert miss.attack_id in report


def test_a_report_with_no_misses_says_so(params: dict[str, Any]) -> None:
    results = run_attacks([attack(note(text="Call me, urgent"))], params)

    assert "## Misses (0)\n\nNone." in render_report(results, "p.json")


def test_runs_are_deterministic(params: dict[str, Any]) -> None:
    attacks = load_attacks([HANDWRITTEN_PATH])

    assert run_attacks(attacks, params) == run_attacks(copy.deepcopy(attacks), params)


def test_ids_must_be_unique_across_files(tmp_path: Any) -> None:
    first, second = tmp_path / "a.json", tmp_path / "b.json"
    for path in (first, second):
        path.write_text(json.dumps({"attacks": [attack()]}))

    with pytest.raises(AttackSetError, match="duplicate"):
        load_attacks([first, second])
