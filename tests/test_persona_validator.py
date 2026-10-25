import copy
import re
from collections.abc import Callable
from typing import Any

import pytest

from agents import persona_ranges as ranges
from agents.persona_briefs import PERSONA_BRIEFS, PERSONA_NAMES
from agents.persona_validator import PersonaError, validate_persona, validate_personas
from contracts.params import CATEGORIES, PHONE_STATES


def valid_persona(name: str = "student_coder") -> dict[str, Any]:
    return {
        "persona": name,
        "description": "Studies late, scrolls reels when stuck.",
        "pull": {
            "r_c": dict.fromkeys(CATEGORIES, 1.0),
            "r_s": {"FREE": 1.0, "FOCUS_STEADY": 0.8, "FOCUS_RESTLESS": 1.3},
            "delta_s": {"FREE": 0.0, "FOCUS_STEADY": 0.0, "FOCUS_RESTLESS": 0.2},
        },
        "notifications_per_hour": {category: [2.0] * 24 for category in CATEGORIES},
        "important_share": dict.fromkeys(CATEGORIES, 0.05) | {"messaging": 0.2},
        "focus_schedule": [
            {"start": "21:00", "duration_min": 50},
            {"start": "22:00", "duration_min": 90},
        ],
    }


def edited(change: Callable[[dict[str, Any]], None]) -> dict[str, Any]:
    persona = valid_persona()
    change(persona)
    return persona


def test_every_category_has_an_important_share_range() -> None:
    assert set(ranges.IMPORTANT_SHARE) == set(CATEGORIES)


def test_there_is_a_state_range_for_every_phone_state() -> None:
    assert set(ranges.R_S) == set(PHONE_STATES) == set(ranges.DELTA_S)


def test_there_are_three_named_briefs() -> None:
    assert PERSONA_NAMES == ("student_coder", "night_shift_nurse", "content_creator")
    for name, brief in PERSONA_BRIEFS.items():
        assert re.fullmatch(r"[a-z][a-z0-9_]{0,40}", name)
        assert len(brief.split()) > 40


def test_a_valid_persona_passes_unchanged() -> None:
    raw = valid_persona()

    report = validate_persona(raw)

    assert report.adjustments == []
    assert report.persona == raw


def test_validation_never_mutates_its_input() -> None:
    raw = edited(lambda p: p["pull"]["r_c"].update(social=9.0))
    before = copy.deepcopy(raw)

    validate_persona(raw)

    assert raw == before


@pytest.mark.parametrize(
    ("change", "expected_problem"),
    [
        (lambda p: p.pop("pull"), "missing 'pull'"),
        (lambda p: p.update(mood="happy"), "unknown key 'mood'"),
        (lambda p: p.update(persona="astronaut"), "unknown name"),
        (lambda p: p.update(description="  "), "description"),
        (lambda p: p["pull"]["r_c"].pop("news"), "pull.r_c: missing 'news'"),
        (lambda p: p["pull"]["r_c"].update(games=1.0), "unknown key 'games'"),
        (lambda p: p["pull"]["r_s"].update(FREE="high"), "pull.r_s.FREE: must be a number"),
        (lambda p: p["pull"]["r_c"].update(social=True), "pull.r_c.social: must be a number"),
        (lambda p: p["pull"]["r_c"].update(social=float("nan")), "must be a number"),
        (
            lambda p: p["notifications_per_hour"].update(social=[1.0] * 23),
            "list of 24 numbers",
        ),
        (
            lambda p: p["notifications_per_hour"]["news"].__setitem__(5, "many"),
            "every hour must be a number",
        ),
        (lambda p: p.update(focus_schedule=[]), "needs 1 to 6 sessions"),
        (lambda p: p["focus_schedule"][0].update(start="25:00"), "HH:MM"),
        (lambda p: p["focus_schedule"][0].update(duration_min=40), "must be one of"),
        (lambda p: p["focus_schedule"][0].pop("start"), "missing 'start'"),
        (
            lambda p: p["focus_schedule"][1].update(start="21:30"),
            "sessions overlap",
        ),
        (
            lambda p: p.update(
                focus_schedule=[
                    {"start": "23:30", "duration_min": 90},
                    {"start": "00:30", "duration_min": 25},
                ]
            ),
            "sessions overlap",
        ),
    ],
)
def test_structural_problems_reject_the_persona(
    change: Callable[[dict[str, Any]], None], expected_problem: str
) -> None:
    with pytest.raises(PersonaError) as error:
        validate_persona(edited(change))

    assert any(expected_problem in problem for problem in error.value.problems)


def test_back_to_back_sessions_do_not_count_as_overlapping() -> None:
    raw = edited(
        lambda p: p.update(
            focus_schedule=[
                {"start": "21:00", "duration_min": 50},
                {"start": "21:50", "duration_min": 25},
            ]
        )
    )

    assert validate_persona(raw).adjustments == []


@pytest.mark.parametrize(
    ("change", "path", "original", "clamped"),
    [
        (lambda p: p["pull"]["r_c"].update(social=9.0), "pull.r_c.social", 9.0, 3.0),
        (lambda p: p["pull"]["r_c"].update(news=0.0), "pull.r_c.news", 0.0, 0.2),
        (lambda p: p["pull"]["r_s"].update(FREE=1.2), "pull.r_s.FREE", 1.2, 1.0),
        (
            lambda p: p["pull"]["delta_s"].update(FOCUS_RESTLESS=-0.4),
            "pull.delta_s.FOCUS_RESTLESS",
            -0.4,
            0.0,
        ),
        (
            lambda p: p["important_share"].update(shopping_promo=0.3),
            "important_share.shopping_promo",
            0.3,
            0.05,
        ),
        (
            lambda p: p["notifications_per_hour"]["social"].__setitem__(3, -2.0),
            "notifications_per_hour.social[3]",
            -2.0,
            0.0,
        ),
    ],
)
def test_out_of_range_numbers_are_clamped_and_reported(
    change: Callable[[dict[str, Any]], None], path: str, original: float, clamped: float
) -> None:
    report = validate_persona(edited(change))

    assert [(a.path, a.original, a.clamped) for a in report.adjustments] == [
        (path, original, clamped)
    ]


def test_an_overbusy_hour_is_scaled_down_keeping_the_category_mix() -> None:
    def busy_hour(persona: dict[str, Any]) -> None:
        for category in CATEGORIES:
            persona["notifications_per_hour"][category][20] = 20.0

    report = validate_persona(edited(busy_hour))
    hour = [report.persona["notifications_per_hour"][c][20] for c in CATEGORIES]

    assert sum(hour) == pytest.approx(ranges.MAX_RATE_PER_HOUR)
    assert len(set(hour)) == 1
    assert report.adjustments[-1].path == "notifications_per_hour.total[20]"


def test_a_file_with_all_three_personas_passes() -> None:
    document = {"personas": [valid_persona(name) for name in PERSONA_NAMES]}

    reports = validate_personas(document)

    assert [r.persona["persona"] for r in reports] == list(PERSONA_NAMES)


@pytest.mark.parametrize(
    "names",
    [
        ["student_coder", "night_shift_nurse"],
        ["student_coder", "student_coder", "night_shift_nurse"],
        ["student_coder", "night_shift_nurse", "content_creator", "student_coder"],
    ],
)
def test_a_file_must_hold_each_persona_exactly_once(names: list[str]) -> None:
    with pytest.raises(PersonaError):
        validate_personas({"personas": [valid_persona(name) for name in names]})


def test_problems_in_several_personas_are_all_reported() -> None:
    broken = [valid_persona(name) for name in PERSONA_NAMES]
    broken[0]["pull"].pop("r_c")
    broken[2]["focus_schedule"] = []

    with pytest.raises(PersonaError) as error:
        validate_personas({"personas": broken})

    assert len(error.value.problems) == 2
    assert error.value.problems[0].startswith("student_coder:")
    assert error.value.problems[1].startswith("content_creator:")
