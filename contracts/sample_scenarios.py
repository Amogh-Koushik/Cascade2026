# The ten demo scenarios as engine inputs: what the screen shows, the features the phone would
# build, and the focus context. engine/make_sample_decisions.py runs them through the engine to
# write sample_decisions.json, so the stub app shows real engine numbers.

from typing import Any

POSTED_AT_MILLIS = 1_792_918_800_000
MOM_HASH = "s_7c41e2a9"


def features(key: str, package: str, category: str, **fields: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "key": key,
        "posted_at_millis": POSTED_AT_MILLIS,
        "package_name": package,
        "category": category,
        "android_category": None,
        "channel_importance": 3,
        "is_ongoing": False,
        "is_conversation": False,
        "priority_conversation": False,
        "group_chat": False,
        "sender_hash": None,
        "sender_display_name": None,
        "same_sender_recent_pings": 0,
        "bait_hit": False,
        "urgency_hit": False,
        "otp_pattern": False,
    }
    unknown = set(fields) - set(base)
    if unknown:
        raise KeyError(f"Unknown feature fields: {sorted(unknown)}")
    return base | fields


def context(**fields: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "state": "FOCUS_STEADY",
        "autonomy": "AUTO",
        "minutes_until_release": 16.0,
        "focus_minutes_elapsed": 34.0,
        "fatigue_risk": "LOW",
        "exploration_draw": 0.5,
        "always_deliver_senders": [],
        "always_deliver_packages": [],
        "demo_learning": False,
    }
    unknown = set(fields) - set(base)
    if unknown:
        raise KeyError(f"Unknown context fields: {sorted(unknown)}")
    return base | fields


def scenario(
    scenario_id: str, display: dict[str, str], feats: dict[str, Any], ctx: dict[str, Any]
) -> dict[str, Any]:
    # "display" is what the phone shows for the notification itself; transient, never stored.
    return {"id": scenario_id, "display": display, "features": feats, "context": ctx}


def build_scenario_inputs() -> list[dict[str, Any]]:
    steady = context()
    restless = context(state="FOCUS_RESTLESS")
    free = context(state="FREE", minutes_until_release=0.0, focus_minutes_elapsed=0.0)

    return [
        scenario(
            "instagram_like_steady",
            {"app": "Instagram", "title": "rohan_k", "text": "Rohan liked your reel"},
            features("n01", "com.instagram.android", "social", bait_hit=True),
            steady,
        ),
        scenario(
            "instagram_like_restless",
            {"app": "Instagram", "title": "rohan_k", "text": "Rohan liked your reel"},
            features("n02", "com.instagram.android", "social", bait_hit=True),
            restless,
        ),
        scenario(
            "whatsapp_group_chat",
            {"app": "WhatsApp", "title": "CSE 3rd Year", "text": "Priya: anyone has the notes?"},
            features(
                "n03",
                "com.whatsapp",
                "messaging",
                is_conversation=True,
                group_chat=True,
                sender_hash="s_19bd0f33",
            ),
            steady,
        ),
        scenario(
            "mom_urgent_first",
            {"app": "WhatsApp", "title": "Mom", "text": "Call me, urgent"},
            features(
                "n04",
                "com.whatsapp",
                "messaging",
                is_conversation=True,
                sender_hash=MOM_HASH,
                sender_display_name="Mom",
                urgency_hit=True,
            ),
            steady,
        ),
        scenario(
            "mom_urgent_repeat",
            {"app": "WhatsApp", "title": "Mom", "text": "Pick up the phone"},
            features(
                "n05",
                "com.whatsapp",
                "messaging",
                is_conversation=True,
                sender_hash=MOM_HASH,
                sender_display_name="Mom",
                same_sender_recent_pings=2,
            ),
            steady,
        ),
        scenario(
            "sms_otp",
            {"app": "Messages", "title": "HDFCBK", "text": "482913 is your OTP for login"},
            features("n06", "com.google.android.apps.messaging", "messaging", otp_pattern=True),
            steady,
        ),
        scenario(
            "otp_bait_shopping",
            {
                "app": "Flipkart",
                "title": "Your code inside",
                "text": "OTP 482913 unlocks 60% off. Don't miss it!",
            },
            features(
                "n07", "com.flipkart.android", "shopping_promo", bait_hit=True, otp_pattern=True
            ),
            steady,
        ),
        scenario(
            "promo_burst_third",
            {"app": "Swiggy", "title": "Hungry?", "text": "Flat 50% off for the next hour"},
            features("n08", "in.swiggy.android", "shopping_promo", bait_hit=True),
            steady,
        ),
        scenario(
            "work_email",
            {"app": "Gmail", "title": "Prof. Rao", "text": "Lab submission moved to Friday"},
            features("n09", "com.google.android.gm", "work_email"),
            steady,
        ),
        scenario(
            "youtube_free_state",
            {"app": "YouTube", "title": "New video", "text": "A channel you follow just posted"},
            features("n10", "com.google.android.youtube", "short_video", bait_hit=True),
            free,
        ),
    ]
