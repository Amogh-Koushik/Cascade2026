# What the twin has learned about this user: open and minutes tallies for the pull model and
# label tallies for the worth model, updated by the table in CONTRACTS.md section 7. The JSON
# form is what the phone stores, so counts survive restarts.

import json
import math
from dataclasses import dataclass, field
from typing import Any

from engine.contract import FeedbackEvent, PhoneState, PullOutcome, WorthLabel


class FeedbackError(ValueError):
    pass


@dataclass
class PullTally:
    shown: int = 0
    opens: int = 0
    n_min: int = 0
    sum_x: float = 0.0
    sum_x2: float = 0.0

    def record(self, opened: bool, x: float | None) -> None:
        self.shown += 1
        if opened and x is not None:
            self.opens += 1
            self.n_min += 1
            self.sum_x += x
            self.sum_x2 += x * x


@dataclass
class WorthTally:
    labels: int = 0
    positives: int = 0

    def record(self, positive: bool) -> None:
        self.labels += 1
        if positive:
            self.positives += 1


@dataclass
class Counts:
    global_pull: PullTally = field(default_factory=PullTally)
    category: dict[str, PullTally] = field(default_factory=dict)
    cell: dict[str, PullTally] = field(default_factory=dict)
    app: dict[str, WorthTally] = field(default_factory=dict)
    sender: dict[str, WorthTally] = field(default_factory=dict)

    # Reads never create entries, so looking at an unseen cell leaves the counts unchanged.
    def category_tally(self, category: str) -> PullTally:
        return self.category.get(category, PullTally())

    def cell_tally(self, category: str, state: str) -> PullTally:
        return self.cell.get(cell_key(category, state), PullTally())

    def app_tally(self, package: str) -> WorthTally:
        return self.app.get(package, WorthTally())

    def sender_tally(self, sender_hash: str | None) -> WorthTally:
        if sender_hash is None:
            return WorthTally()
        return self.sender.get(sender_hash, WorthTally())


def cell_key(category: str, state: str) -> str:
    return f"{category}|{state}"


def log_minutes(minutes: float, policy: dict[str, Any]) -> float:
    capped = min(max(minutes, policy["minutes_floor_min"]), policy["minutes_lost_cap_min"])
    return math.log(capped)


def apply_event(counts: Counts, event: FeedbackEvent, policy: dict[str, Any]) -> None:
    if isinstance(event, PullOutcome):
        _apply_pull(counts, event, policy)
    elif isinstance(event, WorthLabel):
        _apply_worth(counts, event)
    else:
        raise FeedbackError(f"Unknown feedback event: {event!r}")


def _apply_pull(counts: Counts, event: PullOutcome, policy: dict[str, Any]) -> None:
    if event.opened and event.minutes_lost is None:
        raise FeedbackError("An opened notification needs minutes_lost")
    if not event.opened and event.minutes_lost is not None:
        raise FeedbackError("minutes_lost is only measured for opened notifications")

    x = log_minutes(event.minutes_lost, policy) if event.minutes_lost is not None else None
    category = str(event.category)

    # A tray release happens at a break, so it teaches only the FREE cell.
    if event.released_from_tray:
        key = cell_key(category, PhoneState.FREE)
        counts.cell.setdefault(key, PullTally()).record(event.opened, x)
        return

    counts.global_pull.record(event.opened, x)
    counts.category.setdefault(category, PullTally()).record(event.opened, x)
    counts.cell.setdefault(cell_key(category, event.state), PullTally()).record(event.opened, x)


def _apply_worth(counts: Counts, event: WorthLabel) -> None:
    positive = event.label.positive
    counts.app.setdefault(event.package_name, WorthTally()).record(positive)
    if event.sender_hash is not None:
        counts.sender.setdefault(event.sender_hash, WorthTally()).record(positive)


def counts_to_json(counts: Counts) -> str:
    def pull_dict(tally: PullTally, with_squares: bool) -> dict[str, Any]:
        out: dict[str, Any] = {
            "shown": tally.shown,
            "opens": tally.opens,
            "n_min": tally.n_min,
            "sum_x": tally.sum_x,
        }
        if with_squares:
            out["sum_x2"] = tally.sum_x2
        return out

    document = {
        "pull": {
            "global": {"shown": counts.global_pull.shown, "opens": counts.global_pull.opens},
            "category": {k: pull_dict(t, False) for k, t in sorted(counts.category.items())},
            "cell": {k: pull_dict(t, True) for k, t in sorted(counts.cell.items())},
        },
        "worth": {
            "app": {k: vars(t) for k, t in sorted(counts.app.items())},
            "sender": {k: vars(t) for k, t in sorted(counts.sender.items())},
        },
    }
    return json.dumps(document)


def counts_from_json(text: str) -> Counts:
    # Missing entries mean zero, so an empty object is a valid fresh twin.
    document = json.loads(text)
    pull = document.get("pull", {})
    worth = document.get("worth", {})
    global_pull = pull.get("global", {})
    return Counts(
        global_pull=PullTally(shown=global_pull.get("shown", 0), opens=global_pull.get("opens", 0)),
        category={k: PullTally(**v) for k, v in pull.get("category", {}).items()},
        cell={k: PullTally(**v) for k, v in pull.get("cell", {}).items()},
        app={k: WorthTally(**v) for k, v in worth.get("app", {}).items()},
        sender={k: WorthTally(**v) for k, v in worth.get("sender", {}).items()},
    )
