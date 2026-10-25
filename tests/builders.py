# Builders for engine inputs. Defaults describe the beat-1 notification: an Instagram like
# arriving 34 minutes into steady focus, 16 minutes before the break.

from typing import Any

from contracts.params import CATEGORIES
from engine.contract import (
    Autonomy,
    Category,
    FatigueRisk,
    NotificationFeatures,
    PhoneState,
    PullOutcome,
    TwinContext,
    WorthLabel,
    WorthLabelKind,
)

INSTAGRAM = "com.instagram.android"
WHATSAPP = "com.whatsapp"
MOM = "s_mom"


def valid_persona(name: str = "student_coder") -> dict[str, Any]:
    return {
        "persona": name,
        "description": "Studies late, scrolls reels when stuck.",
        "pull": {
            "r_c": dict.fromkeys(CATEGORIES, 1.0),
            "r_s": {"FREE": 1.0, "FOCUS_STEADY": 0.8, "FOCUS_RESTLESS": 1.3},
            "delta_s": {"FREE": 0.0, "FOCUS_STEADY": 0.0, "FOCUS_RESTLESS": 0.2},
        },
        "notifications_per_hour": {category: [2.0] * 24 for category in CATEGORIES},
        "important_share": dict.fromkeys(CATEGORIES, 0.05) | {"messaging": 0.2},
        "focus_schedule": [
            {"start": "21:00", "duration_min": 50},
            {"start": "22:00", "duration_min": 90},
        ],
    }


def make_features(**fields: Any) -> NotificationFeatures:
    values: dict[str, Any] = {
        "key": "n1",
        "posted_at_millis": 1_792_918_800_000,
        "package_name": INSTAGRAM,
        "category": Category.SOCIAL,
        "android_category": None,
        "channel_importance": 3,
        "is_ongoing": False,
        "is_conversation": False,
        "priority_conversation": False,
        "group_chat": False,
        "sender_hash": None,
        "sender_display_name": None,
        "same_sender_recent_pings": 0,
        "bait_hit": True,
        "urgency_hit": False,
        "otp_pattern": False,
    }
    values.update(fields)
    return NotificationFeatures(**values)


def mom_message(**fields: Any) -> NotificationFeatures:
    values: dict[str, Any] = {
        "package_name": WHATSAPP,
        "category": Category.MESSAGING,
        "is_conversation": True,
        "sender_hash": MOM,
        "sender_display_name": "Mom",
        "bait_hit": False,
    }
    values.update(fields)
    return make_features(**values)


def make_context(**fields: Any) -> TwinContext:
    values: dict[str, Any] = {
        "state": PhoneState.FOCUS_STEADY,
        "autonomy": Autonomy.AUTO,
        "minutes_until_release": 16.0,
        "focus_minutes_elapsed": 34.0,
        "fatigue_risk": FatigueRisk.LOW,
        "exploration_draw": 0.5,
        "always_deliver_senders": frozenset(),
        "always_deliver_packages": frozenset(),
        "demo_learning": False,
    }
    values.update(fields)
    return TwinContext(**values)


def opened(
    minutes: float,
    category: Category = Category.SOCIAL,
    state: PhoneState = PhoneState.FOCUS_STEADY,
    from_tray: bool = False,
) -> PullOutcome:
    return PullOutcome(category, state, True, minutes, from_tray)


def ignored(
    category: Category = Category.SOCIAL,
    state: PhoneState = PhoneState.FOCUS_STEADY,
    from_tray: bool = False,
) -> PullOutcome:
    return PullOutcome(category, state, False, None, from_tray)


def label(kind: WorthLabelKind, package: str = INSTAGRAM, sender: str | None = None) -> WorthLabel:
    return WorthLabel(package, sender, kind)
