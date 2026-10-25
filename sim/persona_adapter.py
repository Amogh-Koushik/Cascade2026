# Feeds the LLM-generated personas (data/generated/personas.json, validated by
# agents/persona_validator.py) to the Twin Lab simulator, which reads its own field names.
#
#   personas.json                  ->  simulator
#   pull.r_c                       ->  category_pull_multipliers
#   pull.r_s, pull.delta_s         ->  state_multipliers {state: {r_s, delta_s}}
#   important_share                ->  latent_importance_rates
#   notifications_per_hour (x24)   ->  notification_rates_per_hour (daily mean) + the hourly lists
#   (not in personas)              ->  category_minutes from that persona's params file
#
# Minutes-if-opened come from the params files because they are fitted to LSApp sessions; the
# personas only describe how this person differs. A persona without its own params file uses
# the example params, and the output says which.
#
# Run: uv run python -m sim.persona_adapter   (writes sim/personas.json)

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from agents.persona_validator import PersonaError, validate_personas
from contracts.params import CATEGORIES, PARAMS_DIR, PHONE_STATES, load_params

REPO_ROOT = Path(__file__).resolve().parent.parent
GENERATED_PERSONAS = REPO_ROOT / "data" / "generated" / "personas.json"
SIMULATOR_PERSONAS = REPO_ROOT / "sim" / "personas.json"
EXAMPLE_PARAMS = PARAMS_DIR / "twin_params_example.json"


def params_for(persona: str) -> tuple[dict[str, Any], str]:
    own = PARAMS_DIR / f"twin_params_{persona}.json"
    path = own if own.exists() else EXAMPLE_PARAMS
    return load_params(path), path.name


def to_simulator(
    persona: dict[str, Any], params: dict[str, Any], params_name: str
) -> dict[str, Any]:
    pull = persona["pull"]
    hourly = persona["notifications_per_hour"]
    category_params = params["pull"]["category"]
    return {
        "name": persona["persona"],
        "description": persona["description"],
        "category_pull_multipliers": {c: pull["r_c"][c] for c in CATEGORIES},
        "category_minutes": {
            c: {"mu": category_params[c]["mu_c0"], "sigma": category_params[c]["sigma_c"]}
            for c in CATEGORIES
        },
        "state_multipliers": {
            s: {"r_s": pull["r_s"][s], "delta_s": pull["delta_s"][s]} for s in PHONE_STATES
        },
        "latent_importance_rates": {c: persona["important_share"][c] for c in CATEGORIES},
        "notification_rates_per_hour": {c: round(sum(hourly[c]) / 24, 3) for c in CATEGORIES},
        "notification_rates_by_hour": {c: list(hourly[c]) for c in CATEGORIES},
        "focus_schedule": persona["focus_schedule"],
        "category_minutes_source": params_name,
    }


def adapt(document: dict[str, Any]) -> dict[str, Any]:
    reports = validate_personas(document)
    personas = {}
    for report in reports:
        persona = report.persona
        params, params_name = params_for(persona["persona"])
        personas[persona["persona"]] = to_simulator(persona, params, params_name)
    return {
        "source": "data/generated/personas.json via sim/persona_adapter.py",
        "label": "simulated (LLM-written personas, validated); minutes from the params files",
        "personas": personas,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert generated personas for the simulator.")
    parser.add_argument("--source", type=Path, default=GENERATED_PERSONAS)
    parser.add_argument("--out", type=Path, default=SIMULATOR_PERSONAS)
    args = parser.parse_args()

    try:
        adapted = adapt(json.loads(args.source.read_text(encoding="utf-8")))
    except (OSError, json.JSONDecodeError, PersonaError) as error:
        print(f"Failed: {error}")
        return 1

    args.out.write_text(json.dumps(adapted, indent=1) + "\n", encoding="utf-8")
    for name, persona in adapted["personas"].items():
        print(f"{name}: minutes from {persona['category_minutes_source']}")
    print(f"Wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
