# Python mirror of contracts/kotlin/.../TwinEngine.kt. Field names are the snake_case JSON keys
# of the Kotlin properties, so a dict from sample_decisions.json or a test vector maps straight
# onto these classes. tests/test_engine_contract.py keeps the two sides in step.

from dataclasses import asdict, dataclass, fields
from enum import StrEnum
from typing import Any, Self


class Category(StrEnum):
    SHORT_VIDEO = "short_video"
    SOCIAL = "social"
    MESSAGING = "messaging"
    SHOPPING_PROMO = "shopping_promo"
    NEWS = "news"
    WORK_EMAIL = "work_email"
    OTHER = "other"

    @classmethod
    def from_key(cls, key: str | None) -> "Category":
        # Apps missing from the params category map, or with an unknown key, count as OTHER.
        try:
            return cls(key)
        except ValueError:
            return cls.OTHER


class PhoneState(StrEnum):
    FREE = "FREE"
    FOCUS_STEADY = "FOCUS_STEADY"
    FOCUS_RESTLESS = "FOCUS_RESTLESS"


class Autonomy(StrEnum):
    OBSERVE = "OBSERVE"
    SUGGEST = "SUGGEST"
    AUTO = "AUTO"


class Action(StrEnum):
    DELIVER = "DELIVER"
    HOLD = "HOLD"


class FatigueRisk(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class DecisionRule(StrEnum):
    OVERRIDE = "OVERRIDE"
    FREE_STATE = "FREE_STATE"
    OBSERVE_MODE = "OBSERVE_MODE"
    MIGHT_MATTER = "MIGHT_MATTER"
    HOLD_BETTER = "HOLD_BETTER"
    DELIVER_BETTER = "DELIVER_BETTER"
    EXPLORATION = "EXPLORATION"


class WorthLabelKind(StrEnum):
    WORTH_IT = "WORTH_IT"
    RELEASE_NOW = "RELEASE_NOW"
    ALWAYS_DELIVER = "ALWAYS_DELIVER"
    SHOULD_HAVE_COME_THROUGH = "SHOULD_HAVE_COME_THROUGH"
    NOT_WORTH_IT = "NOT_WORTH_IT"
    CORRECT_HOLD = "CORRECT_HOLD"

    @property
    def positive(self) -> bool:
        return self not in (WorthLabelKind.NOT_WORTH_IT, WorthLabelKind.CORRECT_HOLD)


def _strict_kwargs(cls: type, data: dict[str, Any]) -> dict[str, Any]:
    names = {field.name for field in fields(cls)}
    unknown = set(data) - names
    missing = names - set(data)
    if unknown or missing:
        raise ValueError(
            f"{cls.__name__}: unknown keys {sorted(unknown)}, missing keys {sorted(missing)}"
        )
    return dict(data)


@dataclass(frozen=True)
class NotificationFeatures:
    key: str
    posted_at_millis: int
    package_name: str
    category: Category
    android_category: str | None
    channel_importance: int
    is_ongoing: bool
    is_conversation: bool
    priority_conversation: bool
    group_chat: bool
    sender_hash: str | None
    sender_display_name: str | None
    same_sender_recent_pings: int
    bait_hit: bool
    urgency_hit: bool
    otp_pattern: bool

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> Self:
        kwargs = _strict_kwargs(cls, data)
        kwargs["category"] = Category.from_key(kwargs["category"])
        return cls(**kwargs)


@dataclass(frozen=True)
class TwinContext:
    state: PhoneState
    autonomy: Autonomy
    minutes_until_release: float
    focus_minutes_elapsed: float
    fatigue_risk: FatigueRisk
    exploration_draw: float
    always_deliver_senders: frozenset[str]
    always_deliver_packages: frozenset[str]
    demo_learning: bool

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> Self:
        kwargs = _strict_kwargs(cls, data)
        kwargs["state"] = PhoneState(kwargs["state"])
        kwargs["autonomy"] = Autonomy(kwargs["autonomy"])
        kwargs["fatigue_risk"] = FatigueRisk(kwargs["fatigue_risk"])
        kwargs["always_deliver_senders"] = frozenset(kwargs["always_deliver_senders"])
        kwargs["always_deliver_packages"] = frozenset(kwargs["always_deliver_packages"])
        return cls(**kwargs)


@dataclass(frozen=True)
class FutureCard:
    pull_mean: float
    pull_lo: float
    pull_hi: float
    mins_if_opened_lo: float
    mins_if_opened_hi: float
    l_p10: float
    l_p50: float
    l_p90: float
    l_mean: float
    h_p10: float
    h_p50: float
    h_p90: float
    h_mean: float
    q_mean: float
    q_lo: float
    q_hi: float
    q_upper80: float
    hold_better_per_hundred: int
    net_minutes_protected: float
    minutes_until_release: float
    low_confidence: bool
    fatigue_risk: FatigueRisk


@dataclass(frozen=True)
class Decision:
    action: Action
    rule: DecisionRule
    would_be_action: Action
    suggest_only: bool
    explored: bool
    propensity: float
    reason: str
    explanation: str
    card: FutureCard

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class PullOutcome:
    category: Category
    state: PhoneState
    opened: bool
    minutes_lost: float | None
    released_from_tray: bool


@dataclass(frozen=True)
class WorthLabel:
    package_name: str
    sender_hash: str | None
    label: WorthLabelKind


FeedbackEvent = PullOutcome | WorthLabel


def feedback_from_dict(data: dict[str, Any]) -> FeedbackEvent:
    # JSON events carry a "type" of pull_outcome or worth_label (CONTRACTS.md section 1).
    body = {key: value for key, value in data.items() if key != "type"}
    if data.get("type") == "pull_outcome":
        kwargs = _strict_kwargs(PullOutcome, body)
        kwargs["category"] = Category.from_key(kwargs["category"])
        kwargs["state"] = PhoneState(kwargs["state"])
        return PullOutcome(**kwargs)
    if data.get("type") == "worth_label":
        kwargs = _strict_kwargs(WorthLabel, body)
        kwargs["label"] = WorthLabelKind(kwargs["label"])
        return WorthLabel(**kwargs)
    raise ValueError(f"Unknown feedback event type: {data.get('type')!r}")
