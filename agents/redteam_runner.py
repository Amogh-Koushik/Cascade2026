# Fires every red-team attack at engine.py and scores each notification against its label:
# an important one that is held is an urgent miss, an unimportant one that is delivered is
# bait that got through. Every miss is listed by name; the attackers are imagined ones.
#
# Each attack gets a fresh twin in steady Auto focus, 16 minutes before the break, with
# exploration switched off (u = 0.5), so the results are deterministic.
#
# Run: uv run python -m agents.redteam_runner [attack files...] [--out eval/redteam_report.md]

import argparse
import json
import sys
from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from agents.feature_builder import RawNotification, build_features
from agents.redteam_schema import ATTACK_TYPES, AttackSetError, validate_attack_set
from contracts.params import PARAMS_DIR, ParamsError, load_params
from engine.contract import Action, Autonomy, FatigueRisk, PhoneState, TwinContext
from engine.engine import TwinEngine

REPO_ROOT = Path(__file__).resolve().parent.parent
HANDWRITTEN_PATH = REPO_ROOT / "agents" / "redteam_handwritten.json"
SYNTHETIC_PATH = REPO_ROOT / "data" / "generated" / "redteam_set.json"
DEFAULT_PARAMS = PARAMS_DIR / "twin_params_example.json"

FOCUS_CONTEXT = TwinContext(
    state=PhoneState.FOCUS_STEADY,
    autonomy=Autonomy.AUTO,
    minutes_until_release=16.0,
    focus_minutes_elapsed=34.0,
    fatigue_risk=FatigueRisk.LOW,
    exploration_draw=0.5,
    always_deliver_senders=frozenset(),
    always_deliver_packages=frozenset(),
    demo_learning=False,
)


@dataclass(frozen=True)
class NotificationResult:
    attack_id: str
    attack_type: str
    source: str
    index: int
    title: str
    text: str
    important: bool
    action: Action
    rule: str
    reason: str

    @property
    def urgent_missed(self) -> bool:
        return self.important and self.action == Action.HOLD

    @property
    def bait_delivered(self) -> bool:
        return not self.important and self.action == Action.DELIVER


@dataclass(frozen=True)
class TypeSummary:
    attack_type: str
    notifications: int
    important: int
    urgent_missed: int
    unimportant: int
    bait_delivered: int


def run_attack(attack: dict[str, Any], params: dict[str, Any]) -> list[NotificationResult]:
    twin = TwinEngine(params)
    earlier: list[RawNotification] = []
    results = []
    for index, raw in enumerate(attack["notifications"]):
        notification = RawNotification.from_dict(raw)
        features = build_features(notification, earlier, params, key=f"{attack['id']}#{index}")
        decision = twin.decide(features, FOCUS_CONTEXT)
        results.append(
            NotificationResult(
                attack_id=attack["id"],
                attack_type=attack["attack_type"],
                source=attack["source"],
                index=index,
                title=notification.title,
                text=notification.text,
                important=raw["important"],
                action=decision.action,
                rule=str(decision.rule),
                reason=decision.reason,
            )
        )
        earlier.append(notification)
    return results


def run_attacks(
    attacks: Iterable[dict[str, Any]], params: dict[str, Any]
) -> list[NotificationResult]:
    return [result for attack in attacks for result in run_attack(attack, params)]


def summarise(results: list[NotificationResult]) -> list[TypeSummary]:
    summaries = []
    for attack_type in ATTACK_TYPES:
        rows = [r for r in results if r.attack_type == attack_type]
        summaries.append(
            TypeSummary(
                attack_type=attack_type,
                notifications=len(rows),
                important=sum(r.important for r in rows),
                urgent_missed=sum(r.urgent_missed for r in rows),
                unimportant=sum(not r.important for r in rows),
                bait_delivered=sum(r.bait_delivered for r in rows),
            )
        )
    return summaries


def _share(part: int, whole: int) -> str:
    return f"{part}/{whole} ({100 * part / whole:.0f}%)" if whole else "-"


def render_report(results: list[NotificationResult], params_name: str) -> str:
    summaries = summarise(results)
    total_important = sum(s.important for s in summaries)
    total_unimportant = sum(s.unimportant for s in summaries)
    lines = [
        "# Red-team results",
        "",
        f"Engine: engine.py on `{params_name}`. Attacks are imagined; labels are the team's.",
        f"Attacks: {len({r.attack_id for r in results})}, notifications: {len(results)}.",
        "",
        "| Attack type | Notifications | Urgent missed | Bait delivered |",
        "|---|---|---|---|",
    ]
    for s in summaries:
        lines.append(
            f"| {s.attack_type} | {s.notifications} | {_share(s.urgent_missed, s.important)} "
            f"| {_share(s.bait_delivered, s.unimportant)} |"
        )
    lines.append(
        f"| **all** | {len(results)} "
        f"| {_share(sum(s.urgent_missed for s in summaries), total_important)} "
        f"| {_share(sum(s.bait_delivered for s in summaries), total_unimportant)} |"
    )

    misses = [r for r in results if r.urgent_missed or r.bait_delivered]
    lines += ["", f"## Misses ({len(misses)})", ""]
    if not misses:
        lines.append("None.")
    else:
        lines += ["| Attack | Notification | Should be | Got | Rule |", "|---|---|---|---|---|"]
        for r in misses:
            should = "delivered" if r.important else "held"
            note = f"{r.title}: {r.text}".replace("|", "/")
            lines.append(
                f"| {r.attack_id} | {note} | {should} | {r.action.value.lower()} | {r.rule} |"
            )
    return "\n".join(lines) + "\n"


def load_attacks(paths: list[Path]) -> list[dict[str, Any]]:
    attacks: list[dict[str, Any]] = []
    for path in paths:
        attacks += validate_attack_set(json.loads(path.read_text(encoding="utf-8")))
    # Ids must be unique across files too, so every miss in the report points at one attack.
    validate_attack_set({"attacks": attacks})
    return attacks


def main() -> int:
    parser = argparse.ArgumentParser(description="Score red-team attacks against engine.py.")
    parser.add_argument("files", nargs="*", type=Path, help="attack files (default: all known)")
    parser.add_argument("--params", type=Path, default=DEFAULT_PARAMS)
    parser.add_argument("--out", type=Path, help="also write the report to this file")
    args = parser.parse_args()

    files = args.files or [p for p in (HANDWRITTEN_PATH, SYNTHETIC_PATH) if p.exists()]
    try:
        params = load_params(args.params)
        attacks = load_attacks(files)
    except (AttackSetError, ParamsError, json.JSONDecodeError) as error:
        print(error)
        return 1

    report = render_report(run_attacks(attacks, params), args.params.name)
    print(report)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(report, encoding="utf-8")
        print(f"Wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
