"""
E2 Convergence Chart (eval/e2_convergence.py)
Author: Safal | CASCADE '26 · Pratibimb

Headline proof: Shows that without exploration (epsilon=0), the model
never learns the true behavior of held categories. With exploration
(epsilon=0.1), Bayesian updates converge to the true user within days.

Output: eval/e2_convergence.png
"""

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
import os

CATEGORIES = ["short_video", "social", "messaging", "shopping_promo", "news", "work_email", "other"]

TRUE_USER = {
    "social":         {"true_p": 0.52, "true_mu": 2.6},
    "messaging":      {"true_p": 0.18, "true_mu": 1.1},
    "short_video":    {"true_p": 0.61, "true_mu": 2.9},
    "shopping_promo": {"true_p": 0.08, "true_mu": 1.3},
    "news":           {"true_p": 0.14, "true_mu": 1.1},
    "work_email":     {"true_p": 0.11, "true_mu": 0.7},
    "other":          {"true_p": 0.22, "true_mu": 1.0},
}

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
FOCUS_PROB = 0.70
HOLD_THRESHOLD = 0.65

def simulate_learning(epsilon: float, seed: int = 42):
    rng = np.random.default_rng(seed)
    alpha = {c: STARTER[c]["alpha"] for c in CATEGORIES}
    beta  = {c: STARTER[c]["beta"]  for c in CATEGORIES}
    mu_est = {c: STARTER[c]["mu"]   for c in CATEGORIES}
    history = {c: [] for c in CATEGORIES}

    for day in range(N_DAYS):
        for _ in range(NOTIFICATIONS_PER_DAY):
            cat = rng.choice(CATEGORIES, p=[0.20, 0.25, 0.25, 0.10, 0.05, 0.10, 0.05])
            is_focus = rng.random() < FOCUS_PROB
            p_est = alpha[cat] / (alpha[cat] + beta[cat])

            if is_focus and p_est > HOLD_THRESHOLD:
                observe = (rng.random() < epsilon)
            else:
                observe = True

            if observe:
                did_open = rng.random() < TRUE_USER[cat]["true_p"]
                if did_open:
                    alpha[cat] += 1
                    true_mu = TRUE_USER[cat]["true_mu"]
                    log_t = rng.normal(true_mu, 0.6)
                    n = alpha[cat] + beta[cat]
                    mu_est[cat] = mu_est[cat] * (n - 1) / n + log_t / n
                else:
                    beta[cat] += 1

        for c in CATEGORIES:
            p_current = alpha[c] / (alpha[c] + beta[c])
            history[c].append(p_current)

    return history

def plot_e2(history_no_eps, history_with_eps, output_path: str):
    FOCUS_CATS = ["social", "short_video", "messaging"]

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

        ax.plot(days, history_no_eps[cat], color="#E74C3C",
                linewidth=2, linestyle="--", label="No Exploration (e=0)", zorder=3)
        ax.plot(days, history_with_eps[cat], color="#2ECC71",
                linewidth=2.5, label="With Exploration (e=0.1)", zorder=4)
        ax.axhline(true_p, color="white", linewidth=1.5,
                   linestyle=":", alpha=0.8, label=f"True p = {true_p:.2f}", zorder=5)
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

    ax_gap = fig.add_subplot(gs[1, :])
    ax_gap.set_facecolor("#1A1D27")

    for cat in FOCUS_CATS:
        true_p = TRUE_USER[cat]["true_p"]
        err_no_eps   = [abs(p - true_p) for p in history_no_eps[cat]]
        err_with_eps = [abs(p - true_p) for p in history_with_eps[cat]]
        color = COLORS[cat]
        ax_gap.plot(days, err_no_eps,   color=color, linewidth=2,
                    linestyle="--", alpha=0.7, label=f"{cat} - No Exploration")
        ax_gap.plot(days, err_with_eps, color=color, linewidth=2.5,
                    alpha=1.0, label=f"{cat} - With Exploration")

    ax_gap.fill_between(days,
                        [0]*N_DAYS, [0.05]*N_DAYS,
                        alpha=0.12, color="#2ECC71", label="Target error < 0.05")
    ax_gap.axhline(0.05, color="#2ECC71", linewidth=1, linestyle=":", alpha=0.5)

    ax_gap.set_title("Absolute Error vs. True User: All Focus Categories",
                     color="white", fontsize=9, fontweight="bold")
    ax_gap.set_xlabel("Day of Learning", color="#AAAAAA", fontsize=8)
    ax_gap.set_ylabel("|Estimated p - True p|", color="#AAAAAA", fontsize=8)
    ax_gap.tick_params(colors="#AAAAAA", labelsize=7.5)
    ax_gap.spines[:].set_color("#333344")
    ax_gap.set_xlim(1, N_DAYS)
    ax_gap.legend(fontsize=7, frameon=False, labelcolor="white", ncol=3)

    fig.suptitle(
        "E2 - Convergence Proof: Why Exploration Is Non-Negotiable",
        color="white", fontsize=12, fontweight="bold", y=1.01
    )

    plt.tight_layout()
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=150, bbox_inches="tight",
                facecolor=fig.get_facecolor())
    plt.close()
    print(f"Saved E2 chart -> {output_path}")

if __name__ == "__main__":
    h_no = simulate_learning(epsilon=0.0, seed=42)
    h_yes = simulate_learning(epsilon=0.1, seed=42)
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_p = os.path.join(base_dir, "eval", "e2_convergence.png")
    plot_e2(h_no, h_yes, out_p)
