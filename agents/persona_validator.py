# Checks persona configs before they reach the simulator. A structural problem (missing or
# unknown keys, wrong shapes, an impossible focus schedule) rejects the persona outright. A
# number outside its allowed range is clamped, and every clamp is reported, so nothing the
# persona agent writes is accepted silently.
#
# Run: uv run python -m agents.persona_validator personas.json [--out cleaned.json]

import argparse
import copy
import itertools
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from agents import persona_ranges as ranges
from agents.checks import is_number, key_problems
from agents.persona_briefs import PERSONA_NAMES
from contracts.params import CATEGORIES, PHONE_STATES

PERSONA_KEYS = (
    "persona",
    "description",
    "pull",
    "notifications_per_hour",
    "important_share",
    "focus_schedule",
)
PULL_KEYS = ("r_c", "r_s", "delta_s")
SESSION_KEYS = ("start", "duration_min")
START_PATTERN = re.compile(r"^([01]\d|2[0-3]):([0-5]\d)$")
MINUTES_PER_DAY = 24 * 60


@dataclass(frozen=True)
class Adjustment:
    path: str
    original: float
    clamped: float


@dataclass(frozen=True)
class ValidationReport:
    persona: dict[str, Any]
    adjustments: list[Adjustment]


class PersonaError(ValueError):
    def __init__(self, problems: list[str]) -> None:
        self.problems = problems
        super().__init__("Persona rejected:\n  " + "\n  ".join(problems))


def validate_persona(raw: Any) -> ValidationReport:
    problems = structural_problems(raw)
    if problems:
        raise PersonaError(problems)

    persona = copy.deepcopy(raw)
    adjustments: list[Adjustment] = []
    _clamp_pull(persona["pull"], adjustments)
    _clamp_rates(persona["notifications_per_hour"], adjustments)
    _clamp_important_share(persona["important_share"], adjustments)
    return ValidationReport(persona, adjustments)


def validate_personas(document: Any) -> list[ValidationReport]:
    # The agent's output must hold each persona exactly once, in any order.
    if not isinstance(document, dict) or not isinstance(document.get("personas"), list):
        raise PersonaError(['expected an object with a "personas" list'])

    names = [p.get("persona") if isinstance(p, dict) else None for p in document["personas"]]
    if sorted(map(str, names)) != sorted(PERSONA_NAMES):
        raise PersonaError([f"expected personas {sorted(PERSONA_NAMES)}, got {names}"])

    reports, problems = [], []
    for raw in document["personas"]:
        try:
            reports.append(validate_persona(raw))
        except PersonaError as error:
            problems.extend(f"{raw['persona']}: {problem}" for problem in error.problems)
    if problems:
        raise PersonaError(problems)
    return reports


def structural_problems(raw: Any) -> list[str]:
    if not isinstance(raw, dict):
        return ["persona must be a JSON object"]

    problems = key_problems(raw, PERSONA_KEYS, "persona")
    if problems:
        return problems

    if raw["persona"] not in PERSONA_NAMES:
        problems.append(f"persona: unknown name {raw['persona']!r}")
    if not isinstance(raw["description"], str) or not raw["description"].strip():
        problems.append("description: must be a non-empty string")

    problems += _pull_problems(raw["pull"])
    problems += _number_map_problems(raw["important_share"], CATEGORIES, "important_share")
    problems += _rate_problems(raw["notifications_per_hour"])
    problems += _schedule_problems(raw["focus_schedule"])
    return problems


def _number_map_problems(value: Any, expected: tuple[str, ...], path: str) -> list[str]:
    problems = key_problems(value, expected, path)
    if problems:
        return problems
    return [f"{path}.{key}: must be a number" for key in expected if not is_number(value[key])]


def _pull_problems(pull: Any) -> list[str]:
    problems = key_problems(pull, PULL_KEYS, "pull")
    if problems:
        return problems
    return (
        _number_map_problems(pull["r_c"], CATEGORIES, "pull.r_c")
        + _number_map_problems(pull["r_s"], PHONE_STATES, "pull.r_s")
        + _number_map_problems(pull["delta_s"], PHONE_STATES, "pull.delta_s")
    )


def _rate_problems(rates: Any) -> list[str]:
    problems = key_problems(rates, CATEGORIES, "notifications_per_hour")
    if problems:
        return problems
    for category in CATEGORIES:
        hourly = rates[category]
        path = f"notifications_per_hour.{category}"
        if not isinstance(hourly, list) or len(hourly) != ranges.HOURS_PER_DAY:
            problems.append(f"{path}: must be a list of {ranges.HOURS_PER_DAY} numbers")
        elif not all(is_number(rate) for rate in hourly):
            problems.append(f"{path}: every hour must be a number")
    return problems


def _schedule_problems(schedule: Any) -> list[str]:
    if not isinstance(schedule, list):
        return ["focus_schedule: must be a list of sessions"]
    if not ranges.MIN_SESSIONS_PER_DAY <= len(schedule) <= ranges.MAX_SESSIONS_PER_DAY:
        return [
            f"focus_schedule: needs {ranges.MIN_SESSIONS_PER_DAY} to "
            f"{ranges.MAX_SESSIONS_PER_DAY} sessions, got {len(schedule)}"
        ]

    problems: list[str] = []
    intervals: list[tuple[int, int]] = []
    for index, session in enumerate(schedule):
        path = f"focus_schedule[{index}]"
        session_problems = key_problems(session, SESSION_KEYS, path)
        if session_problems:
            problems += session_problems
            continue
        match = START_PATTERN.match(str(session["start"]))
        if not match:
            problems.append(f"{path}.start: must be HH:MM in 24-hour time")
            continue
        if session["duration_min"] not in ranges.FOCUS_DURATIONS_MIN:
            problems.append(f"{path}.duration_min: must be one of {ranges.FOCUS_DURATIONS_MIN}")
            continue
        start = int(match.group(1)) * 60 + int(match.group(2))
        intervals.append((start, start + session["duration_min"]))

    if not problems and _overlaps(intervals):
        problems.append("focus_schedule: sessions overlap")
    return problems


def _overlaps(intervals: list[tuple[int, int]]) -> bool:
    # The day is a circle: a 23:30 session of 90 minutes runs into a 00:30 one.
    ordered = sorted(intervals)
    for (_, end), (next_start, _) in itertools.pairwise(ordered):
        if end > next_start:
            return True
    first_start, last_end = ordered[0][0], ordered[-1][1]
    return last_end > first_start + MINUTES_PER_DAY


def _clamp_value(
    container: dict[str, Any] | list[Any],
    key: Any,
    allowed: ranges.Range,
    path: str,
    adjustments: list[Adjustment],
) -> None:
    original = float(container[key])
    clamped = allowed.clamp(original)
    if clamped != original:
        container[key] = clamped
        adjustments.append(Adjustment(path, original, clamped))


def _clamp_pull(pull: dict[str, Any], adjustments: list[Adjustment]) -> None:
    for category in CATEGORIES:
        _clamp_value(pull["r_c"], category, ranges.R_C, f"pull.r_c.{category}", adjustments)
    for state in PHONE_STATES:
        _clamp_value(pull["r_s"], state, ranges.R_S[state], f"pull.r_s.{state}", adjustments)
        _clamp_value(
            pull["delta_s"], state, ranges.DELTA_S[state], f"pull.delta_s.{state}", adjustments
        )


def _clamp_rates(rates: dict[str, list[Any]], adjustments: list[Adjustment]) -> None:
    for category in CATEGORIES:
        for hour in range(ranges.HOURS_PER_DAY):
            path = f"notifications_per_hour.{category}[{hour}]"
            _clamp_value(rates[category], hour, ranges.RATE_PER_CATEGORY, path, adjustments)

    # An hour that is too busy overall is scaled down evenly, keeping the mix of categories.
    for hour in range(ranges.HOURS_PER_DAY):
        total = sum(rates[category][hour] for category in CATEGORIES)
        if total > ranges.MAX_RATE_PER_HOUR:
            scale = ranges.MAX_RATE_PER_HOUR / total
            for category in CATEGORIES:
                rates[category][hour] *= scale
            adjustments.append(
                Adjustment(f"notifications_per_hour.total[{hour}]", total, ranges.MAX_RATE_PER_HOUR)
            )


def _clamp_important_share(shares: dict[str, Any], adjustments: list[Adjustment]) -> None:
    for category in CATEGORIES:
        allowed = ranges.IMPORTANT_SHARE[category]
        _clamp_value(shares, category, allowed, f"important_share.{category}", adjustments)


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate persona configs.")
    parser.add_argument("personas", type=Path, help='JSON file with a "personas" list')
    parser.add_argument("--out", type=Path, help="write the clamped personas here")
    args = parser.parse_args()

    try:
        document = json.loads(args.personas.read_text(encoding="utf-8"))
        reports = validate_personas(document)
    except json.JSONDecodeError as error:
        print(f"{args.personas} is not valid JSON: {error}")
        return 1
    except PersonaError as error:
        print(error)
        return 1

    for report in reports:
        name = report.persona["persona"]
        print(f"{name}: {len(report.adjustments)} value(s) clamped")
        for adjustment in report.adjustments:
            print(f"  {adjustment.path}: {adjustment.original:g} -> {adjustment.clamped:g}")

    if args.out:
        cleaned = {"personas": [report.persona for report in reports]}
        args.out.write_text(json.dumps(cleaned, indent=1) + "\n", encoding="utf-8")
        print(f"Wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
