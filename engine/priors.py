# The prior weights in force for one decision. Normally they come from pull.prior_weights and
# worth.k_app / k_sender; with demo_learning on, any key present in params.demo_learning wins,
# so a few taps visibly move the ranges during the demo.

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class PriorWeights:
    global_n: float
    category: float
    cell_base: float
    cell_pool: float
    cell_pool_cap: float
    mu_category: float
    mu_cell: float
    sigma: float
    k_app: float
    k_sender: float


def prior_weights(params: dict[str, Any], demo_learning: bool) -> PriorWeights:
    pull_weights = dict(params["pull"]["prior_weights"])
    k_app = params["worth"]["k_app"]
    k_sender = params["worth"]["k_sender"]

    if demo_learning:
        demo = params["demo_learning"]
        pull_weights.update(demo["prior_weights"])
        k_app = demo["k_app"]
        k_sender = demo["k_sender"]

    return PriorWeights(
        global_n=params["pull"]["global"]["n_g"],
        category=pull_weights["category"],
        cell_base=pull_weights["cell_base"],
        cell_pool=pull_weights["cell_pool"],
        cell_pool_cap=pull_weights["cell_pool_cap"],
        mu_category=pull_weights["mu_category"],
        mu_cell=pull_weights["mu_cell"],
        sigma=pull_weights["sigma"],
        k_app=k_app,
        k_sender=k_sender,
    )
