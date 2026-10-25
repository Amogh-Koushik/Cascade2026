# Text-bank agent: writes 250 labelled Indian notifications in ten themed batches of 25, so each
# reply stays small enough for a local model. Every batch is validated (shape, allowed apps,
# label sanity, no duplicates across the whole bank) before it is kept.
#
# Run: uv run python -m agents.text_bank_agent [--seeds data/text_bank_seeds.json]

import argparse
import json
import logging
import sys
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from agents import llm
from agents.app_catalogue import APP_NAMES, APPS, category_of, describe_apps
from agents.json_generation import AskFn, GenerationError, generate_json, ollama_json
from agents.text_bank import ALL_PACKAGES, TextBankError, normalised, validate_items
from contracts.params import PARAMS_DIR, load_params

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = REPO_ROOT / "data" / "generated" / "text_bank.json"

SYSTEM_PROMPT = (
    "You write realistic smartphone notifications as seen by college students and young "
    "workers in India, in English and Hinglish (Hindi in Latin script mixed with English). "
    "You never use real people's names with real phone numbers. You answer with one JSON "
    "object and nothing else."
)


@dataclass(frozen=True)
class Batch:
    theme: str
    apps: tuple[str, ...]
    count: int = 25


BATCHES = (
    Batch("Messages from family and close friends", ("WhatsApp", "Telegram")),
    Batch("College class and hostel WhatsApp groups", ("WhatsApp",)),
    Batch("College portal, professor and work email or chat", ("Gmail", "Outlook", "Slack")),
    Batch("Social media likes, comments, follows and DMs", ("Instagram", "Snapchat", "LinkedIn")),
    Batch("Video uploads, live streams and recommendations", ("YouTube",)),
    Batch("Food delivery promos and real order updates", ("Swiggy", "Zomato")),
    Batch(
        "Shopping sales, cart reminders and real shipping updates", ("Flipkart", "Amazon", "Myntra")
    ),
    Batch(
        "OTPs, bank alerts and UPI payments", ("Messages (SMS)", "Google Pay", "PhonePe", "Paytm")
    ),
    Batch("Ride, delivery tracking and navigation", ("Uber", "Ola", "Rapido", "Google Maps")),
    Batch("News headlines and breaking alerts", ("Inshorts",)),
)

LABEL_RULES = """Labels for each item:
- "important": true only if a reasonable person would be hurt by seeing it 45 minutes late
  (family emergencies, deadline changes, a ride arriving, money leaving the account).
- "bait": true if it is designed to pull you in rather than inform (likes, sales, "don't miss").
- "urgency": true if the wording sounds urgent, whether or not it really is.
- An item can never be both bait and important.
- "is_conversation": true for a message from a person; "group_chat": true only for group messages.
- "sender": the person or sender name shown (a first name, a group member, or an SMS sender ID
  like "HDFCBK"); null for app notifications with no sender.
- "language": "english" or "hinglish". Aim for about 4 in 10 items in Hinglish."""


def batch_prompt(batch: Batch) -> str:
    return f"""Write {batch.count} different notifications for this theme: {batch.theme}.
Mix ordinary and important ones, and include some that only sound urgent.

Use only these apps (package names in brackets must be copied exactly):
{describe_apps(list(batch.apps))}

{LABEL_RULES}

Return {{"items": [...]}} where each item has exactly these keys:
"package_name", "title", "text", "sender", "language", "is_conversation", "group_chat",
"important", "bait", "urgency"."""


def generate_batch(
    ask: AskFn, batch: Batch, seen: set[str], params: dict[str, Any], start_index: int
) -> list[dict[str, Any]]:
    allowed = frozenset(APPS[name] for name in batch.apps)

    def validate(document: Any) -> list[dict[str, Any]]:
        if not isinstance(document, dict):
            raise TextBankError(['reply must be an object with an "items" list'])
        return validate_items(document.get("items"), allowed, seen, batch.count)

    items = generate_json(ask, SYSTEM_PROMPT, batch_prompt(batch), validate, label=batch.theme)
    return [_finish(item, params, start_index + offset, "llm") for offset, item in enumerate(items)]


def _finish(
    item: dict[str, Any], params: dict[str, Any], index: int, source: str
) -> dict[str, Any]:
    package = item["package_name"]
    return {
        "id": f"tb{index:03d}",
        "app": APP_NAMES[package],
        "category": category_of(package, params),
        "source": source,
        **item,
    }


def load_seeds(
    path: Path, params: dict[str, Any], start_index: int, seen: set[str]
) -> list[dict[str, Any]]:
    document = json.loads(path.read_text(encoding="utf-8"))
    seeds = document.get("items") if isinstance(document, dict) else None
    count = len(seeds) if isinstance(seeds, list) else 0
    valid = validate_items(seeds, ALL_PACKAGES, seen, count)
    return [
        _finish(item, params, start_index + offset, "seed") for offset, item in enumerate(valid)
    ]


def generate_text_bank(
    ask: AskFn,
    params: dict[str, Any],
    seeds_path: Path | None = None,
    clock: Callable[[], datetime] = lambda: datetime.now(UTC),
) -> dict[str, Any]:
    items: list[dict[str, Any]] = []
    seen: set[str] = set()
    for batch in BATCHES:
        new_items = generate_batch(ask, batch, seen, params, start_index=len(items) + 1)
        items += new_items
        seen.update(normalised(item) for item in new_items)
    if seeds_path is not None:
        items += load_seeds(seeds_path, params, len(items) + 1, seen)

    return {
        "generated_by": "agents/text_bank_agent.py",
        "model": llm.model_name(),
        "created_at": clock().isoformat(timespec="seconds"),
        "label": "LLM-written notifications with LLM labels; 50 are hand-checked by the team",
        "items": items,
    }


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    parser = argparse.ArgumentParser(description="Generate the labelled notification text bank.")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--seeds", type=Path, help='hand-written items, {"items": [...]}')
    args = parser.parse_args()

    params = load_params(PARAMS_DIR / "twin_params_example.json")
    try:
        document = generate_text_bank(
            ollama_json(temperature=0.9, context_tokens=8192), params, args.seeds
        )
    except (llm.LLMError, GenerationError, TextBankError) as error:
        print(f"Failed: {error}")
        return 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(document, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(document['items'])} items to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
