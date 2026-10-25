# Writes contracts/test_vectors.json: 50 cases covering every model path, every override and
# every decision branch, with the reference engine's outputs as the expected values.
# TwinEngine.kt must reproduce them within the tolerances in engine/vectors.py.
#
# Run: uv run python -m engine.make_test_vectors

import json
from pathlib import Path
from typing import Any

from contracts.params import PARAMS_DIR, load_params
from contracts.sample_scenarios import context, features
from engine.vectors import ABSOLUTE_TOLERANCE, EXP_FIELDS, EXP_TOLERANCE, run_vector

PARAMS_FILE = "twin_params_example.json"
OUTPUT_PATH = Path(__file__).resolve().parent.parent / "contracts" / "test_vectors.json"

INSTAGRAM = "com.instagram.android"
WHATSAPP = "com.whatsapp"
MOM = "s_mom"
FRIEND = "s_friend"


def like(**fields: Any) -> dict[str, Any]:
    return features("v", INSTAGRAM, "social", bait_hit=True) | fields


def message(sender: str = MOM, **fields: Any) -> dict[str, Any]:
    base = features(
        "v",
        WHATSAPP,
        "messaging",
        is_conversation=True,
        sender_hash=sender,
        sender_display_name="Mom" if sender == MOM else None,
    )
    return base | fields


def opened(
    minutes: float, category: str = "social", state: str = "FOCUS_STEADY", tray: bool = False
) -> dict[str, Any]:
    return {
        "type": "pull_outcome",
        "category": category,
        "state": state,
        "opened": True,
        "minutes_lost": minutes,
        "released_from_tray": tray,
    }


def ignored(category: str = "social", state: str = "FOCUS_STEADY") -> dict[str, Any]:
    return {
        "type": "pull_outcome",
        "category": category,
        "state": state,
        "opened": False,
        "minutes_lost": None,
        "released_from_tray": False,
    }


def label(kind: str, package: str = INSTAGRAM, sender: str | None = None) -> dict[str, Any]:
    return {"type": "worth_label", "package_name": package, "sender_hash": sender, "label": kind}


def patch(*path_and_value: Any) -> dict[str, Any]:
    *path, value = path_and_value
    return {"path": list(path), "value": value}


def case(
    case_id: str,
    description: str,
    feats: dict[str, Any],
    ctx: dict[str, Any] | None = None,
    events: list[dict[str, Any]] | None = None,
    patches: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    return {
        "id": case_id,
        "description": description,
        "param_patches": patches or [],
        "events": events or [],
        "features": feats | {"key": case_id},
        "context": ctx or context(),
    }


def fresh_cases() -> list[dict[str, Any]]:
    return [
        case("fresh_social", "Instagram like, fresh twin, steady focus", like()),
        case(
            "fresh_short_video",
            "YouTube upload, fresh twin",
            features("v", "com.google.android.youtube", "short_video", bait_hit=True),
        ),
        case("fresh_messaging", "Direct message from a friend", message(FRIEND)),
        case(
            "fresh_shopping_promo",
            "Swiggy promo",
            features("v", "in.swiggy.android", "shopping_promo", bait_hit=True),
        ),
        case("fresh_news", "News alert", features("v", "com.nis.app", "news")),
        case(
            "fresh_work_email", "Work email", features("v", "com.google.android.gm", "work_email")
        ),
        case("fresh_other", "App with no category", features("v", "com.example.app", "other")),
        case(
            "unknown_category_key",
            "A category key the contract does not know falls back to other",
            features("v", "com.example.app", "games"),
        ),
        case("state_free", "Like with no focus session", like(), context(state="FREE")),
        case("state_restless", "Like in restless focus", like(), context(state="FOCUS_RESTLESS")),
    ]


def override_cases() -> list[dict[str, Any]]:
    return [
        case(
            "override_always_app",
            "User always delivers Instagram",
            like(),
            context(always_deliver_packages=[INSTAGRAM]),
        ),
        case(
            "override_always_sender",
            "User always delivers Mom",
            message(),
            context(always_deliver_senders=[MOM]),
        ),
        case("override_navigation", "Navigation category", like(android_category="navigation")),
        case("override_ongoing", "Ongoing notification", like(is_ongoing=True)),
        case(
            "override_ride_app",
            "Uber update",
            features("v", "com.ubercab", "other"),
        ),
        case(
            "override_sms_otp",
            "OTP from the SMS app",
            features("v", "com.google.android.apps.messaging", "messaging", otp_pattern=True),
        ),
        case("override_repeat_ping", "Mom's third try", message(same_sender_recent_pings=2)),
        case(
            "no_override_second_ping",
            "A second message alone is not yet a repeated ping",
            message(same_sender_recent_pings=1),
        ),
        case(
            "override_order",
            "Several overrides match; the always-deliver app wins",
            like(android_category="call", is_ongoing=True),
            context(always_deliver_packages=[INSTAGRAM]),
        ),
        case(
            "no_override_otp_bait",
            "OTP-looking promo from a shopping app is not trusted",
            features(
                "v", "com.flipkart.android", "shopping_promo", bait_hit=True, otp_pattern=True
            ),
        ),
        case(
            "no_override_group_without_sender",
            "Repeats without a sender are not a repeated ping",
            message(sender_hash=None, same_sender_recent_pings=3),
        ),
    ]


def policy_cases() -> list[dict[str, Any]]:
    return [
        case(
            "observe_mode", "Observe mode logs a would-be hold", like(), context(autonomy="OBSERVE")
        ),
        case("suggest_mode", "Suggest mode recommends a hold", like(), context(autonomy="SUGGEST")),
        case(
            "explore_below_epsilon",
            "u below epsilon flips the hold",
            like(),
            context(exploration_draw=0.05),
        ),
        case(
            "explore_at_epsilon",
            "u equal to epsilon does not explore",
            like(),
            context(exploration_draw=0.1),
        ),
        case(
            "explore_not_in_suggest",
            "No exploration outside Auto",
            like(),
            context(autonomy="SUGGEST", exploration_draw=0.0),
        ),
        case(
            "might_matter_never_explored",
            "An urgent message from Mom with u = 0",
            message(urgency_hit=True),
            context(exploration_draw=0.0),
        ),
        case(
            "deliver_better",
            "Heavy delay penalty makes holding the costlier choice",
            like(),
            patches=[patch("policy", "w", 20.0)],
        ),
        case(
            "pi_star_high",
            "A stricter confidence bar turns the hold into a delivery",
            like(),
            patches=[patch("policy", "pi_star", 0.95)],
        ),
        case(
            "lambda_one",
            "No asymmetry between showing and holding costs",
            features("v", "com.nis.app", "news"),
            patches=[patch("policy", "lambda", 1.0)],
        ),
        case(
            "category_prior_clipped",
            "A huge category multiplier is clipped to 0.95",
            like(),
            context(state="FREE"),
            patches=[patch("pull", "category", "social", "r_c", 50.0)],
        ),
        case(
            "epsilon_zero",
            "With epsilon 0 nothing is explored, even at u = 0",
            like(),
            context(exploration_draw=0.0),
            patches=[patch("policy", "epsilon", 0.0)],
        ),
    ]


def pull_learning_cases() -> list[dict[str, Any]]:
    return [
        case(
            "learn_ten_opens",
            "Ten 5-minute opens of social in steady focus",
            like(),
            events=[opened(5.0)] * 10,
        ),
        case(
            "learn_ten_ignores", "Ten ignored social notifications", like(), events=[ignored()] * 10
        ),
        case(
            "learn_varied_minutes",
            "Opens of 1, 3, 8, 20 and 45 minutes update the spread",
            like(),
            events=[opened(m) for m in (1.0, 3.0, 8.0, 20.0, 45.0)],
        ),
        case(
            "learn_tray_release",
            "Tray releases teach only the FREE cell",
            like(),
            context(state="FREE"),
            events=[opened(4.0, tray=True), opened(6.0, tray=True)],
        ),
        case(
            "learn_zero_minutes",
            "A 0-minute open is floored before the log",
            like(),
            events=[opened(0.0)] * 3,
        ),
        case(
            "learn_capped_minutes",
            "A 75-minute open counts as 60",
            like(),
            events=[opened(75.0)] * 3,
        ),
        case(
            "learn_cell_pool_cap",
            "Fifty restless social events strengthen the steady cell prior up to the cap",
            like(),
            events=[ignored(state="FOCUS_RESTLESS")] * 50,
        ),
        case(
            "learn_other_category",
            "Short-video opens move social only through the global rate",
            like(),
            events=[opened(10.0, category="short_video")] * 10,
        ),
        case(
            "learn_mixed_history",
            "Opens and ignores in two states",
            like(),
            context(state="FOCUS_RESTLESS"),
            events=[
                opened(3.0),
                ignored(),
                opened(12.0, state="FOCUS_RESTLESS"),
                ignored("social", "FOCUS_RESTLESS"),
            ],
        ),
    ]


def worth_learning_cases() -> list[dict[str, Any]]:
    return [
        case(
            "worth_not_worth_it",
            "Five Not worth it taps on Instagram",
            like(),
            events=[label("NOT_WORTH_IT")] * 5,
        ),
        case(
            "worth_worth_it",
            "Five Worth it taps on Instagram",
            like(),
            events=[label("WORTH_IT")] * 5,
        ),
        case(
            "worth_sender_negative",
            "Three Correct hold taps on Mom use the sender path",
            message(),
            events=[label("CORRECT_HOLD", WHATSAPP, MOM)] * 3,
        ),
        case(
            "worth_other_sender",
            "Labels on Mom move a friend only through the app layer",
            message(FRIEND),
            events=[label("CORRECT_HOLD", WHATSAPP, MOM)] * 3,
        ),
        case(
            "worth_mixed_labels",
            "Release now, Always deliver and Not worth it on one sender",
            message(),
            events=[
                label("RELEASE_NOW", WHATSAPP, MOM),
                label("ALWAYS_DELIVER", WHATSAPP, MOM),
                label("NOT_WORTH_IT", WHATSAPP, MOM),
            ],
        ),
        case(
            "worth_unchanged_by_fast_opens",
            "Ten fast opens leave worth exactly where it was",
            like(),
            events=[opened(0.2)] * 10,
        ),
    ]


def demo_and_misc_cases() -> list[dict[str, Any]]:
    return [
        case(
            "demo_fresh",
            "Beat 1 like with demo learning weights",
            like(),
            context(demo_learning=True),
        ),
        case(
            "demo_four_correct_holds",
            "Beat 1 after four Correct hold taps",
            like(),
            context(demo_learning=True),
            events=[label("CORRECT_HOLD")] * 4,
        ),
        case(
            "demo_four_worth_it",
            "Beat 1 after four Worth it taps",
            like(),
            context(demo_learning=True),
            events=[label("WORTH_IT")] * 4,
        ),
        case(
            "release_now",
            "Break is due now, so holding costs nothing",
            like(),
            context(minutes_until_release=0.0),
        ),
        case(
            "release_at_cap",
            "Break is 45 minutes away",
            like(),
            context(minutes_until_release=45.0),
        ),
        case(
            "fatigue_high",
            "Fatigue risk shows on the card but never changes the decision",
            like(),
            context(fatigue_risk="HIGH"),
        ),
        case(
            "group_chat",
            "WhatsApp group chat",
            message("s_group", group_chat=True, sender_display_name=None),
        ),
        case(
            "priority_conversation",
            "Message in a conversation the user marked priority",
            message(FRIEND, priority_conversation=True),
        ),
        case("urgent_message", "Urgent words in a direct message", message(urgency_hit=True)),
    ]


def build_cases() -> list[dict[str, Any]]:
    return (
        fresh_cases()
        + override_cases()
        + policy_cases()
        + pull_learning_cases()
        + worth_learning_cases()
        + demo_and_misc_cases()
    )


def build_document() -> dict[str, Any]:
    base_params = load_params(PARAMS_DIR / PARAMS_FILE)
    vectors = [case_ | {"expected": run_vector(case_, base_params)} for case_ in build_cases()]
    ids = [vector["id"] for vector in vectors]
    if len(set(ids)) != len(ids):
        raise ValueError("Test vector ids must be unique")
    return {
        "version": "1.0",
        "params_file": PARAMS_FILE,
        "tolerance": {
            "absolute": ABSOLUTE_TOLERANCE,
            "exp_fields": EXP_TOLERANCE,
            "exp_field_names": sorted(EXP_FIELDS),
            "not_compared": ["explanation"],
        },
        "vectors": vectors,
    }


def main() -> None:
    document = build_document()
    OUTPUT_PATH.write_text(json.dumps(document, indent=1) + "\n", encoding="utf-8")
    print(f"Wrote {len(document['vectors'])} test vectors to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
