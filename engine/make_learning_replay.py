# Writes contracts/demo/learning_replay.json: 30 recorded outcomes of delivered Instagram
# notifications during steady focus, for the demo's Learning replay button (FR-9.3). The phone
# pushes them through the same applyFeedback code as real taps, so the "if shown" range
# visibly narrows. They come from a simulated user and the file is labelled that way.
#
# Run: uv run python -m engine.make_learning_replay

import json
import math
import random
from pathlib import Path
from typing import Any

OUTPUT_PATH = Path(__file__).resolve().parent.parent / "contracts" / "demo" / "learning_replay.json"

SEED = 2026
EVENT_COUNT = 30

# The simulated user opens about 45% of likes and then loses around 6 minutes, give or take,
# a steadier habit than the starter prior assumes, which is what makes the range shrink.
OPEN_RATE = 0.45
MEDIAN_MINUTES = 6.0
LOG_SPREAD = 0.35


def build_events(seed: int = SEED) -> list[dict[str, Any]]:
    rng = random.Random(seed)
    events = []
    for _ in range(EVENT_COUNT):
        opened = rng.random() < OPEN_RATE
        minutes = round(math.exp(rng.gauss(math.log(MEDIAN_MINUTES), LOG_SPREAD)), 1)
        events.append(
            {
                "type": "pull_outcome",
                "category": "social",
                "state": "FOCUS_STEADY",
                "opened": opened,
                "minutes_lost": minutes if opened else None,
                "released_from_tray": False,
            }
        )
    return events


def build_document() -> dict[str, Any]:
    return {
        "version": "1.0",
        "label": "simulated",
        "description": "30 recorded outcomes of delivered Instagram notifications in steady focus",
        "seed": SEED,
        "events": build_events(),
    }


def main() -> None:
    document = build_document()
    OUTPUT_PATH.write_text(json.dumps(document, indent=1) + "\n", encoding="utf-8")
    opened = sum(event["opened"] for event in document["events"])
    print(f"Wrote {len(document['events'])} events ({opened} opened) to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
