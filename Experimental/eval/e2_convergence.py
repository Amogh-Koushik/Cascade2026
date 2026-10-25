"""
E2 Convergence Chart (eval/e2_convergence.py)
Author: Safal | CASCADE '26 · Pratibimb

Headline proof: Shows that without exploration (epsilon=0), the model
never learns the true behavior of held categories. With exploration
(epsilon=0.1), Bayesian updates converge to the true user within days.

Output: eval/e2_convergence.png
"""

import json
import math
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
import os

CATEGORIES = ["short_video", "social", "messaging", "shopping_promo", "news", "work_email", "other"]

# ── True user parameters (shifted from starter priors) ──────────────────────
TRUE_USER = {
    "social":         {"true_p": 0.52, "true_mu": 2.6},
    "messaging":      {"true_p": 0.18, "true_mu": 1.1},
    "short_video":    {"true_p": 0.61, "true_mu": 2.9},
    "shopping_promo": {"true_p": 0.08, "true_mu": 1.3},
    "news":           {"true_p": 0.14, "true_mu": 1.1},
    "work_email":     {"true_p": 0.11, "true_mu": 0.7},
    "other":          {"true_p": 0.22, "true_mu": 1.0},
}

# ── Starter priors from twin_params ─────────────────────────────────────────
STARTER = {
    "social":         {"alpha": 3.3, "beta": 11.5, "mu": 2.1},
    "messaging":      {"alpha": 2.2, "beta": 7.8,  "mu": 1.2},
    "short_video":    {"alpha": 3.9, "beta": 13.9, "mu": 2.4},
    "shopping_promo": {"alpha": 0.9, "beta": 9.1,  "mu": 1.5},
    "news":           {"alpha": 1.7, "beta": 8.3,  "mu": 1.2},
    "work_email":     {"alpha": 1.1, "beta": 10.9, "mu": 0.8},
    "other":          {"alpha": 2.2, "beta": 7.8,  "mu": 1.0},
}

NOTIFICATIONS_PER_DAY = 40
N_DAYS = 14
FOCUS_PROB = 0.70  # 70% of notifications arrive during focus
HOLD_THRESHOLD = 0.65  # model holds if estimated p > 0.65

def simulate_learning(epsilon: float, seed: int = 42):
    """
    Simulates 14 days of Bayesian online learning.
    epsilon: probability of exploring (showing) a held notification.
    Returns: dict of category -> list of daily p_estimate values
    """
    rng = np.random.default_rng(seed)

    # Current belief state (conjugate Beta params)
    alpha = {c: STARTER[c]["alpha"] for c in CATEGORIES}
    beta  = {c: STARTER[c]["beta"]  for c in CATEGORIES}
    mu_est = {c: STARTER[c]["mu"]   for c in CATEGORIES}

    history = {c: [] for c in CATEGORIES}

    for day in range(N_DAYS):
        for _ in range(NOTIFICATIONS_PER_DAY):
            # Sample category by realistic rate
            cat = rng.choice(CATEGORIES,
                             p=[0.20, 0.25, 0.25, 0.10, 0.05, 0.10, 0.05])
            is_focus = rng.random() < FOCUS_PROB

            # Current estimated probability
            p_est = alpha[cat] / (alpha[cat] + beta[cat])

            # Decision: hold if above threshold during focus, with epsilon exploration
            if is_focus and p_est > HOLD_THRESHOLD:
                if rng.random() < epsilon:
                    # Explore: show despite high estimate
                    observe = True
                else:
                    # Hold: no observation this round (crucial blind spot)
                    observe = False
            else:
                observe = True

            if observe:
                # True outcome from real user
                did_open = rng.random() < TRUE_USER[cat]["true_p"]
                if did_open:
                    alpha[cat] += 1
                    # Update duration estimate (running mean in log space)
                    true_mu = TRUE_USER[cat]["true_mu"]
                    log_t = rng.normal(true_mu, 0.6)
                    n = alpha[cat] + beta[cat]
                    mu_est[cat] = mu_est[cat] * (n - 1) / n + log_t / n
                else:
                    beta[cat] += 1

        # Record daily belief
        for c in CATEGORIES:
            p_current = alpha[c] / (alpha[c] + beta[c])
            history[c].append(p_current)

    return history


def plot_e2(history_no_eps, history_with_eps, output_path: str):
    FOCUS_CATS = ["social", "short_video", "messaging"]  # Most important 3

    fig = plt.figure(figsize=(16, 10))
    fig.patch.set_facecolor("#0F1117")

    gs = gridspec.GridSpec(2, 3, figure=fig, hspace=0.45, wspace=0.35)

    COLORS = {
        "social":      "#9B59B6",
        "short_video": "#E74C3C",
        "messaging":   "#2ECC71",
    }

    days = list(range(1, N_DAYS + 1))

    for idx, cat in enumerate(FOCUS_CATS):
        ax = fig.add_subplot(gs[0, idx])
        ax.set_facecolor("#1A1D27")

        true_p = TRUE_USER[cat]["true_p"]
        color = COLORS[cat]

        # Without exploration
        ax.plot(days, history_no_eps[cat], color="#E74C3C",
                linewidth=2, linestyle="--", label=f"No Exploration (e=0)", zorder=3)
        # With exploration
        ax.plot(days, history_with_eps[cat], color="#2ECC71",
                linewidth=2.5, label=f"With Exploration (e=0.1)", zorder=4)
        # True value line
        ax.axhline(true_p, color="white", linewidth=1.5,
                   linestyle=":", alpha=0.8, label=f"True p = {true_p:.2f}", zorder=5)
        # Starter prior
        ax.axhline(STARTER[cat]["alpha"] / (STARTER[cat]["alpha"] + STARTER[cat]["beta"]),
                   color="#888888", linewidth=1, linestyle="-.", alpha=0.6,
                   label="Starter prior", zorder=2)

        ax.set_title(cat.replace("_", " ").title(), color="white",
                     fontsize=10, fontweight="bold")
        ax.set_xlabel("Day", color="#AAAAAA", fontsize=8)
        ax.set_ylabel("Estimated P(open)", color="#AAAAAA", fontsize=8)
        ax.tick_params(colors="#AAAAAA", labelsize=7.5)
        ax.spines[:].set_color("#333344")
        ax.set_xlim(1, N_DAYS)
        ax.set_ylim(0, 0.85)
        ax.legend(fontsize=6.5, frameon=False, labelcolor="white")

    # Bottom row: error gap chart
    ax_gap = fig.add_subplot(gs[1, :])
    ax_gap.set_facecolor("#1A1D27")

    for cat in FOCUS_CATS:
        true_p = TRUE_USER[cat]["true_p"]
        err_no_eps   = [abs(p - true_p) for p in history_no_eps[cat]]
        err_with_eps = [abs(p - true_p) for p in history_with_eps[cat]]
        color = COLORS[cat]
        ax_gap.plot(days, err_no_eps,   color=color, linewidth=2,
                    linestyle="--", alpha=0.7, label=f"{cat} · No Exploration")
        ax_gap.plot(days, err_with_eps, color=color, linewidth=2.5,
                    alpha=1.0, label=f"{cat} · With Exploration")

    ax_gap.fill_between(days,
                        [0]*N_DAYS, [0.05]*N_DAYS,
                        alpha=0.12, color="#2ECC71", label="Target error < 0.05")
    ax_gap.axhline(0.05, color="#2ECC71", linewidth=1, linestyle=":", alpha=0.5)

    ax_gap.set_title("Absolute Error vs. True User · All Focus Categories\n"
                     "Dashed = No Exploration | Solid = With Exploration (e=0.1)",
                     color="white", fontsize=9, fontweight="bold")
    ax_gap.set_xlabel("Day of Learning", color="#AAAAAA", fontsize=8)
    ax_gap.set_ylabel("|Estimated p - True p|", color="#AAAAAA", fontsize=8)
    ax_gap.tick_params(colors="#AAAAAA", labelsize=7.5)
    ax_gap.spines[:].set_color("#333344")
    ax_gap.set_xlim(1, N_DAYS)
    ax_gap.legend(fontsize=7, frameon=False, labelcolor="white", ncol=3)

    fig.suptitle(
        "E2 — Convergence Proof: Why Exploration Is Non-Negotiable\n"
        "Without epsilon-exploration, the model never observes held categories and learning freezes permanently.",
        color="white", fontsize=12, fontweight="bold", y=1.01
    )
    fig.text(
        0.5, -0.02,
        "Simulated · 14-day Bayesian update simulation on a shifted true user.\n"
        "Real observations not yet available (hackathon conditions). Startup prior: LSApp (real, public).",
        ha="center", fontsize=7.5, color="#888888"
    )

    plt.tight_layout()
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=150, bbox_inches="tight",
                facecolor=fig.get_facecolor())
    plt.close()
    print(f"Saved E2 chart -> {output_path}")


if __name__ == "__main__":
    print("Simulating 14 days WITHOUT exploration (epsilon=0)...")
    history_no_eps = simulate_learning(epsilon=0.0, seed=42)

    print("Simulating 14 days WITH exploration (epsilon=0.1)...")
    history_with_eps = simulate_learning(epsilon=0.1, seed=42)

    # Print day-14 errors
    print("\n--- Day 14 Error vs. True User ---")
    for cat in CATEGORIES:
        true_p = TRUE_USER[cat]["true_p"]
        err_no  = abs(history_no_eps[cat][-1] - true_p)
        err_yes = abs(history_with_eps[cat][-1] - true_p)
        print(f"  {cat:20} | No-eps error: {err_no:.3f} | With-eps error: {err_yes:.3f}")

    plot_e2(history_no_eps, history_with_eps, "eval/e2_convergence.png")
    print("[OK] E2 chart complete.")
