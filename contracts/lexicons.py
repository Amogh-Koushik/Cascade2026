# The keyword lists every params file uses, in English and Hinglish (Hindi in Latin script).
# Matching is a lowercase substring check on "title text" (CONTRACTS.md section 3), so each
# entry is chosen so it cannot fire inside an ordinary word or a name: "abhi" alone would match
# "Abhishek", and "offer" alone would match a placement "offer letter", so both appear only
# inside longer phrases.
#
# What each list does in the engine:
#   urgency      raises worth, but only inside a personal conversation, so a promo that
#                shouts "urgent" gains nothing from it.
#   bait         lowers worth: words written to pull you in rather than to inform.
#   otp_keywords marks a one-time password, together with a 4 to 8 digit run in the text;
#                only SMS, messaging and payment apps get the never-hold OTP override.
#
# Keep OTP words out of the urgency list, or every OTP-style promo would count as urgent.
# Entries are plain ASCII, so every JSON writer and parser on both sides handles them alike.

URGENCY = (
    # English
    "urgent",
    "emergency",
    "asap",
    "call me",
    "call back",
    "pick up",
    "pick the phone",
    "right now",
    "immediately",
    "hospital",
    "admitted",
    "accident",
    "help me",
    "not well",
    "come home",
    "where are you",
    # Hinglish
    "jaldi",
    "turant",
    "zaroori",
    "zaruri",
    "abhi call",
    "abhi aa",
    "call kar",
    "phone utha",
    "phone uthao",
    "kahan ho",
    "ghar aa",
    "tabiyat",
)

BAIT = (
    # Social pull
    "liked your",
    "commented on",
    "mentioned you",
    "tagged you",
    "started following",
    "viewed your",
    "new followers",
    "is live",
    "live now",
    "just posted",
    "watch now",
    "trending",
    "you won't believe",
    # Sales pressure
    "% off",
    "cashback",
    "coupon",
    "free delivery",
    "don't miss",
    "dont miss",
    "last chance",
    "limited time",
    "offer ends",
    "special offer",
    "deal of the day",
    "sale is live",
    "sale ends",
    "flash sale",
    # Hinglish
    "miss mat karo",
    "sirf aaj",
    "abhi dekho",
    "offer khatam",
)

OTP_KEYWORDS = (
    "otp",
    "one time password",
    "one-time password",
    "verification code",
    "security code",
    "login code",
    "passcode",
    "is your code",
)

LEXICONS: dict[str, list[str]] = {
    "urgency": list(URGENCY),
    "bait": list(BAIT),
    "otp_keywords": list(OTP_KEYWORDS),
}
