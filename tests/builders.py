# Builders for engine inputs. Defaults describe the beat-1 notification: an Instagram like
# arriving 34 minutes into steady focus, 16 minutes before the break.

from typing import Any

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
