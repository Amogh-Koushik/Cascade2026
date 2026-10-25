import itertools
from typing import Any

import pytest

from engine.contract import (
    Action,
    Autonomy,
    Category,
    DecisionRule,
    PhoneState,
    WorthLabelKind,
)
from engine.engine import TwinEngine
from tests.builders import (
    INSTAGRAM,
    MOM,
    WHATSAPP,
    label,
    make_context,
    make_features,
    mom_message,
)

SWIGGY = "in.swiggy.android"
FLIPKART = "com.flipkart.android"
SMS = "com.google.android.apps.messaging"


@pytest.mark.parametrize(
    ("features", "context", "reason"),
    [
        (
            make_features(),
            make_context(always_deliver_packages=frozenset({INSTAGRAM})),
            "you chose this app",
        ),
        (
            mom_message(),
            make_context(always_deliver_senders=frozenset({MOM})),
            "you chose this sender",
        ),
        (make_features(android_category="call"), make_context(), "calls, alarms and navigation"),
        (make_features(android_category="alarm"), make_context(), "calls, alarms and navigation"),
        (make_features(is_ongoing=True), make_context(), "ongoing activity"),
        (
            make_features(package_name="com.ubercab", category=Category.OTHER),
            make_context(),
            "rides, deliveries and maps",
        ),
        (
            make_features(package_name=SMS, category=Category.MESSAGING, otp_pattern=True),
            make_context(),
            "one-time password",
        ),
        (mom_message(same_sender_recent_pings=1), make_context(), "repeated ping"),
    ],
)
def test_never_hold_overrides_always_deliver(
    twin: TwinEngine, features: Any, context: Any, reason: str
) -> None:
    decision = twin.decide(features, context)

    assert decision.action == Action.DELIVER
    assert decision.rule == DecisionRule.OVERRIDE
    assert decision.reason == f"Always delivered: {reason}"


def test_the_first_matching_override_wins(twin: TwinEngine) -> None:
    features = make_features(android_category="call", is_ongoing=True)
    context = make_context(always_deliver_packages=frozenset({INSTAGRAM}))

    assert twin.decide(features, context).reason == "Always delivered: you chose this app"


def test_otp_bait_from_a_shopping_app_is_held(twin: TwinEngine) -> None:
    bait = make_features(package_name=FLIPKART, category=Category.SHOPPING_PROMO, otp_pattern=True)

    assert twin.decide(bait, make_context()).action == Action.HOLD


def test_three_promos_in_a_row_are_all_held(twin: TwinEngine) -> None:
    # An app re-sending promos never counts as a repeated ping: there is no person behind it.
    promos = [
        make_features(key=f"p{i}", package_name=SWIGGY, category=Category.SHOPPING_PROMO)
        for i in range(3)
    ]

    assert [twin.decide(p, make_context()).action for p in promos] == [Action.HOLD] * 3


def test_two_pings_from_the_same_person_are_delivered(twin: TwinEngine) -> None:
    second = twin.decide(mom_message(same_sender_recent_pings=1), make_context())

    assert second.action == Action.DELIVER
    assert second.explanation == "Delivered: Mom messaged twice in 3 minutes."


def test_repeated_pings_from_a_group_member_without_a_sender_do_not_override(
    twin: TwinEngine,
) -> None:
    features = mom_message(sender_hash=None, same_sender_recent_pings=3)

    assert twin.decide(features, make_context()).rule != DecisionRule.OVERRIDE


def test_free_state_delivers_everything(twin: TwinEngine) -> None:
    context = make_context(state=PhoneState.FREE, minutes_until_release=0.0)

    decision = twin.decide(make_features(), context)

    assert (decision.action, decision.rule) == (Action.DELIVER, DecisionRule.FREE_STATE)


def test_a_like_in_steady_focus_is_held(twin: TwinEngine) -> None:
    decision = twin.decide(make_features(), make_context())

    assert (decision.action, decision.rule) == (Action.HOLD, DecisionRule.HOLD_BETTER)
    assert decision.card.hold_better_per_hundred >= 70
    assert decision.explanation.startswith("Held: 34 min into steady focus.")


def test_an_urgent_message_from_mom_might_matter_and_is_delivered(twin: TwinEngine) -> None:
    decision = twin.decide(mom_message(urgency_hit=True), make_context())

    assert (decision.action, decision.rule) == (Action.DELIVER, DecisionRule.MIGHT_MATTER)


def test_cheap_notifications_are_delivered_when_holding_costs_more(
    params: dict[str, Any],
) -> None:
    # A heavy delay penalty makes holding expensive, so showing wins.
    params["policy"]["w"] = 20.0
    decision = TwinEngine(params).decide(make_features(), make_context())

    assert (decision.action, decision.rule) == (Action.DELIVER, DecisionRule.DELIVER_BETTER)
    assert decision.card.hold_better_per_hundred < 70


def test_observe_mode_delivers_but_records_the_would_be_hold(twin: TwinEngine) -> None:
    decision = twin.decide(make_features(), make_context(autonomy=Autonomy.OBSERVE))

    assert decision.action == Action.DELIVER
    assert decision.rule == DecisionRule.OBSERVE_MODE
    assert decision.would_be_action == Action.HOLD
    assert "would have been held" in decision.explanation


def test_suggest_mode_recommends_and_leaves_the_choice_to_the_user(twin: TwinEngine) -> None:
    decision = twin.decide(make_features(), make_context(autonomy=Autonomy.SUGGEST))

    assert decision.action == Action.HOLD
    assert decision.suggest_only
    assert decision.explanation.startswith("Suggest holding:")


@pytest.mark.parametrize(
    ("draw", "action", "rule", "explored", "propensity"),
    [
        (0.05, Action.DELIVER, DecisionRule.EXPLORATION, True, 0.1),
        (0.5, Action.HOLD, DecisionRule.HOLD_BETTER, False, 0.9),
    ],
)
def test_exploration_flips_a_confident_hold_only_when_the_draw_is_below_epsilon(
    twin: TwinEngine,
    draw: float,
    action: Action,
    rule: DecisionRule,
    explored: bool,
    propensity: float,
) -> None:
    decision = twin.decide(make_features(), make_context(exploration_draw=draw))

    assert (decision.action, decision.rule, decision.explored) == (action, rule, explored)
    assert decision.would_be_action == Action.HOLD
    assert decision.propensity == pytest.approx(propensity)


@pytest.mark.parametrize("autonomy", [Autonomy.OBSERVE, Autonomy.SUGGEST])
def test_exploration_happens_only_in_auto_mode(twin: TwinEngine, autonomy: Autonomy) -> None:
    decision = twin.decide(make_features(), make_context(autonomy=autonomy, exploration_draw=0.0))

    assert not decision.explored
    assert decision.propensity == 1.0


def test_something_that_might_matter_is_never_used_for_exploration(twin: TwinEngine) -> None:
    decision = twin.decide(mom_message(urgency_hit=True), make_context(exploration_draw=0.0))

    assert decision.rule == DecisionRule.MIGHT_MATTER
    assert decision.propensity == 1.0


def test_same_inputs_always_give_the_same_decision(twin: TwinEngine) -> None:
    assert twin.decide(make_features(), make_context()) == twin.decide(
        make_features(), make_context()
    )


def test_switching_persona_parameters_keeps_what_was_learned(
    twin: TwinEngine, params: dict[str, Any]
) -> None:
    twin.apply_feedback(label(WorthLabelKind.WORTH_IT, WHATSAPP, MOM))
    learned = twin.export_counts()

    params["pull"]["category"]["social"]["r_c"] = 0.5
    twin.set_params(params)

    assert twin.export_counts() == learned


def test_demo_learning_still_holds_the_beat_one_like(twin: TwinEngine) -> None:
    decision = twin.decide(make_features(), make_context(demo_learning=True))

    assert decision.action == Action.HOLD
    assert decision.card.q_upper80 < 0.5


def test_four_correct_hold_taps_visibly_narrow_the_hold_cost_range(twin: TwinEngine) -> None:
    context = make_context(demo_learning=True)
    before = twin.decide(make_features(), context).card
    for _ in range(4):
        twin.apply_feedback(label(WorthLabelKind.CORRECT_HOLD))
    after = twin.decide(make_features(), context).card

    assert (after.h_p90 - after.h_p10) < 0.8 * (before.h_p90 - before.h_p10)
    assert after.hold_better_per_hundred >= before.hold_better_per_hundred


def test_reset_counts_forgets_everything_learned(twin: TwinEngine) -> None:
    fresh = twin.decide(make_features(), make_context())
    for _ in range(5):
        twin.apply_feedback(label(WorthLabelKind.WORTH_IT))

    twin.reset_counts()

    assert twin.decide(make_features(), make_context()) == fresh


GRID = list(
    itertools.product(
        list(Category),
        [PhoneState.FOCUS_STEADY, PhoneState.FOCUS_RESTLESS],
        [False, True],
        [False, True],
        [0.0, 16.0, 45.0],
    )
)


@pytest.mark.parametrize(("category", "state", "conversation", "bait", "release"), GRID)
def test_every_card_is_internally_consistent(
    twin: TwinEngine,
    category: Category,
    state: PhoneState,
    conversation: bool,
    bait: bool,
    release: float,
) -> None:
    features = make_features(
        category=category,
        is_conversation=conversation,
        sender_hash="s_x" if conversation else None,
        bait_hit=bait,
    )
    decision = twin.decide(features, make_context(state=state, minutes_until_release=release))
    card = decision.card

    assert 0.0 < card.pull_lo <= card.pull_mean <= card.pull_hi < 1.0
    assert card.mins_if_opened_lo <= card.mins_if_opened_hi
    assert card.l_p10 <= card.l_p50 <= card.l_p90
    assert card.h_p10 <= card.h_p50 <= card.h_p90
    assert card.q_lo <= card.q_mean <= card.q_hi
    assert card.q_mean <= card.q_upper80
    assert 0 <= card.hold_better_per_hundred <= 100
    assert card.net_minutes_protected == pytest.approx(card.l_mean - card.h_mean)
    assert card.minutes_until_release == release
    assert len(decision.explanation.split()) < 25
    if decision.action == Action.HOLD:
        assert card.q_upper80 < 0.5
