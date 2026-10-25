# Model 1, PULL: how likely this user is to open a notification within the open window, and how
# many focus minutes it costs if they do. Both are pooled global -> category -> category x state,
# so a cell with no data borrows from its category, and a category from the whole phone.

import math
from dataclasses import dataclass
from typing import Any

from engine.counts import Counts
from engine.priors import PriorWeights
from engine.stats import clip

MIN_OPEN_PRIOR = 0.01
MAX_OPEN_PRIOR = 0.95


@dataclass(frozen=True)
class PullEstimate:
    p_cs: float
    # Effective sample size behind p_cs; it sets how wide the sampled pull range is.
    n_p: float
    mu_cs: float
    sigma_pred: float


def estimate_pull(
    params: dict[str, Any],
    weights: PriorWeights,
    counts: Counts,
    category: str,
    state: str,
) -> PullEstimate:
    category_params = params["pull"]["category"][category]
    state_params = params["pull"]["state"][state]
    category_tally = counts.category_tally(category)
    cell_tally = counts.cell_tally(category, state)

    m_g = params["pull"]["global"]["m_g"]
    p_g = (weights.global_n * m_g + counts.global_pull.opens) / (
        weights.global_n + counts.global_pull.shown
    )

    a_c = clip(p_g * category_params["r_c"], MIN_OPEN_PRIOR, MAX_OPEN_PRIOR)
    p_c = (weights.category * a_c + category_tally.opens) / (
        weights.category + category_tally.shown
    )

    # A category with more history earns its cells a stronger prior.
    a_cs = clip(p_c * state_params["r_s"], MIN_OPEN_PRIOR, MAX_OPEN_PRIOR)
    n_cs0 = weights.cell_base + weights.cell_pool * min(category_tally.shown, weights.cell_pool_cap)
    p_cs = (n_cs0 * a_cs + cell_tally.opens) / (n_cs0 + cell_tally.shown)
    n_p = n_cs0 + cell_tally.shown

    mu_c = (weights.mu_category * category_params["mu_c0"] + category_tally.sum_x) / (
        weights.mu_category + category_tally.n_min
    )
    n_cs = cell_tally.n_min
    mu_cs = (weights.mu_cell * (mu_c + state_params["delta_s"]) + cell_tally.sum_x) / (
        weights.mu_cell + n_cs
    )

    # Sum of squared deviations from mu_cs, rebuilt from the stored sums. Floored at zero
    # because float rounding can push it a hair below.
    squared_deviation = max(
        0.0, cell_tally.sum_x2 - 2.0 * mu_cs * cell_tally.sum_x + n_cs * mu_cs * mu_cs
    )
    variance = (weights.sigma * category_params["sigma_c"] ** 2 + squared_deviation) / (
        weights.sigma + n_cs
    )
    sigma_pred = math.sqrt(variance) * math.sqrt(1.0 + 1.0 / (weights.mu_cell + n_cs))

    return PullEstimate(p_cs=p_cs, n_p=n_p, mu_cs=mu_cs, sigma_pred=sigma_pred)
