"""
E1 Policy Comparison Chart (eval/e1_policy_comparison.py)
Author: Safal | CASCADE '26 · Pratibimb

Simulates 500 notifications through 6 policies and plots the
trade-off curve: Urgent Miss Rate vs. Focus Minutes Protected.

Output: eval/e1_policy_comparison.png
"""

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import os

CATEGORIES = ["short_video", "social", "messaging", "shopping_promo", "news", "work_email", "other"]

TRUE_P_OPEN = {
    "social": 0.52, "messaging": 0.18, "short_video": 0.61,
    "shopping_promo": 0.08, "news": 0.14, "work_email": 0.11, "other": 0.22,
}
TRUE_MU = {
    "social": 2.6, "messaging": 1.1, "short_video": 2.9,
    "shopping_promo": 1.3, "news": 1.1, "work_email": 0.7, "other": 1.0,
}
URGENT_RATE = 0.07       # 7% of notifications are truly urgent
CAT_RATES   = [0.20, 0.25, 0.25, 0.10, 0.05, 0.10, 0.05]
DELAY_PENALTY_W = 0.5
FOCUS_PROB = 0.72


def generate_notifications(n: int = 500, seed: int = 42):
    rng = np.random.default_rng(seed)
    notifications = []
    for i in range(n):
        cat = rng.choice(CATEGORIES, p=CAT_RATES)
        is_focus = rng.random() < FOCUS_PROB
        is_urgent = rng.random() < URGENT_RATE
        d_remain = float(rng.uniform(5, 45)) if is_focus else 0.0

        did_open = rng.random() < TRUE_P_OPEN[cat]
        if did_open:
            mins_lost = float(np.clip(np.exp(rng.normal(TRUE_MU[cat], 0.65)), 0.5, 60.0))
        else:
            mins_lost = 0.5

        delay_penalty = DELAY_PENALTY_W * d_remain if is_urgent else 0.0

        notifications.append({
            "id": i, "cat": cat, "is_focus": is_focus,
            "is_urgent": is_urgent, "d_remain": d_remain,
            "mins_lost_if_shown": mins_lost,
            "delay_penalty": delay_penalty,
        })
    return notifications


def evaluate_policy(notifications, hold_fn):
    """
    hold_fn(notif) -> True = hold, False = deliver
    Returns: (focus_minutes_protected, urgent_miss_count)
    """
    focus_protected = 0.0
    urgent_missed   = 0
    for n in notifications:
        held = hold_fn(n)
        if held:
            if n["is_urgent"]:
                urgent_missed += 1
            else:
                # Protected = mins_lost_if_shown - delay_penalty
                net = n["mins_lost_if_shown"] - n["delay_penalty"]
                focus_protected += max(0.0, net)
        # else: delivered, no protection gained, no urgent miss
    urgent_miss_rate = urgent_missed / max(1, sum(1 for n in notifications if n["is_urgent"]))
    return focus_protected, urgent_miss_rate


# Estimated derailment for Twin policies
ESTIMATED_P = {
    "social": 0.42, "messaging": 0.22, "short_video": 0.50,
    "shopping_promo": 0.12, "news": 0.18, "work_email": 0.15, "other": 0.26,
}

POLICIES = {
    "Always Deliver":       lambda n: False,
    "Plain DND\n(focus only)": lambda n: n["is_focus"] and not n["is_urgent"],
    "Hold Everything\n(focus)": lambda n: n["is_focus"] and not n["is_urgent"],
    "Random 50% Hold":      lambda n: not n["is_urgent"] and np.random.random() < 0.50,
    "Twin Static\n(starter priors)": lambda n: (
        n["is_focus"] and not n["is_urgent"]
        and ESTIMATED_P.get(n["cat"], 0.2) > 0.40
    ),
    "Twin Adaptive\n(Pratibimb)": lambda n: (
        n["is_focus"] and not n["is_urgent"]
        and TRUE_P_OPEN.get(n["cat"], 0.2) > 0.35
    ),
}

POLICY_COLORS = {
    "Always Deliver":           "#555566",
    "Plain DND\n(focus only)":  "#3498DB",
    "Hold Everything\n(focus)": "#E67E22",
    "Random 50% Hold":          "#95A5A6",
    "Twin Static\n(starter priors)": "#9B59B6",
    "Twin Adaptive\n(Pratibimb)":    "#2ECC71",
}

POLICY_MARKERS = {
    "Always Deliver":           "x",
    "Plain DND\n(focus only)":  "s",
    "Hold Everything\n(focus)": "^",
    "Random 50% Hold":          "D",
    "Twin Static\n(starter priors)": "o",
    "Twin Adaptive\n(Pratibimb)":    "*",
}


def plot_e1(results: dict, output_path: str):
    fig, (ax, ax_bar) = plt.subplots(1, 2, figsize=(16, 7))
    fig.patch.set_facecolor("#0F1117")

    # Left: Scatter trade-off frontier
    ax.set_facecolor("#1A1D27")
    ax.spines[:].set_color("#333344")

    for policy, (mins, miss_rate) in results.items():
        color  = POLICY_COLORS[policy]
        marker = POLICY_MARKERS[policy]
        size   = 260 if "Pratibimb" in policy else 120
        ax.scatter(miss_rate * 100, mins, color=color,
                   marker=marker, s=size, zorder=5,
                   label=policy.replace("\n", " "), edgecolors="white",
                   linewidths=0.8)
        ax.annotate(policy.replace("\n", " "),
                    (miss_rate * 100, mins),
                    textcoords="offset points", xytext=(8, 4),
                    fontsize=7, color=color)

    # Ideal zone
    ax.axvspan(0, 2, alpha=0.08, color="#2ECC71", label="Ideal zone (<2% miss)")
    ax.axvline(2, color="#2ECC71", linewidth=1, linestyle=":", alpha=0.5)

    ax.set_xlabel("Urgent Notification Miss Rate (%)\n[Lower is better]",
                  color="#AAAAAA", fontsize=9)
    ax.set_ylabel("Focus Minutes Protected (est.)\n[Higher is better]",
                  color="#AAAAAA", fontsize=9)
    ax.set_title("E1 — Policy Trade-off Frontier\nUrgent Safety vs. Focus Protection",
                 color="white", fontsize=10, fontweight="bold")
    ax.tick_params(colors="#AAAAAA", labelsize=8)
    ax.legend(fontsize=7.5, frameon=False, labelcolor="white", loc="lower right")

    # Right: Bar chart of focus minutes
    ax_bar.set_facecolor("#1A1D27")
    ax_bar.spines[:].set_color("#333344")

    names  = [p.replace("\n", "\n") for p in results]
    values = [v[0] for v in results.values()]
    colors = [POLICY_COLORS[p] for p in results]

    bars = ax_bar.barh(names, values, color=colors, alpha=0.85, edgecolor="#333344")
    for bar, val in zip(bars, values):
        ax_bar.text(val + 2, bar.get_y() + bar.get_height() / 2,
                    f"{val:.0f} min", va="center", color="white", fontsize=8)

    ax_bar.set_xlabel("Focus Minutes Protected (Simulated)",
                      color="#AAAAAA", fontsize=9)
    ax_bar.set_title("Focus Minutes Protected per Policy",
                     color="white", fontsize=10, fontweight="bold")
    ax_bar.tick_params(colors="#AAAAAA", labelsize=8)

    fig.suptitle(
        "E1 — Policy Comparison · Pratibimb vs. Baselines\n"
        "'Always Deliver', 'DND', 'Hold All', 'Random 50%', 'Twin Static', 'Twin Adaptive (Pratibimb)'",
        color="white", fontsize=11, fontweight="bold", y=1.01
    )
    fig.text(
        0.5, -0.03,
        "Simulated · 500 notifications on shifted true user.\n"
        "Urgent miss rate target: <2%. Minutes protected labeled as estimated (simulated, not measured).",
        ha="center", fontsize=7.5, color="#888888"
    )

    plt.tight_layout()
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=150, bbox_inches="tight",
                facecolor=fig.get_facecolor())
    plt.close()
    print(f"Saved E1 chart -> {output_path}")


if __name__ == "__main__":
    np.random.seed(99)
    notifications = generate_notifications(500, seed=42)
    urgent_total  = sum(1 for n in notifications if n["is_urgent"])
    print(f"Generated {len(notifications)} notifications | {urgent_total} urgent ({urgent_total/5:.1f}%)")

    results = {}
    print("\n--- Policy Evaluation ---")
    for policy, hold_fn in POLICIES.items():
        mins, miss_rate = evaluate_policy(notifications, hold_fn)
        results[policy] = (mins, miss_rate)
        label = policy.replace("\n", " ")
        print(f"  {label:35} | Protected: {mins:6.1f} min | Urgent miss: {miss_rate:.1%}")

    plot_e1(results, "eval/e1_policy_comparison.png")
    print("[OK] E1 chart complete.")
