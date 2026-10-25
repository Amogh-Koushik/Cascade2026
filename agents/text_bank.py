# The text bank's item format and its checks: realistic Indian notifications in English and
# Hinglish, each labelled with whether it matters, whether it is bait and whether it sounds
# urgent. Used by the text-bank agent and for the team's hand-written seeds.

import re
from typing import Any

from agents.app_catalogue import APPS
from agents.checks import bool_problems, key_problems, text_problems

LANGUAGES = ("english", "hinglish")
ITEM_KEYS = (
    "package_name",
    "title",
    "text",
    "sender",
    "language",
    "is_conversation",
    "group_chat",
    "important",
    "bait",
    "urgency",
)
ITEM_FLAGS = ("is_conversation", "group_chat", "important", "bait", "urgency")
MAX_TEXT_LENGTH = 200
ALL_PACKAGES = frozenset(APPS.values())


class TextBankError(ValueError):
    def __init__(self, problems: list[str]) -> None:
        self.problems = problems
        super().__init__("Text bank items rejected:\n  " + "\n  ".join(problems))


def normalised(item: dict[str, Any]) -> str:
    # Two items are duplicates when they say the same thing, ignoring case and spacing.
    return re.sub(r"\s+", " ", f"{item['title']} | {item['text']}".lower()).strip()


def item_problems(item: Any, where: str, allowed_packages: frozenset[str]) -> list[str]:
    problems = key_problems(item, ITEM_KEYS, where)
    if problems:
        return problems

    if item["package_name"] not in allowed_packages:
        problems.append(f"{where}.package_name: must be one of the listed apps")
    problems += text_problems(item["title"], f"{where}.title", MAX_TEXT_LENGTH)
    problems += text_problems(item["text"], f"{where}.text", MAX_TEXT_LENGTH)
    if item["sender"] is not None:
        problems += text_problems(item["sender"], f"{where}.sender", 60)
    if item["language"] not in LANGUAGES:
        problems.append(f"{where}.language: must be one of {LANGUAGES}")
    problems += bool_problems(item, ITEM_FLAGS, where)
    if problems:
        return problems

    if item["group_chat"] and not item["is_conversation"]:
        problems.append(f"{where}: a group chat must also be a conversation")
    if item["bait"] and item["important"]:
        problems.append(f"{where}: an item cannot be both bait and important")
    return problems


def validate_items(
    items: Any,
    allowed_packages: frozenset[str],
    seen: set[str],
    count: int,
) -> list[dict[str, Any]]:
    # Returns exactly `count` new items, or raises with every problem found.
    if not isinstance(items, list):
        raise TextBankError(['"items" must be a list'])
    # Extra items beyond the batch size are dropped unread, so they cannot fail the batch.
    items = items[:count]

    problems: list[str] = []
    batch_seen: set[str] = set()
    for index, item in enumerate(items):
        where = f"items[{index}]"
        found = item_problems(item, where, allowed_packages)
        problems += found
        if found:
            continue
        key = normalised(item)
        if key in seen or key in batch_seen:
            problems.append(f"{where}: duplicates an earlier notification ({item['title']!r})")
        batch_seen.add(key)

    if len(items) < count:
        problems.append(f"need {count} items, got {len(items)}")
    if problems:
        raise TextBankError(problems)
    return items
