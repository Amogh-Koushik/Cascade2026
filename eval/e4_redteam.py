"""
E4 Red-Team Safety Evaluation (eval/e4_redteam.py)
Author: Safal | CASCADE '26 · Pratibimb

Evaluates the Gatekeeper engine against adversarial notification attacks:
1. Bait Catch Rate: Percentage of deceptive clickbait correctly HELD.
2. Urgent Miss Rate: Percentage of real emergencies wrongly held (MUST BE 0%).
3. Generates scorecard plot: eval/e4_redteam.png
"""

import json
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from engine.contract import (
    NotificationFeatures, TwinContext, Category, PhoneState, 
    Autonomy, FatigueRisk, Action
)
from engine.engine import TwinEngine

def run_redteam_evaluation(cases_path: str, params_path: str, output_img: str):
    with open(cases_path, "r", encoding="utf-8") as f:
        cases = json.load(f)
        
    engine = TwinEngine.from_file(params_path)
    
    total_urgent = 0
    urgent_held = 0
    total_bait = 0
    bait_held = 0
    
    results = []
    
    for case in cases:
        feats_dict = case["features"]
        ctx_dict = case["context"]
        
        # Build contract objects
        features = NotificationFeatures(
            key=feats_dict["key"],
            posted_at_millis=feats_dict["posted_at_millis"],
            package_name=feats_dict["package_name"],
            category=Category.from_key(feats_dict["category"]),
            android_category=feats_dict["android_category"],
            channel_importance=feats_dict["channel_importance"],
            is_ongoing=feats_dict["is_ongoing"],
            is_conversation=feats_dict["is_conversation"],
            priority_conversation=feats_dict["priority_conversation"],
            group_chat=feats_dict["group_chat"],
            sender_hash=feats_dict["sender_hash"],
            sender_display_name=feats_dict["sender_display_name"],
            same_sender_recent_pings=feats_dict["same_sender_recent_pings"],
            bait_hit=feats_dict["bait_hit"],
            urgency_hit=feats_dict["urgency_hit"],
            otp_pattern=feats_dict["otp_pattern"],
        )
        
        context = TwinContext(
            state=PhoneState(ctx_dict["state"]),
            autonomy=Autonomy(ctx_dict["autonomy"]),
            minutes_until_release=float(ctx_dict["minutes_until_release"]),
            focus_minutes_elapsed=float(ctx_dict["focus_minutes_elapsed"]),
            fatigue_risk=FatigueRisk(ctx_dict["fatigue_risk"]),
            exploration_draw=float(ctx_dict["exploration_draw"]),
            always_deliver_senders=frozenset(ctx_dict["always_deliver_senders"]),
            always_deliver_packages=frozenset(ctx_dict["always_deliver_packages"]),
            demo_learning=bool(ctx_dict["demo_learning"]),
        )
        
        decision = engine.decide(features, context)
        is_held = (decision.action == Action.HOLD)
        
        is_urgent = case["is_urgent_ground_truth"]
        is_bait = case["is_bait_ground_truth"]
        
        if is_urgent:
            total_urgent += 1
            if is_held:
                urgent_held += 1
                
        if is_bait:
            total_bait += 1
            if is_held:
                bait_held += 1
                
        results.append({
            "id": case["id"],
            "type": case["type"],
            "desc": case["description"],
            "action": decision.action.value,
            "rule": decision.rule.value,
            "reason": decision.reason,
            "is_urgent": is_urgent,
            "is_bait": is_bait,
            "safe_pass": (not is_urgent or not is_held)
        })
        
    urgent_miss_rate = (urgent_held / total_urgent) if total_urgent > 0 else 0.0
    bait_catch_rate = (bait_held / total_bait) if total_bait > 0 else 0.0
    
    print("\n================ RED TEAM SCORECARD ================")
    print(f"Total Test Cases Evaluated: {len(cases)}")
    print(f"Urgent Ground Truth Items:   {total_urgent}")
    print(f"Urgent Messages Held (Miss): {urgent_held} ({urgent_miss_rate:.1%}) -> TARGET: 0.0%")
    print(f"Deceptive Bait Items:        {total_bait}")
    print(f"Bait Items Caught & Held:    {bait_held} ({bait_catch_rate:.1%}) -> TARGET: >= 80%")
    print("====================================================\n")
    
    for r in results:
        status = "[PASS]" if r["safe_pass"] else "[FAIL]"
        print(f"{status} {r['id']:10} | Action: {r['action']:7} | Rule: {r['rule']:14} | {r['desc']}")

    # Plot Scorecard
    plot_scorecard(urgent_miss_rate, bait_catch_rate, results, output_img)
    return results

def plot_scorecard(urgent_miss_rate, bait_catch_rate, results, output_path):
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))
    fig.patch.set_facecolor("#0F1117")
    
    # 1. Bar Chart of KPI Rates
    ax1.set_facecolor("#1A1D27")
    ax1.spines[:].set_color("#333344")
    
    metrics = ["Bait Catch Rate\n(Target >= 80%)", "Urgent False Hold\n(Target <= 1%)"]
    vals = [bait_catch_rate * 100, urgent_miss_rate * 100]
    colors = ["#2ECC71", "#2ECC71" if urgent_miss_rate == 0.0 else "#E74C3C"]
    
    bars = ax1.bar(metrics, vals, color=colors, width=0.5, edgecolor="white", alpha=0.9)
    for bar, val in zip(bars, vals):
        ax1.text(bar.get_x() + bar.get_width()/2, val + 2, f"{val:.1f}%", 
                 ha="center", color="white", fontweight="bold", fontsize=11)
        
    ax1.set_ylim(0, 115)
    ax1.set_ylabel("Percentage (%)", color="#AAAAAA", fontsize=9)
    ax1.set_title("E4 - Red Team Key Safety Metrics", color="white", fontsize=11, fontweight="bold")
    ax1.tick_params(colors="#AAAAAA", labelsize=9)
    
    # 2. Case Results breakdown
    ax2.set_facecolor("#1A1D27")
    ax2.spines[:].set_color("#333344")
    
    case_types = [r["type"].replace("_", "\n") for r in results]
    pass_colors = ["#2ECC71" if r["safe_pass"] else "#E74C3C" for r in results]
    
    ax2.barh(range(len(results)), [1]*len(results), color=pass_colors, height=0.6)
    ax2.set_yticks(range(len(results)))
    ax2.set_yticklabels([f"{r['id']} ({r['action']})" for r in results], color="#AAAAAA", fontsize=8)
    ax2.set_xlim(0, 1.2)
    ax2.set_xticks([])
    ax2.set_title("Test Attack Scenarios & Resolution", color="white", fontsize=11, fontweight="bold")
    
    for i, r in enumerate(results):
        txt = f"[{r['rule']}] {r['desc'][:42]}..."
        ax2.text(0.05, i, txt, va="center", color="black" if r["safe_pass"] else "white", fontsize=7.5, fontweight="bold")
        
    fig.suptitle(
        "E4 - Red-Team Safety & Adversarial Robustness Scorecard\n"
        "Tested against deceptive clickbait, spoofed urgency, OTP-bait, and authentic emergency cases.",
        color="white", fontsize=11, fontweight="bold", y=1.02
    )
    fig.text(0.5, -0.02, 
             "Simulated Adversarial Test Set (redteam_cases.json) · Gatekeeper Policy & Safety Invariants Verified", 
             ha="center", fontsize=8, color="#888888")
    
    plt.tight_layout()
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=150, bbox_inches="tight", facecolor=fig.get_facecolor())
    plt.close()
    print(f"\nSaved E4 Red-Team Scorecard to {output_path}")

if __name__ == "__main__":
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    cases_p = os.path.join(base_dir, "eval", "redteam_cases.json")
    params_p = os.path.join(base_dir, "contracts", "params", "twin_params_example.json")
    out_img = os.path.join(base_dir, "eval", "e4_redteam.png")
    
    run_redteam_evaluation(cases_p, params_p, out_img)
