# The small numeric helpers every model uses, written out in plain math so the Kotlin port can
# copy them line for line (CONTRACTS.md section 2). No numpy: its defaults are not part of the
# contract, these definitions are.

import math

PROB_EPS = 1e-6


def sigmoid(x: float) -> float:
    return 1.0 / (1.0 + math.exp(-x))


def clamp_prob(p: float) -> float:
    return min(max(p, PROB_EPS), 1.0 - PROB_EPS)


def logit(p: float) -> float:
    p = clamp_prob(p)
    return math.log(p / (1.0 - p))


def clip(x: float, lo: float, hi: float) -> float:
    return min(max(x, lo), hi)


def percentile(values: list[float], p: float) -> float:
    # Linear interpolation between closest ranks, the same rule as numpy's default.
    ordered = sorted(values)
    position = (len(ordered) - 1) * p
    lo = math.floor(position)
    hi = min(lo + 1, len(ordered) - 1)
    return ordered[lo] + (position - lo) * (ordered[hi] - ordered[lo])


def mean(values: list[float]) -> float:
    return sum(values) / len(values)
