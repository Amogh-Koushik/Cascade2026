# Writes contracts/sample_decisions.json: ten scenarios, each with the engine inputs and a full
# Decision, so the stub engine and the screens can be built before engine.py exists.
# The numbers are hand-set placeholders (every scenario says "placeholder": true) but they obey
# the same rules the real engine will, which tests/test_contracts.py checks. engine.py
# regenerates this file with real outputs later.
#
# Run: uv run python -m contracts.make_sample_decisions

import json
from pathlib import Path
from typing import Any

OUTPUT_PATH = Path(__file__).with_name("sample_decisions.json")

EPSILON = 0.1


def features(key: str, package: str, category: str, **fields: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "key": key,
        "posted_at_millis": 1_792_918_800_000,
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


def card(
    ctx: dict[str, Any],
    pull: tuple[float, float, float],
    mins_if_opened: tuple[float, float],
    loss: tuple[float, float, float, float],
    hold: tuple[float, float, float, float],
    worth: tuple[float, float, float, float],
    hold_better_per_hundred: int,
    low_confidence: bool = False,
) -> dict[str, Any]:
    # Tuples: pull (mean, lo, hi); loss and hold (p10, p50, p90, mean);
    # worth (mean, lo, hi, upper80).
    return {
        "pull_mean": pull[0],
        "pull_lo": pull[1],
        "pull_hi": pull[2],
        "mins_if_opened_lo": mins_if_opened[0],
        "mins_if_opened_hi": mins_if_opened[1],
        "l_p10": loss[0],
        "l_p50": loss[1],
        "l_p90": loss[2],
        "l_mean": loss[3],
        "h_p10": hold[0],
        "h_p50": hold[1],
        "h_p90": hold[2],
        "h_mean": hold[3],
        "q_mean": worth[0],
        "q_lo": worth[1],
        "q_hi": worth[2],
        "q_upper80": worth[3],
        "hold_better_per_hundred": hold_better_per_hundred,
        "net_minutes_protected": round(loss[3] - hold[3], 2),
        "minutes_until_release": ctx["minutes_until_release"],
        "low_confidence": low_confidence,
        "fatigue_risk": ctx["fatigue_risk"],
    }


def decision(
    action: str,
    rule: str,
    reason: str,
    explanation: str,
    future_card: dict[str, Any],
    explore_eligible: bool = False,
) -> dict[str, Any]:
    # A hold that was eligible for exploration was kept with probability 1 - epsilon.
    return {
        "action": action,
        "rule": rule,
        "would_be_action": action,
        "suggest_only": False,
        "explored": False,
        "propensity": round(1 - EPSILON, 2) if explore_eligible else 1.0,
        "reason": reason,
        "explanation": explanation,
        "card": future_card,
    }


def scenario(
    scenario_id: str,
    display: dict[str, str],
    feats: dict[str, Any],
    ctx: dict[str, Any],
    dec: dict[str, Any],
) -> dict[str, Any]:
    return {
        "id": scenario_id,
        "placeholder": True,
        # What the phone shows for the notification itself; transient, never stored.
        "display": display,
        "features": feats,
        "context": ctx,
        "decision": dec,
    }


def build_scenarios() -> list[dict[str, Any]]:
    steady = context()
    restless = context(state="FOCUS_RESTLESS")
    free = context(state="FREE", minutes_until_release=0.0, focus_minutes_elapsed=0.0)
    mom_hash = "s_7c41e2a9"

    return [
        scenario(
            "instagram_like_steady",
            {"app": "Instagram", "title": "rohan_k", "text": "Rohan liked your reel"},
            features("n01", "com.instagram.android", "social", bait_hit=True),
            steady,
            decision(
                "HOLD",
                "HOLD_BETTER",
                "Held: high pull, low worth",
                "Held: you're 34 min into steady focus. Instagram likes usually pull you in "
                "for 3-22 min. Back at your break in 16 min.",
                card(
                    steady,
                    (0.28, 0.17, 0.41),
                    (3.1, 22.0),
                    (0.9, 2.6, 7.4, 3.8),
                    (0.02, 0.08, 0.31, 0.13),
                    (0.016, 0.006, 0.04, 0.03),
                    97,
                ),
                explore_eligible=True,
            ),
        ),
        scenario(
            "instagram_like_restless",
            {"app": "Instagram", "title": "rohan_k", "text": "Rohan liked your reel"},
            features("n02", "com.instagram.android", "social", bait_hit=True),
            restless,
            decision(
                "HOLD",
                "HOLD_BETTER",
                "Held: high pull, low worth",
                "Held: you're switching apps a lot. Instagram likes pull you in harder "
                "right now. Back at your break in 16 min.",
                card(
                    restless,
                    (0.45, 0.30, 0.60),
                    (3.8, 27.0),
                    (2.0, 5.1, 11.6, 6.4),
                    (0.02, 0.08, 0.31, 0.13),
                    (0.016, 0.006, 0.04, 0.03),
                    99,
                ),
                explore_eligible=True,
            ),
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
            decision(
                "DELIVER",
                "MIGHT_MATTER",
                "Delivered: might matter",
                "Delivered: might matter. Group chats from this app are sometimes important "
                "to you.",
                card(
                    steady,
                    (0.31, 0.20, 0.44),
                    (1.2, 8.5),
                    (0.6, 1.4, 3.2, 1.9),
                    (2.6, 4.0, 5.4, 4.0),
                    (0.50, 0.33, 0.67, 0.60),
                    18,
                ),
            ),
        ),
        scenario(
            "mom_urgent_first",
            {"app": "WhatsApp", "title": "Mom", "text": "Call me, urgent"},
            features(
                "n04",
                "com.whatsapp",
                "messaging",
                is_conversation=True,
                sender_hash=mom_hash,
                sender_display_name="Mom",
                urgency_hit=True,
            ),
            steady,
            decision(
                "DELIVER",
                "MIGHT_MATTER",
                "Delivered: might matter",
                "Delivered: might matter. A direct message that sounds urgent.",
                card(
                    steady,
                    (0.30, 0.19, 0.43),
                    (1.2, 8.5),
                    (0.5, 1.0, 2.4, 1.4),
                    (6.8, 7.4, 7.7, 7.3),
                    (0.92, 0.85, 0.96, 0.95),
                    2,
                ),
            ),
        ),
        scenario(
            "mom_urgent_repeat",
            {"app": "WhatsApp", "title": "Mom", "text": "Pick up the phone"},
            features(
                "n05",
                "com.whatsapp",
                "messaging",
                is_conversation=True,
                sender_hash=mom_hash,
                sender_display_name="Mom",
                same_sender_recent_pings=1,
            ),
            steady,
            decision(
                "DELIVER",
                "OVERRIDE",
                "Always delivered: repeated ping",
                "Delivered: Mom messaged twice in 3 minutes.",
                card(
                    steady,
                    (0.30, 0.19, 0.43),
                    (1.2, 8.5),
                    (0.5, 1.0, 2.4, 1.4),
                    (6.2, 7.0, 7.5, 6.9),
                    (0.86, 0.77, 0.93, 0.91),
                    2,
                ),
            ),
        ),
        scenario(
            "sms_otp",
            {"app": "Messages", "title": "HDFCBK", "text": "482913 is your OTP for login"},
            features(
                "n06",
                "com.google.android.apps.messaging",
                "messaging",
                otp_pattern=True,
            ),
            steady,
            decision(
                "DELIVER",
                "OVERRIDE",
                "Always delivered: one-time password",
                "Delivered: looks like a one-time password from your SMS app.",
                card(
                    steady,
                    (0.30, 0.19, 0.43),
                    (1.2, 8.5),
                    (0.5, 0.9, 2.0, 1.1),
                    (1.6, 2.6, 3.8, 2.6),
                    (0.33, 0.20, 0.47, 0.43),
                    10,
                ),
            ),
        ),
        scenario(
            "otp_bait_shopping",
            {
                "app": "Flipkart",
                "title": "Your code inside",
                "text": "OTP 482913 unlocks 60% off. Don't miss it!",
            },
            features(
                "n07",
                "com.flipkart.android",
                "shopping_promo",
                bait_hit=True,
                otp_pattern=True,
            ),
            steady,
            decision(
                "HOLD",
                "HOLD_BETTER",
                "Held: costs more to show than to hold",
                "Held: a shopping promo dressed up as a code. OTPs are only trusted from SMS "
                "and payment apps.",
                card(
                    steady,
                    (0.13, 0.07, 0.22),
                    (1.1, 6.0),
                    (0.45, 0.6, 1.6, 0.9),
                    (0.01, 0.04, 0.12, 0.06),
                    (0.01, 0.004, 0.03, 0.02),
                    88,
                ),
                explore_eligible=True,
            ),
        ),
        scenario(
            "promo_burst_third",
            {"app": "Swiggy", "title": "Hungry?", "text": "Flat 50% off for the next hour"},
            features("n08", "in.swiggy.android", "shopping_promo", bait_hit=True),
            steady,
            decision(
                "HOLD",
                "HOLD_BETTER",
                "Held: costs more to show than to hold",
                "Held: third Swiggy promo in a few minutes. Repeated promos never count as urgent.",
                card(
                    steady,
                    (0.12, 0.06, 0.20),
                    (1.1, 6.0),
                    (0.45, 0.6, 1.5, 0.85),
                    (0.01, 0.04, 0.12, 0.06),
                    (0.01, 0.004, 0.03, 0.02),
                    90,
                ),
                explore_eligible=True,
            ),
        ),
        scenario(
            "work_email",
            {"app": "Gmail", "title": "Prof. Rao", "text": "Lab submission moved to Friday"},
            features("n09", "com.google.android.gm", "work_email"),
            steady,
            decision(
                "DELIVER",
                "MIGHT_MATTER",
                "Delivered: might matter",
                "Delivered: might matter. Work email often does, so it comes through.",
                card(
                    steady,
                    (0.24, 0.14, 0.36),
                    (1.9, 10.4),
                    (0.5, 1.2, 3.1, 1.7),
                    (1.4, 3.4, 5.3, 3.4),
                    (0.43, 0.18, 0.66, 0.52),
                    22,
                    low_confidence=True,
                ),
            ),
        ),
        scenario(
            "youtube_free_state",
            {"app": "YouTube", "title": "New video", "text": "A channel you follow just posted"},
            features("n10", "com.google.android.youtube", "short_video", bait_hit=True),
            free,
            decision(
                "DELIVER",
                "FREE_STATE",
                "Delivered: no focus session",
                "Delivered: no focus session is running.",
                card(
                    free,
                    (0.40, 0.27, 0.54),
                    (4.5, 36.0),
                    (1.8, 5.2, 14.0, 7.0),
                    (0.0, 0.0, 0.0, 0.0),
                    (0.01, 0.004, 0.03, 0.02),
                    100,
                ),
            ),
        ),
    ]


def main() -> None:
    document = {
        "version": "0.1",
        "placeholder": True,
        "note": "Hand-set placeholder numbers for integration. engine.py replaces this file.",
        "scenarios": build_scenarios(),
    }
    OUTPUT_PATH.write_text(json.dumps(document, indent=1, ensure_ascii=False) + "\n", "utf-8")
    print(f"Wrote {len(document['scenarios'])} scenarios to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
