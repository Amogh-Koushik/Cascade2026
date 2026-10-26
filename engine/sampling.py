# Turns the two models into the Future Card. Each of the 400 shared draws is one sampled future:
# a pull, a minutes-if-opened and a worth. From them come L (focus minutes lost if shown now),
# H (the delay cost of holding, if it matters) and pi, the share of futures where holding wins.

import math
from dataclasses import dataclass
from typing import Any

from engine.contract import FatigueRisk, FutureCard
from engine.pull import PullEstimate
from engine.stats import clamp_prob, logit, mean, percentile, sigmoid
from engine.worth import WorthEstimate


@dataclass(frozen=True)
class SampledFutures:
    pull: list[float]
    minutes_if_opened: list[float]
    worth: list[float]
    loss: list[float]
    hold_cost: list[float]
    holding_wins: int

    @property
    def pi(self) -> float:
        return self.holding_wins / len(self.loss)

    @property
    def hold_better_per_hundred(self) -> int:
        # floor(100 * pi + 0.5) in whole numbers, so Python and Kotlin cannot round differently.
        n = len(self.loss)
        return (200 * self.holding_wins + n) // (2 * n)


def logit_spread(p: float, n: float) -> float:
    # Approximate standard deviation of logit(p) for a Beta estimate with n effective samples.
    p = clamp_prob(p)
    return 1.0 / math.sqrt(n * p * (1.0 - p))


def upper80(q: float, n_q: float, z: float) -> float:
    return sigmoid(logit(q) + z * logit_spread(q, n_q))


def sample_futures(
    pull: PullEstimate,
    worth: WorthEstimate,
    draws: list[list[float]],
    policy: dict[str, Any],
    minutes_until_release: float,
) -> SampledFutures:
    s_p = logit_spread(pull.p_cs, pull.n_p)
    s_q = logit_spread(worth.q, worth.n_q)
    logit_p = logit(pull.p_cs)
    logit_q = logit(worth.q)
    c_glance, w, lam = policy["c_glance"], policy["w"], policy["lambda"]
    # Getting back into the task after an open costs focus on top of the time in the app.
    resume = policy["resume_min"]

    p_i, m_i, q_i, loss, hold_cost = [], [], [], [], []
    for z1, z2, z3 in draws:
        p = sigmoid(logit_p + s_p * z1)
        m = math.exp(pull.mu_cs + pull.sigma_pred * z2)
        q = sigmoid(logit_q + s_q * z3)
        p_i.append(p)
        m_i.append(m)
        q_i.append(q)
        loss.append(p * (m + resume) + (1.0 - p) * c_glance)
        hold_cost.append(q * w * minutes_until_release)

    holding_wins = sum(1 for shown, held in zip(loss, hold_cost, strict=True) if shown > lam * held)
    return SampledFutures(p_i, m_i, q_i, loss, hold_cost, holding_wins)


def build_card(
    pull: PullEstimate,
    worth: WorthEstimate,
    futures: SampledFutures,
    policy: dict[str, Any],
    minutes_until_release: float,
    fatigue_risk: FatigueRisk,
) -> FutureCard:
    pull_lo, pull_hi = percentile(futures.pull, 0.1), percentile(futures.pull, 0.9)
    q_lo, q_hi = percentile(futures.worth, 0.1), percentile(futures.worth, 0.9)
    width = policy["low_confidence_width"]
    l_mean, h_mean = mean(futures.loss), mean(futures.hold_cost)

    return FutureCard(
        pull_mean=pull.p_cs,
        pull_lo=pull_lo,
        pull_hi=pull_hi,
        mins_if_opened_lo=percentile(futures.minutes_if_opened, 0.1),
        mins_if_opened_hi=percentile(futures.minutes_if_opened, 0.9),
        l_p10=percentile(futures.loss, 0.1),
        l_p50=percentile(futures.loss, 0.5),
        l_p90=percentile(futures.loss, 0.9),
        l_mean=l_mean,
        h_p10=percentile(futures.hold_cost, 0.1),
        h_p50=percentile(futures.hold_cost, 0.5),
        h_p90=percentile(futures.hold_cost, 0.9),
        h_mean=h_mean,
        q_mean=worth.q,
        q_lo=q_lo,
        q_hi=q_hi,
        q_upper80=upper80(worth.q, worth.n_q, policy["upper80_z"]),
        hold_better_per_hundred=futures.hold_better_per_hundred,
        net_minutes_protected=l_mean - h_mean,
        minutes_until_release=minutes_until_release,
        low_confidence=(pull_hi - pull_lo) > width or (q_hi - q_lo) > width,
        fatigue_risk=fatigue_risk,
    )
