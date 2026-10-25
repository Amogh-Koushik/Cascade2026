# Laptop copy of the phone's feature builder: turns a raw notification (package, title, text,
# sender) into NotificationFeatures by the rules in CONTRACTS.md section 3. Used to push the
# red-team set and the text bank through engine.py exactly as the phone would.

import hashlib
from dataclasses import dataclass
from typing import Any

from engine.contract import Category, NotificationFeatures
from engine.lexicon import lexicon_hits

# Laptop runs only. The phone uses its own secret salt, so these hashes never match real ones.
SENDER_SALT = "pratibimb-laptop"


@dataclass(frozen=True)
class RawNotification:
    at_min: float
    package_name: str
    title: str
    text: str
    sender: str | None
    is_conversation: bool
    group_chat: bool
    is_ongoing: bool
    android_category: str | None

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "RawNotification":
        return cls(**{name: data[name] for name in cls.__dataclass_fields__})


def sender_hash(sender: str | None) -> str | None:
    if sender is None:
        return None
    digest = hashlib.sha256(f"{SENDER_SALT}:{sender}".encode()).hexdigest()
    return f"s_{digest[:12]}"


def recent_pings_from_sender(
    notification: RawNotification, earlier: list[RawNotification], window_min: float
) -> int:
    # Only conversation notifications from the same person count, never an app's own repeats.
    if not notification.is_conversation or notification.sender is None:
        return 0
    return sum(
        1
        for previous in earlier
        if previous.is_conversation
        and previous.sender == notification.sender
        and notification.at_min - previous.at_min <= window_min
    )


def build_features(
    notification: RawNotification,
    earlier: list[RawNotification],
    params: dict[str, Any],
    key: str,
) -> NotificationFeatures:
    hits = lexicon_hits(notification.title, notification.text, params["lexicons"])
    category = Category.from_key(params["app_category_map"].get(notification.package_name))
    return NotificationFeatures(
        key=key,
        posted_at_millis=int(notification.at_min * 60_000),
        package_name=notification.package_name,
        category=category,
        android_category=notification.android_category,
        channel_importance=3,
        is_ongoing=notification.is_ongoing,
        is_conversation=notification.is_conversation,
        priority_conversation=False,
        group_chat=notification.group_chat,
        sender_hash=sender_hash(notification.sender),
        sender_display_name=notification.sender,
        same_sender_recent_pings=recent_pings_from_sender(
            notification, earlier, params["policy"]["repeat_ping"]["window_min"]
        ),
        bait_hit=hits.bait,
        urgency_hit=hits.urgency,
        otp_pattern=hits.otp,
    )
