# The shape of a red-team attack, shared by the hand-written set, the red-team agent's output
# and the runner. An attack is a short sequence of notifications, each labelled with whether it
# truly matters, so bursts and buried emergencies can be expressed as well as single items.

from typing import Any

from agents.checks import bool_problems, is_number, key_problems, text_problems

ATTACK_TYPES = (
    "fake_urgency_promo",
    "spoofed_sender",
    "otp_bait",
    "promo_burst",
    "hinglish_urgency",
    "buried_urgent",
)
SOURCES = ("hand_written", "synthetic")

ATTACK_KEYS = ("id", "attack_type", "source", "description", "notifications")
NOTIFICATION_KEYS = (
    "at_min",
    "package_name",
    "title",
    "text",
    "sender",
    "is_conversation",
    "group_chat",
    "is_ongoing",
    "android_category",
    "important",
)
NOTIFICATION_FLAGS = ("is_conversation", "group_chat", "is_ongoing", "important")

MAX_NOTIFICATIONS = 8
MAX_TEXT_LENGTH = 200


class AttackSetError(ValueError):
    def __init__(self, problems: list[str]) -> None:
        self.problems = problems
        super().__init__("Attack set rejected:\n  " + "\n  ".join(problems))


def attack_problems(attack: Any, where: str) -> list[str]:
    problems = key_problems(attack, ATTACK_KEYS, where)
    if problems:
        return problems

    problems += text_problems(attack["id"], f"{where}.id", 40)
    if attack["attack_type"] not in ATTACK_TYPES:
        problems.append(f"{where}.attack_type: must be one of {ATTACK_TYPES}")
    if attack["source"] not in SOURCES:
        problems.append(f"{where}.source: must be one of {SOURCES}")
    problems += text_problems(attack["description"], f"{where}.description", MAX_TEXT_LENGTH)

    notifications = attack["notifications"]
    if not isinstance(notifications, list) or not 1 <= len(notifications) <= MAX_NOTIFICATIONS:
        problems.append(f"{where}.notifications: needs 1 to {MAX_NOTIFICATIONS} notifications")
        return problems

    previous_minute = 0.0
    for index, notification in enumerate(notifications):
        path = f"{where}.notifications[{index}]"
        note_problems = _notification_problems(notification, path)
        problems += note_problems
        if note_problems:
            continue
        if notification["at_min"] < previous_minute:
            problems.append(f"{path}.at_min: notifications must be in time order")
        previous_minute = notification["at_min"]
    return problems


def _notification_problems(notification: Any, where: str) -> list[str]:
    problems = key_problems(notification, NOTIFICATION_KEYS, where)
    if problems:
        return problems

    if not is_number(notification["at_min"]) or notification["at_min"] < 0:
        problems.append(f"{where}.at_min: must be a number of minutes, 0 or more")
    problems += text_problems(notification["package_name"], f"{where}.package_name", 100)
    problems += text_problems(notification["title"], f"{where}.title", MAX_TEXT_LENGTH)
    problems += text_problems(notification["text"], f"{where}.text", MAX_TEXT_LENGTH)
    if notification["sender"] is not None:
        problems += text_problems(notification["sender"], f"{where}.sender", 60)
    if notification["android_category"] is not None:
        problems += text_problems(notification["android_category"], f"{where}.android_category", 40)
    problems += bool_problems(notification, NOTIFICATION_FLAGS, where)
    if notification["group_chat"] is True and notification["is_conversation"] is not True:
        problems.append(f"{where}: a group chat must also be a conversation")
    return problems


def validate_attack_set(document: Any) -> list[dict[str, Any]]:
    if not isinstance(document, dict) or not isinstance(document.get("attacks"), list):
        raise AttackSetError(['expected an object with an "attacks" list'])

    attacks = document["attacks"]
    problems: list[str] = []
    for index, attack in enumerate(attacks):
        name = attack.get("id") if isinstance(attack, dict) else None
        problems += attack_problems(attack, f"attacks[{index}]" + (f" ({name})" if name else ""))

    ids = [attack.get("id") for attack in attacks if isinstance(attack, dict)]
    duplicates = sorted({str(i) for i in ids if ids.count(i) > 1})
    if duplicates:
        problems.append(f"duplicate attack ids: {duplicates}")
    if problems:
        raise AttackSetError(problems)
    return attacks
