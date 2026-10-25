import pytest

from contracts.lexicons import LEXICONS
from engine.lexicon import lexicon_hits


@pytest.mark.parametrize("name", sorted(LEXICONS))
def test_entries_are_unique_lowercase_ascii(name: str) -> None:
    words = LEXICONS[name]

    assert len(words) == len(set(words))
    assert all(word == word.lower() and word.isascii() and word.strip() == word for word in words)


def test_otp_words_never_count_as_urgency() -> None:
    urgency = " ".join(LEXICONS["urgency"])

    assert not any(word in urgency for word in LEXICONS["otp_keywords"])
    assert "code" not in LEXICONS["urgency"]


@pytest.mark.parametrize(
    ("title", "text", "bait", "urgency", "otp"),
    [
        ("Papa", "Beta jaldi ghar aa, dadi ki tabiyat kharab hai", False, True, False),
        ("Didi", "Accident hua hai, please call me", False, True, False),
        ("Rahul", "phone utha yaar", False, True, False),
        ("rohan_k", "Rohan liked your reel", True, False, False),
        ("Myntra", "Sale ends tonight! Flat 60% off", True, False, False),
        ("Swiggy", "Miss mat karo, sirf aaj free delivery", True, False, False),
        ("HDFCBK", "482913 is your OTP for login", False, False, True),
        ("Google", "G-482913 is your verification code", False, False, True),
        # Everyday words and names that must not trip a list.
        ("Abhishek", "sent you the notes", False, False, False),
        ("Placement Cell", "Your offer letter from Infosys is attached", False, False, False),
        ("Prof. Rao", "This was really helpful, thanks", False, False, False),
        ("Bank", "Your OTP is on its way", False, False, False),
    ],
)
def test_lexicon_hits_on_real_style_notifications(
    title: str, text: str, bait: bool, urgency: bool, otp: bool
) -> None:
    hits = lexicon_hits(title, text, LEXICONS)

    assert (hits.bait, hits.urgency, hits.otp) == (bait, urgency, otp)
