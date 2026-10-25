# The decision rule (CONTRACTS.md section 6): overrides, then the free state, then the
# recommendation from the card, then exploration, then the autonomy level. One function, so
# every branch is easy to test and to port.

from dataclasses import dataclass
from typing import Any

from engine import explain
from engine.contract import (
    Action,
    Autonomy,
    Category,
    Decision,
    DecisionRule,
    FutureCard,
    PhoneState,
    TwinContext,
)
from engine.overrides import Override
from engine.sampling import SampledFutures


@dataclass(frozen=True)
class Recommendation:
    action: Action
    rule: DecisionRule
    reason: str
    explanation: str


def recommend(
    category: Category,
    card: FutureCard,
    futures: SampledFutures,
    context: TwinContext,
    policy: dict[str, Any],
    used_sender_history: bool,
) -> Recommendation:
    # Deliver when unsure: anything that plausibly matters goes through before costs are compared.
    if card.q_upper80 >= policy["might_matter_upper80"]:
        return Recommendation(
            Action.DELIVER,
            DecisionRule.MIGHT_MATTER,
            "Delivered: might matter",
            explain.might_matter(used_sender_history),
        )
    if futures.pi >= policy["pi_star"]:
        return Recommendation(
            Action.HOLD,
            DecisionRule.HOLD_BETTER,
            "Held: costs more to show than to hold",
            explain.held(category, card, context),
        )
    return Recommendation(
        Action.DELIVER,
        DecisionRule.DELIVER_BETTER,
        "Delivered: cheap to show",
        explain.cheap_to_show(card),
    )


def decide(
    category: Category,
    card: FutureCard,
    futures: SampledFutures,
    override: Override | None,
    context: TwinContext,
    policy: dict[str, Any],
    used_sender_history: bool,
) -> Decision:
    if override is not None:
        return _plain(
            Action.DELIVER, DecisionRule.OVERRIDE, override.reason, override.explanation, card
        )
    if context.state == PhoneState.FREE:
        return _plain(
            Action.DELIVER,
            DecisionRule.FREE_STATE,
            "Delivered: no focus session",
            explain.free_state(),
            card,
        )

    rec = recommend(category, card, futures, context, policy, used_sender_history)

    if context.autonomy == Autonomy.OBSERVE:
        return Decision(
            action=Action.DELIVER,
            rule=DecisionRule.OBSERVE_MODE,
            would_be_action=rec.action,
            suggest_only=False,
            explored=False,
            propensity=1.0,
            reason="Observe mode",
            explanation=explain.observe_mode(rec.action),
            card=card,
        )
    if context.autonomy == Autonomy.SUGGEST:
        return Decision(
            action=rec.action,
            rule=rec.rule,
            would_be_action=rec.action,
            suggest_only=True,
            explored=False,
            propensity=1.0,
            reason=rec.reason,
            explanation=explain.as_suggestion(rec.explanation, rec.action),
            card=card,
        )
    return _auto(rec, card, context, policy)


def _auto(
    rec: Recommendation, card: FutureCard, context: TwinContext, policy: dict[str, Any]
) -> Decision:
    epsilon = policy["epsilon"]
    # Only confident holds are explored, so an item that might matter is never the experiment.
    eligible = rec.action == Action.HOLD and card.q_upper80 < policy["explore_upper80"]

    if eligible and context.exploration_draw < epsilon:
        return Decision(
            action=Action.DELIVER,
            rule=DecisionRule.EXPLORATION,
            would_be_action=rec.action,
            suggest_only=False,
            explored=True,
            propensity=epsilon,
            reason="Delivered: exploration",
            explanation=explain.explored(),
            card=card,
        )
    return Decision(
        action=rec.action,
        rule=rec.rule,
        would_be_action=rec.action,
        suggest_only=False,
        explored=False,
        propensity=1.0 - epsilon if eligible else 1.0,
        reason=rec.reason,
        explanation=rec.explanation,
        card=card,
    )


def _plain(
    action: Action, rule: DecisionRule, reason: str, explanation: str, card: FutureCard
) -> Decision:
    return Decision(
        action=action,
        rule=rule,
        would_be_action=action,
        suggest_only=False,
        explored=False,
        propensity=1.0,
        reason=reason,
        explanation=explanation,
        card=card,
    )
