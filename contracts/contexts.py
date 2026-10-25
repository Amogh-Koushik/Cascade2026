# Contexts: topics the user has said matter right now, preloaded so the demo works without an
# onboarding screen. On the phone, a notification whose text matches an active context's
# phrases is delivered rather than held (unless a content rule holds it first, as with a link
# from a new sender, which is exactly how a fake placement message gets caught).
#
# Matching is whole-word and case-insensitive on "title text", and never applies to social,
# short-video or promo apps, so a sale's "register now" or a reel about placements cannot use
# it. Entries are plain ASCII, like the lexicons.

PLACEMENTS = {
    "id": "placements",
    "label": "Placements",
    "active": True,
    "phrases": [
        "placement",
        "placements",
        "placement drive",
        "campus drive",
        "drive",
        "registration",
        "register by",
        "last date",
        "shortlist",
        "shortlisted",
        "interview",
        "aptitude test",
        "pre-placement talk",
        "offer letter",
    ],
}

CONTEXTS: list[dict[str, object]] = [PLACEMENTS]
