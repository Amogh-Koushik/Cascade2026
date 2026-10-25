# Writes contracts/params/twin_params_example.json: a complete, schema-valid params file with
# placeholder numbers, so the app, the stub engine and engine.py can be built before Safal's
# fitted files exist. Every value here is replaced by the fit; the file says so in "placeholder".
#
# Run: uv run python -m contracts.make_example_params

import json
from typing import Any

from contracts.lexicons import LEXICONS
from contracts.params import PARAMS_DIR, load_draw_bank, validate_params

OUTPUT_PATH = PARAMS_DIR / "twin_params_example.json"

# Per category: pull multiplier r_c, prior mean of log minutes-if-opened mu_c0, and its spread.
CATEGORY_PULL = {
    "short_video": {"r_c": 1.8, "mu_c0": 2.5, "sigma_c": 0.9},
    "social": {"r_c": 1.6, "mu_c0": 2.1, "sigma_c": 0.8},
    "messaging": {"r_c": 1.4, "mu_c0": 1.2, "sigma_c": 0.7},
    "shopping_promo": {"r_c": 0.6, "mu_c0": 1.0, "sigma_c": 0.7},
    "news": {"r_c": 0.8, "mu_c0": 1.3, "sigma_c": 0.7},
    "work_email": {"r_c": 1.1, "mu_c0": 1.5, "sigma_c": 0.6},
    "other": {"r_c": 0.7, "mu_c0": 1.0, "sigma_c": 0.8},
}

APP_CATEGORY_MAP = {
    "com.instagram.android": "social",
    "com.snapchat.android": "social",
    "com.linkedin.android": "social",
    "com.google.android.youtube": "short_video",
    "com.whatsapp": "messaging",
    "org.telegram.messenger": "messaging",
    "com.google.android.apps.messaging": "messaging",
    "com.samsung.android.messaging": "messaging",
    "in.swiggy.android": "shopping_promo",
    "com.application.zomato": "shopping_promo",
    "com.flipkart.android": "shopping_promo",
    "in.amazon.mShop.android.shopping": "shopping_promo",
    "com.myntra.android": "shopping_promo",
    "com.nis.app": "news",
    "com.google.android.gm": "work_email",
    "com.microsoft.office.outlook": "work_email",
    "com.Slack": "work_email",
}


def build_example_params() -> dict[str, Any]:
    return {
        "version": "0.1",
        "persona": "example",
        "placeholder": True,
        "provenance": {
            "everything": "placeholder for integration; replaced by the fitted persona files",
        },
        "app_category_map": APP_CATEGORY_MAP,
        "pull": {
            "global": {"m_g": 0.22, "n_g": 20},
            "category": CATEGORY_PULL,
            "state": {
                "FREE": {"r_s": 1.0, "delta_s": 0.0},
                "FOCUS_STEADY": {"r_s": 0.8, "delta_s": 0.0},
                "FOCUS_RESTLESS": {"r_s": 1.3, "delta_s": 0.2},
            },
            "prior_weights": {
                "category": 10,
                "cell_base": 5,
                "cell_pool": 0.25,
                "cell_pool_cap": 40,
                "mu_category": 8,
                "mu_cell": 4,
                "sigma": 8,
            },
        },
        "worth": {
            "bias": -1.5,
            "weights": {
                "is_conversation": 1.6,
                "cat_messaging": 0.8,
                "cat_work_email": 1.2,
                "priority_conversation": 2.0,
                "group_chat": -0.9,
                "cat_shopping_promo": -1.8,
                "cat_social_nonconversation": -1.4,
                "cat_short_video": -1.6,
                "bait_hit": -1.2,
                "urgency_hit_in_conversation": 1.5,
                "same_sender_repeat": 0.9,
            },
            "k_app": 20,
            "k_sender": 6,
        },
        "policy": {
            "lambda": 2.0,
            "w": 0.5,
            "pi_star": 0.7,
            "epsilon": 0.1,
            "cap_min": 45,
            "c_glance": 0.5,
            "open_window_min": 10,
            "idle_min": 2,
            "minutes_lost_cap_min": 60,
            "minutes_floor_min": 0.1,
            "might_matter_upper80": 0.5,
            "explore_upper80": 0.3,
            "upper80_z": 0.84,
            "low_confidence_width": 0.4,
            "repeat_ping": {"count": 2, "window_min": 3},
        },
        "demo_learning": {
            "k_app": 4,
            "k_sender": 2,
            "prior_weights": {"category": 3, "cell_base": 2, "mu_category": 3, "mu_cell": 2},
        },
        "never_hold": {
            "android_categories": ["call", "alarm", "navigation", "transport", "stopwatch"],
            "packages": [
                "com.google.android.apps.maps",
                "com.ubercab",
                "com.olacabs.customer",
                "com.rapido.passenger",
            ],
            "otp_source_packages": [
                "com.google.android.apps.messaging",
                "com.samsung.android.messaging",
                "com.whatsapp",
                "com.google.android.apps.nbu.paisa.user",
                "com.phonepe.app",
                "net.one97.paytm",
            ],
        },
        "lexicons": {name: list(words) for name, words in LEXICONS.items()},
        "draw_bank": load_draw_bank(),
    }


def main() -> None:
    params = build_example_params()
    validate_params(params)
    PARAMS_DIR.mkdir(exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(params, indent=1) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
