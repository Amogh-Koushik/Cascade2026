# Model 2, WORTH: whether a notification matters to this user. It starts from a hand-set
# structural score and then moves only with explicit labels, first per app and then per sender.
# Opening speed and dwell time never enter here: they measure pull, not worth.

from dataclasses import dataclass
from typing import Any

from engine.contract import Category, NotificationFeatures
from engine.counts import Counts
from engine.priors import PriorWeights
from engine.stats import sigmoid


@dataclass(frozen=True)
class WorthEstimate:
    q: float
    # Effective sample size behind q; it sets how wide the sampled worth range is.
    n_q: float
    structural_score: float
    used_sender: bool


def worth_features(features: NotificationFeatures) -> dict[str, int]:
    # Each x_k is 0 or 1, as listed in CONTRACTS.md section 4.
    category = features.category
    flags = {
        "is_conversation": features.is_conversation,
        "cat_messaging": category == Category.MESSAGING,
        "cat_work_email": category == Category.WORK_EMAIL,
        "priority_conversation": features.priority_conversation,
        "group_chat": features.group_chat,
        "cat_shopping_promo": category == Category.SHOPPING_PROMO,
        "cat_social_nonconversation": category == Category.SOCIAL and not features.is_conversation,
        "cat_short_video": category == Category.SHORT_VIDEO,
        "bait_hit": features.bait_hit,
        "urgency_hit_in_conversation": features.urgency_hit and features.is_conversation,
        "same_sender_repeat": features.same_sender_recent_pings >= 1,
    }
    return {name: int(flag) for name, flag in flags.items()}


def structural_score(params: dict[str, Any], features: NotificationFeatures) -> float:
    worth_params = params["worth"]
    x = worth_features(features)
    total = worth_params["bias"] + sum(
        weight * x[name] for name, weight in worth_params["weights"].items()
    )
    return sigmoid(total)


def estimate_worth(
    params: dict[str, Any],
    weights: PriorWeights,
    counts: Counts,
    features: NotificationFeatures,
) -> WorthEstimate:
    s = structural_score(params, features)
    app = counts.app_tally(features.package_name)
    q_app = (weights.k_app * s + app.positives) / (weights.k_app + app.labels)

    sender = counts.sender_tally(features.sender_hash)
    if features.is_conversation and sender.labels > 0:
        q = (weights.k_sender * q_app + sender.positives) / (weights.k_sender + sender.labels)
        return WorthEstimate(q, weights.k_sender + sender.labels, s, used_sender=True)

    return WorthEstimate(q_app, weights.k_app + app.labels, s, used_sender=False)
