# The Python reference engine: the same five operations as the TwinEngine Kotlin interface.
# TwinEngine.kt is ported from this file and must reproduce its numbers on the test vectors.
#
# Usage:
#   twin = TwinEngine.from_file(Path("contracts/params/twin_params_example.json"))
#   decision = twin.decide(features, context)
#   twin.apply_feedback(PullOutcome(...))

import json
from pathlib import Path
from typing import Any, Self

from contracts.params import load_params, validate_params
from engine.contract import Decision, FeedbackEvent, NotificationFeatures, TwinContext
from engine.counts import Counts, apply_event, counts_from_json, counts_to_json
from engine.overrides import check_overrides
from engine.policy import decide
from engine.priors import prior_weights
from engine.pull import estimate_pull
from engine.sampling import build_card, sample_futures
from engine.worth import estimate_worth


class TwinEngine:
    def __init__(self, params: dict[str, Any]) -> None:
        self._params: dict[str, Any] = {}
        self._counts = Counts()
        self.set_params(params)

    @classmethod
    def from_file(cls, path: Path) -> Self:
        return cls(load_params(path))

    @property
    def params(self) -> dict[str, Any]:
        return self._params

    def set_params(self, params: dict[str, Any]) -> None:
        # Learned counts are kept: a persona switch changes the priors, not what was learned.
        validate_params(params)
        self._params = params

    def load_params(self, params_json: str) -> None:
        self.set_params(json.loads(params_json))

    def decide(self, features: NotificationFeatures, context: TwinContext) -> Decision:
        params = self._params
        policy = params["policy"]
        category = features.category
        weights = prior_weights(params, context.demo_learning)

        pull = estimate_pull(params, weights, self._counts, category, context.state)
        worth = estimate_worth(params, weights, self._counts, features)
        futures = sample_futures(
            pull, worth, params["draw_bank"], policy, context.minutes_until_release
        )
        card = build_card(
            pull, worth, futures, policy, context.minutes_until_release, context.fatigue_risk
        )
        return decide(
            category,
            card,
            futures,
            check_overrides(features, context, params),
            context,
            policy,
            worth.used_sender,
        )

    def apply_feedback(self, event: FeedbackEvent) -> None:
        apply_event(self._counts, event, self._params["policy"])

    def export_counts(self) -> str:
        return counts_to_json(self._counts)

    def import_counts(self, counts_json: str) -> None:
        self._counts = counts_from_json(counts_json)

    def reset_counts(self) -> None:
        self._counts = Counts()
