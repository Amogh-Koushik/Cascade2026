# Allowed range for every number in a persona config. These are assumed starter bounds, wide
# enough for real differences between people and narrow enough to stop an LLM from inventing
# someone who opens 95% of promos. Tighten them once data_notes.md has the dataset figures.

from dataclasses import dataclass


@dataclass(frozen=True)
class Range:
    lo: float
    hi: float

    def clamp(self, value: float) -> float:
        return min(max(value, self.lo), self.hi)


# Pull multiplier per category, relative to the global open rate.
R_C = Range(0.2, 3.0)

# Pull multiplier and log-minutes shift per phone state. FREE is the reference state, so it is
# pinned to exactly 1.0 and 0.0.
R_S = {
    "FREE": Range(1.0, 1.0),
    "FOCUS_STEADY": Range(0.5, 1.2),
    "FOCUS_RESTLESS": Range(1.0, 2.0),
}
DELTA_S = {
    "FREE": Range(0.0, 0.0),
    "FOCUS_STEADY": Range(-0.3, 0.3),
    "FOCUS_RESTLESS": Range(0.0, 0.6),
}

# Notifications per hour, per category and for all categories together.
RATE_PER_CATEGORY = Range(0.0, 30.0)
MAX_RATE_PER_HOUR = 60.0
HOURS_PER_DAY = 24

# Share of a category's notifications that truly matter. Promos and short video are capped low:
# a persona where they often matter would teach the twin the wrong lesson.
IMPORTANT_SHARE = {
    "short_video": Range(0.0, 0.05),
    "social": Range(0.0, 0.2),
    "messaging": Range(0.02, 0.6),
    "shopping_promo": Range(0.0, 0.05),
    "news": Range(0.0, 0.1),
    "work_email": Range(0.05, 0.7),
    "other": Range(0.0, 0.2),
}

FOCUS_DURATIONS_MIN = (25, 50, 90)
MIN_SESSIONS_PER_DAY = 1
MAX_SESSIONS_PER_DAY = 6
