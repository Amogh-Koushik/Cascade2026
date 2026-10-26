# Writes contracts/demo/demo_report.json: the Report tab's simulated view, a week of the
# student_coder persona run through the reference engine and aggregated exactly as the phone
# aggregates a real week (ReportHistory in the app). Where the numbers come from:
#   arrivals      persona notification_rates_by_hour, Poisson per category per hour
#   focus         persona focus_schedule; 10-minute blocks restless with the simulator's 20%
#   opens, mins   sim.simulator.ShiftedTrueUser (Safal's shifted true user)
#   importance    persona latent_importance_rates, nudged by urgency and repeats as in the simulator
#   hold/deliver  engine.TwinEngine on the persona params, learning from each outcome
#   fatigue       the phone's three rules (FatigueRules.kt), read every 15 seconds
# Two behaviours the simulator does not model are filled in the simplest way: the app within a
# category is picked evenly from the params app map, and each opened notification counts as
# one switch to that app. The simulated user answers every worth prompt truthfully and marks
# held items that mattered as "This was important". The file is labelled simulated.
#
# Run: uv run python -m engine.make_demo_report

import json
import math
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

from contracts.params import PARAMS_DIR
from engine.contract import (
    Action,
    Autonomy,
    Category,
    DecisionRule,
    FatigueRisk,
    NotificationFeatures,
    PhoneState,
    PullOutcome,
    TwinContext,
    WorthLabel,
    WorthLabelKind,
)
from engine.engine import TwinEngine
from sim.simulator import ShiftedTrueUser

ROOT = Path(__file__).resolve().parent.parent
OUTPUT_PATH = ROOT / "contracts" / "demo" / "demo_report.json"
PERSONAS_PATH = ROOT / "sim" / "personas.json"
PERSONA = "student_coder"

SEED = 2026
DAYS = 7
HOURS = 24
READING_SECONDS = 15
BLOCK_MIN = 10
RESTLESS_SHARE = 0.2

# The phone's fatigue rules, kept equal to FatigueRules.kt.
LONG_STRETCH_MIN = 90.0
RECENT_WINDOW_MIN = 30.0
SWITCH_RISE = 1.5
MIN_RECENT_SWITCHES = 4
LATE_START_HOUR = 23
LATE_END_HOUR = 5

# Feature rates from sim.simulator.simulate_event_stream.
GROUP_SHARE = 0.4
REPEAT_SHARE = 0.15
BAIT_SHARE = 0.35
URGENCY_SHARE = 0.12

APP_LABELS = {
    "com.instagram.android": "Instagram",
    "com.google.android.youtube": "YouTube",
    "com.zhiliaoapp.musically": "TikTok",
    "com.facebook.katana": "Facebook",
    "com.twitter.android": "X",
    "com.reddit.frontpage": "Reddit",
    "com.linkedin.android": "LinkedIn",
    "com.snapchat.android": "Snapchat",
    "com.whatsapp": "WhatsApp",
    "org.telegram.messenger": "Telegram",
    "com.discord": "Discord",
    "com.google.android.apps.messaging": "Messages",
    "com.samsung.android.messaging": "Samsung Messages",
    "com.amazon.mShop.android.shopping": "Amazon",
    "in.amazon.mShop.android.shopping": "Amazon",
    "com.flipkart.android": "Flipkart",
    "in.swiggy.android": "Swiggy",
    "com.application.zomato": "Zomato",
    "com.myntra.android": "Myntra",
    "com.google.android.apps.magazines": "Google News",
    "com.inshorts": "Inshorts",
    "com.nis.app": "Inshorts",
    "com.google.android.gm": "Gmail",
    "com.microsoft.office.outlook": "Outlook",
    "com.slack": "Slack",
    "com.Slack": "Slack",
}

# Category "other" has no apps in the params map; its notifications are grouped under one key.
OTHER_APPS_KEY = "other"
OTHER_APPS_LABEL = "Other apps"


@dataclass(frozen=True)
class FocusMinute:
    hour: int
    session_minute: float
    session_length: float
    restless: bool


def focus_minutes(
    schedule: list[dict[str, Any]], rng: np.random.Generator
) -> dict[int, FocusMinute]:
    """One day's focus, keyed by minute of the day."""
    minutes: dict[int, FocusMinute] = {}
    for session in schedule:
        hours, mins = (int(part) for part in session["start"].split(":"))
        start = hours * 60 + mins
        length = int(session["duration_min"])
        blocks = math.ceil(length / BLOCK_MIN)
        restless_blocks = [rng.random() < RESTLESS_SHARE for _ in range(blocks)]
        for offset in range(length):
            minute_of_day = (start + offset) % (HOURS * 60)
            minutes[minute_of_day] = FocusMinute(
                minute_of_day // 60,
                float(offset),
                float(length),
                restless_blocks[offset // BLOCK_MIN],
            )
    return minutes


def fatigue_risk(
    session_minute: float, recent_switches: int, session_switches: int, hour: int
) -> FatigueRisk:
    signals = 0
    if session_minute >= LONG_STRETCH_MIN:
        signals += 1
    usual = session_switches * RECENT_WINDOW_MIN / session_minute if session_minute else 0.0
    if (
        session_minute > RECENT_WINDOW_MIN
        and recent_switches >= MIN_RECENT_SWITCHES
        and recent_switches >= SWITCH_RISE * usual
    ):
        signals += 1
    if hour >= LATE_START_HOUR or hour < LATE_END_HOUR:
        signals += 1
    return [FatigueRisk.LOW, FatigueRisk.MEDIUM, FatigueRisk.HIGH][min(signals, 2)]


def hold_cap(category: Category, group_chat: bool, policy: dict[str, Any]) -> float:
    caps = policy["cap_by_kind"]
    if category == Category.MESSAGING:
        return float(caps["group"] if group_chat else caps["direct"])
    if category == Category.WORK_EMAIL:
        return float(caps["email"])
    if category == Category.SHOPPING_PROMO:
        return float(caps["promo"])
    if category in (Category.SOCIAL, Category.SHORT_VIDEO):
        return float(caps["social"])
    return float(policy["cap_min"])


class WeekReport:
    """The phone's ReportHistory aggregates, built up one reading or notification at a time."""

    def __init__(self) -> None:
        self.steady = [0] * HOURS
        self.restless = [0] * HOURS
        self.fatigue = [[0, 0, 0] for _ in range(HOURS)]
        self.opens_app: Counter[str] = Counter()
        self.minutes_app: defaultdict[str, float] = defaultdict(float)
        self.opens_category: Counter[str] = Counter()
        self.minutes_category: defaultdict[str, float] = defaultdict(float)
        self.switches: Counter[str] = Counter()
        self.held: Counter[str] = Counter()
        self.delivered: Counter[str] = Counter()
        self.worth: defaultdict[str, list[int]] = defaultdict(lambda: [0, 0])

    def add_worth(self, package: str, positive: bool) -> None:
        self.worth[package][0] += int(positive)
        self.worth[package][1] += 1

    def to_history(self) -> dict[str, Any]:
        return {
            "since": 0,
            "steady": self.steady,
            "restless": self.restless,
            "fatigue": self.fatigue,
            "opens_app": dict(sorted(self.opens_app.items())),
            "minutes_app": {k: round(v, 1) for k, v in sorted(self.minutes_app.items())},
            "opens_category": dict(sorted(self.opens_category.items())),
            "minutes_category": {k: round(v, 1) for k, v in sorted(self.minutes_category.items())},
            "switches": dict(sorted(self.switches.items())),
            "held": dict(sorted(self.held.items())),
            "delivered": dict(sorted(self.delivered.items())),
            "worth": {k: v for k, v in sorted(self.worth.items())},
        }


class WeekSimulation:
    def __init__(self, seed: int = SEED) -> None:
        personas = json.loads(PERSONAS_PATH.read_text(encoding="utf-8"))["personas"]
        self.persona = personas[PERSONA]
        self.user = ShiftedTrueUser(self.persona, seed=seed)
        self.twin = TwinEngine.from_file(PARAMS_DIR / f"twin_params_{PERSONA}.json")
        self.policy = self.twin.params["policy"]
        # The params map lists some apps under two package names; each app is picked once.
        self.apps_by_category: dict[str, list[str]] = defaultdict(list)
        named: set[str] = set()
        for package, category in sorted(self.twin.params["app_category_map"].items()):
            if APP_LABELS[package] not in named:
                named.add(APP_LABELS[package])
                self.apps_by_category[category].append(package)
        self.rng = np.random.default_rng(seed)
        self.report = WeekReport()
        self.key = 0

    def run(self) -> WeekReport:
        for day in range(DAYS):
            self.run_day(day)
        return self.report

    def run_day(self, day: int) -> None:
        focus = focus_minutes(self.persona["focus_schedule"], self.rng)
        arrivals = self.arrivals_in_focus(focus)
        switch_minutes: list[int] = []
        for minute_of_day in sorted(focus):
            moment = focus[minute_of_day]
            if moment.session_minute == 0:
                switch_minutes = []
            for category in arrivals.get(minute_of_day, []):
                if self.notify(day, minute_of_day, category, moment):
                    switch_minutes.append(minute_of_day)
            self.read_focus(moment, minute_of_day, switch_minutes)

    def arrivals_in_focus(self, focus: dict[int, FocusMinute]) -> dict[int, list[str]]:
        arrivals: dict[int, list[str]] = defaultdict(list)
        for hour in range(HOURS):
            for category, rates in self.persona["notification_rates_by_hour"].items():
                for _ in range(self.rng.poisson(rates[hour])):
                    minute_of_day = hour * 60 + int(self.rng.integers(60))
                    if minute_of_day in focus:
                        arrivals[minute_of_day].append(category)
        return arrivals

    def read_focus(self, moment: FocusMinute, minute_of_day: int, switches: list[int]) -> None:
        recent_from = minute_of_day - RECENT_WINDOW_MIN
        for reading in range(60 // READING_SECONDS):
            session_minute = moment.session_minute + reading * READING_SECONDS / 60
            recent = sum(1 for at in switches if at > recent_from)
            risk = fatigue_risk(session_minute, recent, len(switches), moment.hour)
            self.report.fatigue[moment.hour][list(FatigueRisk).index(risk)] += 1
            if moment.restless:
                self.report.restless[moment.hour] += READING_SECONDS
            else:
                self.report.steady[moment.hour] += READING_SECONDS

    def notify(self, day: int, minute_of_day: int, category_key: str, moment: FocusMinute) -> bool:
        """Decides one notification and plays out what the simulated user does; True if opened."""
        category = Category.from_key(category_key)
        apps = self.apps_by_category.get(category_key, [])
        package = apps[int(self.rng.integers(len(apps)))] if apps else OTHER_APPS_KEY
        state = PhoneState.FOCUS_RESTLESS if moment.restless else PhoneState.FOCUS_STEADY

        conversation = category in (Category.MESSAGING, Category.WORK_EMAIL)
        group_chat = conversation and self.rng.random() < GROUP_SHARE
        repeat = conversation and not group_chat and self.rng.random() < REPEAT_SHARE
        bait = category in (Category.SHOPPING_PROMO, Category.SOCIAL, Category.SHORT_VIDEO) and (
            self.rng.random() < BAIT_SHARE
        )
        urgent = conversation and self.rng.random() < URGENCY_SHARE
        important = self.rng.random() < self.importance(category_key, urgent or repeat, bait)

        self.key += 1
        features = NotificationFeatures(
            key=f"week_{self.key:05d}",
            posted_at_millis=(day * HOURS * 60 + minute_of_day) * 60_000,
            package_name=package,
            category=category,
            android_category=None,
            channel_importance=3,
            is_ongoing=False,
            is_conversation=conversation,
            priority_conversation=False,
            group_chat=group_chat,
            sender_hash=None,
            sender_display_name=None,
            same_sender_recent_pings=int(self.policy["repeat_ping"]["count"]) if repeat else 0,
            bait_hit=bait,
            urgency_hit=urgent,
            otp_pattern=False,
        )
        remaining = moment.session_length - moment.session_minute
        context = TwinContext(
            state=state,
            autonomy=Autonomy.AUTO,
            minutes_until_release=min(remaining, hold_cap(category, group_chat, self.policy)),
            focus_minutes_elapsed=moment.session_minute,
            fatigue_risk=FatigueRisk.LOW,
            exploration_draw=float(self.rng.random()),
            always_deliver_senders=frozenset(),
            always_deliver_packages=frozenset(),
            demo_learning=False,
        )
        decision = self.twin.decide(features, context)

        if decision.action == Action.HOLD:
            self.report.held[package] += 1
            if important:
                self.label(package, WorthLabelKind.SHOULD_HAVE_COME_THROUGH)
            return False

        self.report.delivered[package] += 1
        if decision.rule == DecisionRule.MIGHT_MATTER:
            answer = WorthLabelKind.WORTH_IT if important else WorthLabelKind.NOT_WORTH_IT
            self.label(package, answer)
        opened, minutes = self.open_outcome(category_key, state)
        outcome = PullOutcome(category, state, opened, minutes if opened else None, False)
        self.twin.apply_feedback(outcome)
        if opened:
            self.report.opens_app[package] += 1
            self.report.minutes_app[package] += minutes
            self.report.opens_category[category_key] += 1
            self.report.minutes_category[category_key] += minutes
            self.report.switches[package] += 1
        return opened

    def importance(self, category_key: str, nudged_up: bool, bait: bool) -> float:
        share = float(self.user.latent_importance_rates.get(category_key, 0.1))
        if nudged_up:
            share = min(0.95, share + 0.40)
        if bait:
            share = max(0.01, share - 0.20)
        return share

    def open_outcome(self, category_key: str, state: PhoneState) -> tuple[bool, float]:
        multipliers = self.user.state_multipliers.get(state.value, {})
        pull = self.user.true_m_g * self.user.true_r_c[category_key] * multipliers.get("r_s", 1.0)
        p_open = float(np.clip(pull, 0.01, 0.95))
        if self.rng.random() >= p_open:
            return False, 0.0
        duration = self.user.true_dur[category_key]
        mu = duration["mu"] + multipliers.get("delta_s", 0.0)
        log_minutes = self.rng.normal(mu, duration["sigma"])
        return True, float(np.clip(math.exp(log_minutes), 0.5, 60.0))

    def label(self, package: str, kind: WorthLabelKind) -> None:
        self.report.add_worth(package, kind.positive)
        self.twin.apply_feedback(WorthLabel(package, None, kind))


def build_document(seed: int = SEED) -> dict[str, Any]:
    history = WeekSimulation(seed).run().to_history()
    used = set(history["held"]) | set(history["delivered"]) | set(history["switches"])
    labels = {package: APP_LABELS.get(package, OTHER_APPS_LABEL) for package in sorted(used)}
    return {
        "version": "1.0",
        "label": "simulated",
        "description": f"{DAYS} simulated days of the {PERSONA} persona, decided by engine.py",
        "persona": PERSONA,
        "seed": seed,
        "app_labels": labels,
        "history": history,
    }


def main() -> None:
    document = build_document()
    OUTPUT_PATH.write_text(json.dumps(document, indent=1) + "\n", encoding="utf-8")
    history = document["history"]
    focus_min = (sum(history["steady"]) + sum(history["restless"])) / 60
    held = sum(history["held"].values())
    delivered = sum(history["delivered"].values())
    opened = sum(history["opens_app"].values())
    print(
        f"Wrote {OUTPUT_PATH}: {focus_min:.0f} focus min, "
        f"{held} held, {delivered} delivered, {opened} opened"
    )


if __name__ == "__main__":
    main()
