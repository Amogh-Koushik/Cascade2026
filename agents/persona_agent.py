# Persona agent (Twin Lab): turns each persona brief into numbers for the simulator, one model
# call per persona. Every reply goes through the persona validator: a wrong shape is sent back
# to the model with the problems listed, and out-of-range numbers are clamped and recorded.
#
# Run: uv run python -m agents.persona_agent [--out data/generated/personas.json]

import argparse
import json
import logging
import sys
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from agents import llm
from agents import persona_ranges as ranges
from agents.json_generation import AskFn, GenerationError, generate_json, ollama_json
from agents.persona_briefs import PERSONA_BRIEFS, PERSONA_NAMES
from agents.persona_validator import ValidationReport, validate_persona, validate_personas
from contracts.params import CATEGORIES

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = REPO_ROOT / "data" / "generated" / "personas.json"

SYSTEM_PROMPT = (
    "You design realistic simulated phone users for a research simulator. You answer with one "
    "JSON object and nothing else. Every number must be inside the range you are given."
)


def _range_text(allowed: ranges.Range) -> str:
    return f"{allowed.lo:g} to {allowed.hi:g}"


def persona_prompt(name: str) -> str:
    categories = ", ".join(CATEGORIES)
    share_lines = "\n".join(
        f"  - {category}: {_range_text(ranges.IMPORTANT_SHARE[category])}"
        for category in CATEGORIES
    )
    return f"""Persona name: {name}
Brief: {PERSONA_BRIEFS[name]}

Return one JSON object with exactly these keys:
- "persona": "{name}"
- "description": one sentence summarising the person
- "pull": an object with
  - "r_c": for each category ({categories}), how strongly its notifications pull this person
    compared with an average app, {_range_text(ranges.R_C)}
  - "r_s": for FREE (always exactly 1.0), FOCUS_STEADY ({_range_text(ranges.R_S["FOCUS_STEADY"])})
    and FOCUS_RESTLESS ({_range_text(ranges.R_S["FOCUS_RESTLESS"])})
  - "delta_s": extra log-minutes lost once opened, for FREE (always 0.0), FOCUS_STEADY
    ({_range_text(ranges.DELTA_S["FOCUS_STEADY"])}) and FOCUS_RESTLESS
    ({_range_text(ranges.DELTA_S["FOCUS_RESTLESS"])})
- "notifications_per_hour": for each category, a list of exactly 24 numbers, hour 0 (midnight)
  to hour 23, each {_range_text(ranges.RATE_PER_CATEGORY)}, and at most
  {ranges.MAX_RATE_PER_HOUR:g} across all categories in any one hour. Follow the person's
  sleep and work hours.
- "important_share": for each category, the share of its notifications that truly matter:
{share_lines}
- "focus_schedule": {ranges.MIN_SESSIONS_PER_DAY} to {ranges.MAX_SESSIONS_PER_DAY} sessions,
  each {{"start": "HH:MM", "duration_min": one of {list(ranges.FOCUS_DURATIONS_MIN)}}}, never
  overlapping, at the times this person really focuses.

Make the numbers fit this specific person, not an average one."""


def generate_persona(ask: AskFn, name: str) -> ValidationReport:
    return generate_json(ask, SYSTEM_PROMPT, persona_prompt(name), validate_persona, label=name)


def generate_personas(
    ask: AskFn, clock: Callable[[], datetime] = lambda: datetime.now(UTC)
) -> dict[str, Any]:
    reports = [generate_persona(ask, name) for name in PERSONA_NAMES]
    document: dict[str, Any] = {
        "generated_by": "agents/persona_agent.py",
        "model": llm.model_name(),
        "created_at": clock().isoformat(timespec="seconds"),
        "label": "simulated (LLM-written persona, validated and clamped)",
        "clamped": {
            report.persona["persona"]: [
                {"path": a.path, "original": a.original, "clamped": a.clamped}
                for a in report.adjustments
            ]
            for report in reports
        },
        "personas": [report.persona for report in reports],
    }
    validate_personas(document)
    return document


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    parser = argparse.ArgumentParser(description="Generate the three persona configs.")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    try:
        document = generate_personas(ollama_json(temperature=0.6, context_tokens=8192))
    except (llm.LLMError, GenerationError) as error:
        print(f"Failed: {error}")
        return 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(document, indent=1) + "\n", encoding="utf-8")
    for name, adjustments in document["clamped"].items():
        print(f"{name}: {len(adjustments)} value(s) clamped")
    print(f"Wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
