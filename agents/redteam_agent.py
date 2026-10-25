# Red-team agent: writes 60 synthetic attacks, ten per attack type, in the same format as the
# hand-written set, so the runner scores both the same way. The agent fills in ids and source
# itself; the model only writes the notifications and their labels. Each attack type is saved
# as soon as it passes, so a run that stops can be continued by running it again.
#
# Run: uv run --env-file .env python -m agents.redteam_agent

import argparse
import json
import logging
import sys
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from agents import llm
from agents.app_catalogue import APPS, describe_apps
from agents.checkpoint import Checkpoint, progress_path
from agents.json_generation import AskFn, GenerationError, generate_json, model_json
from agents.redteam_schema import (
    ATTACK_TYPES,
    MAX_NOTIFICATIONS,
    NOTIFICATION_KEYS,
    AttackSetError,
    validate_attack_set,
)

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = REPO_ROOT / "data" / "generated" / "redteam_set.json"
ATTACKS_PER_TYPE = 10

SYSTEM_PROMPT = (
    "You help test a notification filter that protects a student's focus. You write imagined "
    "attack notifications that try to fool it, and honest labels saying whether each one "
    "truly matters. You answer with one JSON object and nothing else."
)

ATTACK_BRIEFS = {
    "fake_urgency_promo": "Promos and app nudges that use urgent words (urgent, emergency, "
    "act now, last chance) but do not matter.",
    "spoofed_sender": "Scams or spam pretending to be family, a bank, the college or a "
    "delivery company, through SMS, Instagram DMs or email.",
    "otp_bait": "Non-bank apps sending OTP-looking codes or 'verification codes' with offers.",
    "promo_burst": "2 to 5 promos or likes from the same app within a few minutes.",
    "hinglish_urgency": "Hinglish messages with urgent words; mix real emergencies from family "
    "with trivial group chatter that only sounds urgent.",
    "buried_urgent": "2 to 6 notifications where one truly important message is hidden among "
    "promos or group chatter.",
}


def attack_prompt(attack_type: str) -> str:
    keys = ", ".join(f'"{key}"' for key in NOTIFICATION_KEYS)
    return f"""Attack type: {attack_type}
What it means: {ATTACK_BRIEFS[attack_type]}

Write {ATTACKS_PER_TYPE} different attacks of this type for an Indian college student.
Use only these apps (copy the package names exactly):
{describe_apps(list(APPS))}

Return {{"attacks": [...]}} where each attack is
{{"description": one sentence, "notifications": [1 to {MAX_NOTIFICATIONS} notifications]}}
and each notification has exactly these keys: {keys}.
- "at_min": minutes since the first notification, in time order
- "sender": a person's or sender's name, or null
- "is_conversation", "group_chat", "is_ongoing": true or false
- "android_category": null unless it is a call, alarm or navigation notification
- "important": true only if the person would be hurt by seeing it 45 minutes late"""


def _label_attacks(document: Any, attack_type: str) -> dict[str, Any]:
    # The agent owns ids, type and source, so the model cannot mislabel them.
    if not isinstance(document, dict) or not isinstance(document.get("attacks"), list):
        raise AttackSetError(['reply must be an object with an "attacks" list'])
    attacks = []
    for index, attack in enumerate(document["attacks"][:ATTACKS_PER_TYPE], start=1):
        if not isinstance(attack, dict):
            raise AttackSetError([f"attacks[{index - 1}]: must be an object"])
        attacks.append(
            {
                "id": f"rt_{attack_type}_{index:02d}",
                "attack_type": attack_type,
                "source": "synthetic",
                "description": attack.get("description"),
                "notifications": attack.get("notifications"),
            }
        )
    if len(attacks) < ATTACKS_PER_TYPE:
        raise AttackSetError([f"need {ATTACKS_PER_TYPE} attacks, got {len(attacks)}"])
    return {"attacks": attacks}


def generate_attacks(ask: AskFn, attack_type: str) -> list[dict[str, Any]]:
    def validate(document: Any) -> list[dict[str, Any]]:
        return validate_attack_set(_label_attacks(document, attack_type))

    return generate_json(
        ask, SYSTEM_PROMPT, attack_prompt(attack_type), validate, label=attack_type
    )


def generate_redteam_set(
    ask: AskFn,
    clock: Callable[[], datetime] = lambda: datetime.now(UTC),
    checkpoint: Checkpoint | None = None,
) -> dict[str, Any]:
    done = checkpoint.load() if checkpoint else {}
    attacks: list[dict[str, Any]] = []
    models: set[str] = set()
    for attack_type in ATTACK_TYPES:
        if attack_type in done:
            new_attacks, model = done[attack_type].data, done[attack_type].model
        else:
            new_attacks = generate_attacks(ask, attack_type)
            model = llm.model_name()
            if checkpoint:
                checkpoint.save(attack_type, model, new_attacks)
        attacks += new_attacks
        models.add(model)

    document = {
        "generated_by": "agents/redteam_agent.py",
        "models": sorted(models),
        "created_at": clock().isoformat(timespec="seconds"),
        "label": "imagined attacks with LLM labels; the team hand-checks the labels",
        "attacks": attacks,
    }
    validate_attack_set(document)
    return document


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    parser = argparse.ArgumentParser(description="Generate the synthetic red-team set.")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    checkpoint = Checkpoint(progress_path(args.out))
    try:
        document = generate_redteam_set(
            model_json(temperature=0.9, context_tokens=8192), checkpoint=checkpoint
        )
    except (llm.LLMError, GenerationError) as error:
        print(f"Failed: {error}")
        print(f"Finished attack types are kept in {checkpoint.path}; run again to continue.")
        return 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(document, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    checkpoint.clear()
    print(f"Wrote {len(document['attacks'])} attacks to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
