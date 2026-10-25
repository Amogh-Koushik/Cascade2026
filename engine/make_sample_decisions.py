# Writes contracts/sample_decisions.json: the ten demo scenarios with the reference engine's
# real decisions on the example params, for the stub engine and the screens.
#
# Run: uv run python -m engine.make_sample_decisions

import json
from pathlib import Path
from typing import Any

from contracts.params import PARAMS_DIR
from contracts.sample_scenarios import build_scenario_inputs
from engine.contract import NotificationFeatures, TwinContext
from engine.engine import TwinEngine

PARAMS_FILE = "twin_params_example.json"
OUTPUT_PATH = Path(__file__).resolve().parent.parent / "contracts" / "sample_decisions.json"


def build_scenarios() -> list[dict[str, Any]]:
    # Each scenario gets a fresh twin, so no scenario's outcome depends on another's.
    scenarios = []
    for scenario in build_scenario_inputs():
        twin = TwinEngine.from_file(PARAMS_DIR / PARAMS_FILE)
        decision = twin.decide(
            NotificationFeatures.from_dict(scenario["features"]),
            TwinContext.from_dict(scenario["context"]),
        )
        scenarios.append(scenario | {"decision": json.loads(json.dumps(decision.to_dict()))})
    return scenarios


def main() -> None:
    document = {
        "version": "1.0",
        "params_file": PARAMS_FILE,
        "note": "Real outputs of engine.py on the example params, with a fresh twin per scenario.",
        "scenarios": build_scenarios(),
    }
    OUTPUT_PATH.write_text(json.dumps(document, indent=1, ensure_ascii=False) + "\n", "utf-8")
    print(f"Wrote {len(document['scenarios'])} scenarios to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
