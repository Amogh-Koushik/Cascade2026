"""
Pratibimb Twin Lab Simulator (sim/simulator.py)
Author: Safal (ML engine + Evaluation)
CASCADE '26 · Theme 3: Attention Economy · PS 3.3

Simulates synthetic user trajectories, notification arrivals, and known
counterfactual outcomes (Y(0) if held vs. Y(1) if shown) under true behavioral distributions.
Generates shifted true users for non-circular evaluation and exports starter twin_params JSONs.
"""

import json
import math
import os
from typing import Dict, Any, List, Tuple
import numpy as np

CATEGORIES = [
    "short_video", "social", "messaging", 
    "shopping_promo", "news", "work_email", "other"
]

APP_CATEGORY_MAP = {
    "com.instagram.android": "short_video",
    "com.google.android.youtube": "short_video",
    "com.zhiliaoapp.musically": "short_video",
    "com.facebook.katana": "social",
    "com.twitter.android": "social",
    "com.reddit.frontpage": "social",
    "com.linkedin.android": "social",
    "com.whatsapp": "messaging",
    "org.telegram.messenger": "messaging",
    "com.discord": "messaging",
    "com.google.android.apps.messaging": "messaging",
    "com.amazon.mShop.android.shopping": "shopping_promo",
    "com.flipkart.android": "shopping_promo",
    "in.swiggy.android": "shopping_promo",
    "com.application.zomato": "shopping_promo",
    "com.google.android.apps.magazines": "news",
    "com.inshorts": "news",
    "com.google.android.gm": "work_email",
    "com.microsoft.office.outlook": "work_email",
    "com.slack": "work_email"
}

HAND_SET_WORTH_WEIGHTS = {
    "is_conversation": 1.6,
    "cat_messaging": 0.8,
    "priority_conversation": 2.0,
    "group_chat": -0.9,
    "cat_shopping_promo": -1.8,
    "cat_social_nonconversation": -1.4,
    "cat_short_video": -1.6,
    "bait_hit": -1.2,
    "urgency_hit_in_conversation": 1.5,
    "same_sender_repeats_3m": 0.9
}

class ShiftedTrueUser:
    """A synthetic user whose ground truth parameters are shifted away from priors."""
    def __init__(self, persona_data: Dict[str, Any], seed: int = 101):
        self.rng = np.random.default_rng(seed)
        self.name = persona_data.get("name", "Shifted User")
        
        # Ground truth open baseline (shifted around Ulster 0.22)
        self.true_m_g = float(np.clip(self.rng.normal(0.22, 0.04), 0.10, 0.40))
        
        # Shift category multipliers by 0.5x - 2.0x
        base_r_c = persona_data["category_pull_multipliers"]
        self.true_r_c = {}
        for cat in CATEGORIES:
            shift = self.rng.uniform(0.6, 1.6)
            self.true_r_c[cat] = float(np.clip(base_r_c.get(cat, 1.0) * shift, 0.1, 3.5))
            
        # Shift lognormal duration parameters by +/- 0.35 log-min
        base_dur = persona_data["category_minutes"]
        self.true_dur = {}
        for cat in CATEGORIES:
            mu_shift = float(self.rng.uniform(-0.35, 0.35))
            sigma_scale = float(self.rng.uniform(0.85, 1.15))
            b_mu = base_dur.get(cat, {"mu": 1.5, "sigma": 0.7})["mu"]
            b_sig = base_dur.get(cat, {"mu": 1.5, "sigma": 0.7})["sigma"]
            self.true_dur[cat] = {
                "mu": float(np.clip(b_mu + mu_shift, 0.2, 3.5)),
                "sigma": float(np.clip(b_sig * sigma_scale, 0.3, 1.2))
            }
            
        self.latent_importance_rates = persona_data.get("latent_importance_rates", {})
        self.state_multipliers = persona_data.get("state_multipliers", {})

class TwinLabSimulator:
    """Generates synthetic notification streams and exact counterfactual outcomes."""
    def __init__(self, personas_file: str = "sim/personas.json", draw_bank_file: str = "fit/draw_bank.json"):
        if os.path.exists(personas_file):
            with open(personas_file, "r", encoding="utf-8") as f:
                self.personas = json.load(f)["personas"]
        else:
            self.personas = {}
            
        if os.path.exists(draw_bank_file):
            with open(draw_bank_file, "r", encoding="utf-8") as f:
                self.draw_bank = json.load(f)["draw_bank"]
        else:
            self.draw_bank = []

    def export_starter_params(self, persona_key: str, output_path: str):
        """Builds twin_params_<persona>.json for Android/Python engines."""
        persona = self.personas.get(persona_key, {})
        category_params = {}
        for cat in CATEGORIES:
            r_c = persona.get("category_pull_multipliers", {}).get(cat, 1.0)
            dur = persona.get("category_minutes", {}).get(cat, {"mu": 1.5, "sigma": 0.7})
            category_params[cat] = {
                "r_c": r_c,
                "mu_c0": dur["mu"],
                "sigma_c": dur["sigma"]
            }

        params = {
            "version": "1.0",
            "persona": persona_key,
            "provenance": {
                "m_g": "literature value: Ulster field study (78% dismissed)",
                "mu_c0": "real (public): LSApp empirical session distributions",
                "r_c": "assumed starter: persona profile",
                "worth_weights": "hand-set starter: sanity checked"
            },
            "app_category_map": APP_CATEGORY_MAP,
            "pull": {
                "global": {"m_g": 0.22, "n_g": 20},
                "category": category_params,
                "state": {
                    "FREE": {"r_s": 1.0, "delta_s": 0.0},
                    "FOCUS_STEADY": {"r_s": 0.8, "delta_s": 0.0},
                    "FOCUS_RESTLESS": {"r_s": 1.3, "delta_s": 0.2}
                },
                "prior_weights": {
                    "category": 10,
                    "cell_base": 5,
                    "cell_pool": 0.25,
                    "cell_pool_cap": 40,
                    "mu_category": 8,
                    "mu_cell": 4
                }
            },
            "worth": {
                "bias": -1.5,
                "weights": HAND_SET_WORTH_WEIGHTS,
                "k_app": 20,
                "k_sender": 6
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
                "repeat_ping": {"count": 2, "window_min": 3}
            },
            "demo_learning": {
                "k_app": 4,
                "k_sender": 2,
                "prior_weights": {
                    "category": 3,
                    "cell_base": 2,
                    "mu_category": 3,
                    "mu_cell": 2
                }
            },
            "never_hold": {
                "categories": ["call", "alarm", "navigation"],
                "otp_sources": ["com.google.android.apps.messaging", "com.whatsapp"],
                "packages": ["com.google.android.dialer", "com.google.android.deskclock"]
            },
            "lexicons": {
                "urgency": ["urgent", "call me", "emergency", "jaldi", "asap", "hospital", "admitted", "otp", "code"],
                "bait": ["liked your", "don't miss", "% off", "trending", "viewed your", "sale ends", "flash sale"]
            },
            "draw_bank": self.draw_bank
        }

        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(params, f, indent=2)
        print(f"Exported starter parameters to {output_path}")

    def simulate_event_stream(
        self,
        true_user: ShiftedTrueUser,
        n_notifications: int = 500,
        session_duration_min: int = 50,
        seed: int = 42
    ) -> List[Dict[str, Any]]:
        """
        Simulates notification events with ground truth counterfactuals:
        Branch A: Y(1) Show Now -> focus minutes lost
        Branch B: Y(0) Hold -> delay penalty
        """
        rng = np.random.default_rng(seed)
        events = []
        
        for i in range(n_notifications):
            cat = rng.choice(CATEGORIES, p=[0.20, 0.25, 0.25, 0.10, 0.05, 0.10, 0.05])
            
            # Contextual state during focus
            is_focus = bool(rng.choice([True, False], p=[0.75, 0.25]))
            if is_focus:
                state = rng.choice(["FOCUS_STEADY", "FOCUS_RESTLESS"], p=[0.8, 0.2])
                session_time_elapsed = rng.uniform(2, session_duration_min - 2)
                d_remain = min(session_duration_min - session_time_elapsed, 45.0)
            else:
                state = "FREE"
                d_remain = 0.0
                
            # Structural features
            is_conv = bool(cat in ["messaging", "work_email"])
            group_chat = bool(is_conv and rng.choice([True, False], p=[0.4, 0.6]))
            is_repeat = bool(is_conv and not group_chat and rng.choice([True, False], p=[0.15, 0.85]))
            bait_hit = bool(cat in ["shopping_promo", "social", "short_video"] and rng.choice([True, False], p=[0.35, 0.65]))
            urgency_hit = bool(is_conv and rng.choice([True, False], p=[0.12, 0.88]))
            
            # Latent true importance (ground truth)
            base_imp = true_user.latent_importance_rates.get(cat, 0.1)
            if urgency_hit or is_repeat:
                base_imp = min(0.95, base_imp + 0.40)
            if bait_hit:
                base_imp = max(0.01, base_imp - 0.20)
            true_is_important = bool(rng.uniform(0, 1) < base_imp)
            
            # Shared random seed for Branch A & B
            sub_seed = rng.integers(1, 1_000_000_000)
            branch_rng = np.random.default_rng(sub_seed)
            
            # --- Branch A: Show Now (Y(1)) ---
            r_c = true_user.true_r_c[cat]
            r_s = true_user.state_multipliers.get(state, {}).get("r_s", 1.0)
            true_p_open = float(np.clip(true_user.true_m_g * r_c * r_s, 0.01, 0.95))
            
            did_open = bool(branch_rng.uniform(0, 1) < true_p_open)
            if did_open:
                mu_true = true_user.true_dur[cat]["mu"]
                sig_true = true_user.true_dur[cat]["sigma"]
                delta_s = true_user.state_multipliers.get(state, {}).get("delta_s", 0.0)
                # Lognormal sample
                log_min = branch_rng.normal(mu_true + delta_s, sig_true)
                mins_lost = float(np.clip(math.exp(log_min), 0.5, 60.0))
            else:
                mins_lost = 0.5 # Glance cost c_glance
                
            # --- Branch B: Hold Until Break (Y(0)) ---
            # If notification is held, immediate focus loss = 0.
            # Delay penalty applies only IF it was truly important!
            w = 0.5
            delay_penalty = (w * d_remain) if true_is_important else 0.0
            
            event = {
                "event_id": f"sim_notif_{i:04d}",
                "category": cat,
                "state": state,
                "is_focus": is_focus,
                "d_minutes_until_release": round(d_remain, 1),
                "features": {
                    "is_conversation": is_conv,
                    "group_chat": group_chat,
                    "same_sender_repeats_3m": is_repeat,
                    "bait_hit": bait_hit,
                    "urgency_hit": urgency_hit
                },
                "ground_truth": {
                    "true_is_important": true_is_important,
                    "branch_show_now": {
                        "did_open": did_open,
                        "minutes_lost": round(mins_lost, 2)
                    },
                    "branch_hold": {
                        "immediate_minutes_lost": 0.0,
                        "delay_penalty_minutes": round(delay_penalty, 2)
                    },
                    "net_minutes_protected_if_held": round(mins_lost - delay_penalty, 2)
                }
            }
            events.append(event)
            
        return events

if __name__ == "__main__":
    sim = TwinLabSimulator()
    # 1. Export starter parameters for student_coder
    starter_path = "fit/twin_params_student_coder.json"
    sim.export_starter_params("student_coder", starter_path)
    
    # Also export night_shift_nurse for persona switcher demo
    nurse_path = "fit/twin_params_night_shift_nurse.json"
    sim.export_starter_params("night_shift_nurse", nurse_path)

    # 2. Simulate 500 events on a Shifted True User
    student_persona = sim.personas["student_coder"]
    shifted_user = ShiftedTrueUser(student_persona, seed=42)
    print(f"\nSimulating 500 counterfactual notifications for: {shifted_user.name}")
    events = sim.simulate_event_stream(shifted_user, n_notifications=500, seed=42)
    
    sim_out = "sim/simulated_events_student_coder.json"
    with open(sim_out, "w", encoding="utf-8") as f:
        json.dump(events[:20], f, indent=2) # Save sample of 20 for inspection
    print(f"Generated {len(events)} events (saved sample to {sim_out})")
    
    # Calculate ground truth statistics
    derailed = [e for e in events if e["ground_truth"]["branch_show_now"]["did_open"]]
    avg_lost = np.mean([e["ground_truth"]["branch_show_now"]["minutes_lost"] for e in derailed])
    important_count = sum(1 for e in events if e["ground_truth"]["true_is_important"])
    
    print("\n--- Ground Truth Simulation Summary ---")
    print(f"Total Notifications: {len(events)}")
    print(f"Open / Derailment Rate: {len(derailed)/len(events):.1%}")
    print(f"Average Minutes Lost when opened: {avg_lost:.1f} min")
    print(f"Truly Important Notifications: {important_count} ({important_count/len(events):.1%})")
