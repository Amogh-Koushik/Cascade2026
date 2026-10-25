import json
from pathlib import Path

from engine.contract import Action, feedback_from_dict
from engine.engine import TwinEngine
from engine.make_learning_replay import EVENT_COUNT, OUTPUT_PATH, build_document
from tests.builders import make_context, make_features

DOCUMENT = json.loads(Path(OUTPUT_PATH).read_text(encoding="utf-8"))


def test_replay_file_is_up_to_date_and_labelled_simulated() -> None:
    assert json.loads(json.dumps(build_document())) == DOCUMENT
    assert DOCUMENT["label"] == "simulated"
    assert len(DOCUMENT["events"]) == EVENT_COUNT == 30


def test_every_replay_event_is_a_valid_pull_outcome() -> None:
    for event in DOCUMENT["events"]:
        outcome = feedback_from_dict(event)
        assert (outcome.minutes_lost is not None) == outcome.opened


def test_replay_visibly_narrows_the_if_shown_range(twin: TwinEngine) -> None:
    context = make_context(demo_learning=True)
    before = twin.decide(make_features(), context).card
    for event in DOCUMENT["events"]:
        twin.apply_feedback(feedback_from_dict(event))
    after = twin.decide(make_features(), context)

    narrower = (after.card.l_p90 - after.card.l_p10) / (before.l_p90 - before.l_p10)
    assert narrower < 0.5
    assert (after.card.mins_if_opened_hi - after.card.mins_if_opened_lo) < (
        before.mins_if_opened_hi - before.mins_if_opened_lo
    )
    assert after.action == Action.HOLD
