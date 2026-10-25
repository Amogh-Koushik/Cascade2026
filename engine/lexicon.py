# Keyword hits on a notification's title and text, by the rules in CONTRACTS.md section 3.
# The phone does the same on-device; this copy lets the text bank and the red-team set be
# turned into features on the laptop with identical results.

import re
from dataclasses import dataclass
from typing import Any

# A run of 4 to 8 digits with no digit directly before or after it.
DIGIT_RUN = re.compile(r"(?<!\d)\d{4,8}(?!\d)")


@dataclass(frozen=True)
class LexiconHits:
    bait: bool
    urgency: bool
    otp: bool


def _any_entry(haystack: str, entries: list[str]) -> bool:
    return any(entry.lower() in haystack for entry in entries)


def lexicon_hits(title: str, text: str, lexicons: dict[str, Any]) -> LexiconHits:
    haystack = f"{title} {text}".lower()
    otp_keyword = _any_entry(haystack, lexicons["otp_keywords"])
    return LexiconHits(
        bait=_any_entry(haystack, lexicons["bait"]),
        urgency=_any_entry(haystack, lexicons["urgency"]),
        otp=otp_keyword and DIGIT_RUN.search(text) is not None,
    )
