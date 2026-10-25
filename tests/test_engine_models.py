import json
import math
import random
from typing import Any

import numpy as np
import pytest

from engine.contract import Category, PhoneState, PullOutcome, WorthLabelKind
from engine.counts import Counts, FeedbackError, apply_event, counts_from_json, counts_to_json
from engine.engine import TwinEngine
from engine.lexicon import lexicon_hits
from engine.priors import prior_weights
from engine.pull import estimate_pull
from engine.sampling import SampledFutures
from engine.stats import clamp_prob, logit, percentile, sigmoid
from engine.worth import estimate_worth, structural_score, worth_features
from tests.builders import (
    INSTAGRAM,
    MOM,
    WHATSAPP,
    ignored,
    label,
    make_context,
    make_features,
    mom_message,
    opened,
)


def test_percentile_matches_numpy_linear_interpolation() -> None:
    rng = random.Random(7)
    for size in (1, 2, 5, 400):
        values = [rng.uniform(-5, 5) for _ in range(size)]
        for p in (0.0, 0.1, 0.5, 0.9, 1.0):
            assert percentile(values, p) == pytest.approx(float(np.percentile(values, p * 100)))


def test_logit_undoes_sigmoid_and_survives_zero_and_one() -> None:
    assert sigmoid(logit(0.3)) == pytest.approx(0.3)
    assert math.isfinite(logit(0.0))
    assert math.isfinite(logit(1.0))
    assert clamp_prob(2.0) < 1.0


def test_hold_better_per_hundred_rounds_halves_up() -> None:
    # 2 wins out of 400 is exactly 0.5 per hundred, which must become 1, not 0.
    futures = SampledFutures([], [], [], [0.0] * 400, [0.0] * 400, holding_wins=2)

    assert futures.hold_better_per_hundred == 1


def pull_for(
    params: dict[str, Any],
    counts: Counts,
    category: str = "social",
    state: str = "FOCUS_STEADY",
    demo: bool = False,
) -> Any:
    return estimate_pull(params, prior_weights(params, demo), counts, category, state)


@pytest.mark.parametrize(
    ("state", "expected"),
    [
        ("FREE", 0.22 * 1.6),
        ("FOCUS_STEADY", 0.22 * 1.6 * 0.8),
        ("FOCUS_RESTLESS", 0.22 * 1.6 * 1.3),
    ],
)
def test_fresh_pull_is_global_rate_times_category_and_state(
    params: dict[str, Any], state: str, expected: float
) -> None:
    assert pull_for(params, Counts(), state=state).p_cs == pytest.approx(expected)


def test_category_prior_is_capped_below_one(params: dict[str, Any]) -> None:
    params["pull"]["category"]["social"]["r_c"] = 50.0

    p = pull_for(params, Counts(), state="FREE").p_cs

    assert p == pytest.approx(0.95)


def test_opens_raise_pull_and_ignores_lower_it(params: dict[str, Any]) -> None:
    fresh = pull_for(params, Counts()).p_cs
    opens, ignores = Counts(), Counts()
    for _ in range(10):
        apply_event(opens, opened(5.0), params["policy"])
        apply_event(ignores, ignored(), params["policy"])

    assert pull_for(params, opens).p_cs > fresh > pull_for(params, ignores).p_cs


def test_minutes_estimate_moves_toward_what_was_observed(params: dict[str, Any]) -> None:
    counts = Counts()
    for _ in range(20):
        apply_event(counts, opened(1.0), params["policy"])

    # mu_c0 for social is 2.1 (about 8 minutes); twenty 1-minute opens pull it toward ln 1 = 0.
    assert pull_for(params, counts).mu_cs < 1.0


def test_starter_counts_for_less_than_half_after_thirty_real_events(
    params: dict[str, Any],
) -> None:
    counts = Counts()
    for index in range(30):
        event = opened(3.0) if index % 3 == 0 else ignored()
        apply_event(counts, event, params["policy"])

    estimate = pull_for(params, counts)
    starter_weight = estimate.n_p - 30

    assert starter_weight / estimate.n_p < 0.5


def test_tray_release_teaches_only_the_free_cell(params: dict[str, Any]) -> None:
    counts = Counts()
    apply_event(counts, opened(4.0, from_tray=True), params["policy"])

    assert counts.global_pull.shown == 0
    assert counts.category_tally("social").shown == 0
    assert counts.cell_tally("social", "FOCUS_STEADY").shown == 0
    assert counts.cell_tally("social", "FREE").opens == 1


@pytest.mark.parametrize(
    "event",
    [
        PullOutcome(Category.SOCIAL, PhoneState.FOCUS_STEADY, True, None, False),
        PullOutcome(Category.SOCIAL, PhoneState.FOCUS_STEADY, False, 3.0, False),
    ],
)
def test_inconsistent_pull_outcomes_are_rejected(
    params: dict[str, Any], event: PullOutcome
) -> None:
    with pytest.raises(FeedbackError):
        apply_event(Counts(), event, params["policy"])


def test_zero_minute_opens_do_not_break_the_log(params: dict[str, Any]) -> None:
    counts = Counts()
    apply_event(counts, opened(0.0), params["policy"])

    assert math.isfinite(counts.cell_tally("social", "FOCUS_STEADY").sum_x)


def test_worth_features_follow_the_contract_table() -> None:
    x = worth_features(mom_message(urgency_hit=True, same_sender_recent_pings=1))

    assert x["is_conversation"] == x["cat_messaging"] == 1
    assert x["urgency_hit_in_conversation"] == x["same_sender_repeat"] == 1
    assert x["cat_social_nonconversation"] == x["bait_hit"] == 0


def test_instagram_like_scores_far_below_a_message_from_mom(params: dict[str, Any]) -> None:
    like = structural_score(params, make_features())
    mom = structural_score(params, mom_message(urgency_hit=True))

    assert like < 0.05 < 0.9 < mom


def test_opening_fast_never_changes_worth(params: dict[str, Any]) -> None:
    weights = prior_weights(params, False)
    counts = Counts()
    before = estimate_worth(params, weights, counts, make_features()).q
    for _ in range(10):
        apply_event(counts, opened(0.2), params["policy"])

    assert estimate_worth(params, weights, counts, make_features()).q == before


def test_worth_labels_move_q_toward_the_label(params: dict[str, Any]) -> None:
    weights = prior_weights(params, False)
    up, down = Counts(), Counts()
    for _ in range(5):
        apply_event(up, label(WorthLabelKind.WORTH_IT), params["policy"])
        apply_event(down, label(WorthLabelKind.NOT_WORTH_IT), params["policy"])
    fresh = estimate_worth(params, weights, Counts(), make_features()).q

    up_q = estimate_worth(params, weights, up, make_features()).q
    down_q = estimate_worth(params, weights, down, make_features()).q

    assert up_q > fresh > down_q


def test_sender_labels_apply_to_that_sender_only(params: dict[str, Any]) -> None:
    weights = prior_weights(params, False)
    counts = Counts()
    for _ in range(3):
        apply_event(counts, label(WorthLabelKind.NOT_WORTH_IT, WHATSAPP, MOM), params["policy"])

    mom = estimate_worth(params, weights, counts, mom_message())
    other = estimate_worth(params, weights, counts, mom_message(sender_hash="s_other"))

    assert mom.used_sender and not other.used_sender
    assert mom.q < other.q


def test_counts_survive_a_json_round_trip(params: dict[str, Any]) -> None:
    twin = TwinEngine(params)
    for event in (opened(4.0), ignored(), label(WorthLabelKind.WORTH_IT, WHATSAPP, MOM)):
        twin.apply_feedback(event)
    before = twin.decide(make_features(), make_context())

    restored = TwinEngine(params)
    restored.import_counts(twin.export_counts())

    assert restored.export_counts() == twin.export_counts()
    assert restored.decide(make_features(), make_context()) == before


def test_an_empty_counts_document_is_a_fresh_twin(params: dict[str, Any]) -> None:
    twin = TwinEngine(params)
    twin.import_counts("{}")

    assert counts_from_json(twin.export_counts()) == Counts()


def test_exported_counts_use_the_contract_layout(params: dict[str, Any]) -> None:
    counts = Counts()
    apply_event(counts, opened(4.0), params["policy"])
    apply_event(counts, label(WorthLabelKind.WORTH_IT, INSTAGRAM, None), params["policy"])

    document = json.loads(counts_to_json(counts))

    assert set(document["pull"]["category"]["social"]) == {"shown", "opens", "n_min", "sum_x"}
    assert "sum_x2" in document["pull"]["cell"]["social|FOCUS_STEADY"]
    assert document["worth"]["app"][INSTAGRAM] == {"labels": 1, "positives": 1}
    assert document["worth"]["sender"] == {}


@pytest.mark.parametrize(
    ("title", "text", "expected"),
    [
        ("Mom", "jaldi call kar", (False, True, False)),
        ("rohan_k", "Rohan LIKED YOUR reel", (True, False, False)),
        ("HDFCBK", "482913 is your OTP", (False, False, True)),
        ("Your OTP", "Use 4829 now", (False, False, True)),
        ("Bank", "Your OTP is on its way", (False, False, False)),
        ("Bank", "OTP ref 123456789", (False, False, False)),
        ("Sale", "Flat 60% off, don't miss it", (True, False, False)),
    ],
)
def test_lexicon_hits_follow_the_contract_rules(
    params: dict[str, Any], title: str, text: str, expected: tuple[bool, bool, bool]
) -> None:
    hits = lexicon_hits(title, text, params["lexicons"])

    assert (hits.bait, hits.urgency, hits.otp) == expected
