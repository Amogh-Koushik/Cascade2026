# Plain-language explanations from fixed templates, never an LLM. Every sentence is built from
# the numbers on the card, so what the user reads always matches what the twin computed.

import math

from engine.contract import Action, Category, FutureCard, PhoneState, TwinContext

CATEGORY_LABELS = {
    Category.SHORT_VIDEO: "Short videos",
    Category.SOCIAL: "Social updates",
    Category.MESSAGING: "Messages like this",
    Category.SHOPPING_PROMO: "Promos",
    Category.NEWS: "News alerts",
    Category.WORK_EMAIL: "Work emails",
    Category.OTHER: "Alerts from this app",
}


def _whole_minutes(minutes: float) -> int:
    # Halves round up, as Kotlin's roundToInt does, so both phones word it the same way.
    return max(1, math.floor(minutes + 0.5))


def held(category: Category, card: FutureCard, context: TwinContext) -> str:
    focus = "restless" if context.state == PhoneState.FOCUS_RESTLESS else "steady"
    lo = _whole_minutes(card.mins_if_opened_lo)
    hi = _whole_minutes(card.mins_if_opened_hi)
    return (
        f"Held: {_whole_minutes(context.focus_minutes_elapsed)} min into {focus} focus. "
        f"{CATEGORY_LABELS[category]} can pull you in for {lo}-{hi} min. "
        f"Back at your break in {_whole_minutes(context.minutes_until_release)} min."
    )


def might_matter(used_sender_history: bool) -> str:
    if used_sender_history:
        return "Delivered: might matter. You have marked this sender before."
    return "Delivered: might matter, so it is not worth the risk of holding."


def cheap_to_show(card: FutureCard) -> str:
    return f"Delivered: showing it now costs about {card.l_mean:.1f} focus minutes (estimated)."


def explored() -> str:
    return "Delivered to keep learning: now and then the twin checks a hold was right."


def free_state() -> str:
    return "Delivered: no focus session is running."


def observe_mode(would_be: Action) -> str:
    verb = "held" if would_be == Action.HOLD else "delivered"
    return f"Observe mode: delivered. In Auto it would have been {verb}."


def as_suggestion(explanation: str, recommended: Action) -> str:
    # "Held: ..." becomes "Suggest holding: ...", so the user sees it is their call.
    verb = "Suggest holding" if recommended == Action.HOLD else "Suggest delivering"
    _, _, rest = explanation.partition(": ")
    return f"{verb}: {rest}"
