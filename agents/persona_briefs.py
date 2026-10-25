# The three people the Twin Lab simulates. The persona agent turns each brief into numbers,
# and the range validator keeps those numbers believable. Briefs describe habits, never real
# people. The demo uses the first two; content_creator is the spare.

PERSONA_BRIEFS: dict[str, str] = {
    "student_coder": (
        "A 20-year-old computer science undergraduate in Bengaluru, preparing for semester "
        "exams and campus placements. Studies mostly 9 pm to 1 am on weekdays, plus long "
        "blocks on weekend mornings, with the phone on the desk. Watches Instagram reels and "
        "YouTube shorts heavily and loses 15 to 30 minutes when one pulls them in. Class "
        "WhatsApp groups are very active in the evening, mostly chatter, sometimes a deadline "
        "change. Messages from parents matter and are rare. College portal and professor "
        "emails matter. Gets frequent Swiggy, Zomato and shopping promos. When stuck on a "
        "problem they start switching apps every few minutes."
    ),
    "night_shift_nurse": (
        "A 29-year-old staff nurse in Pune on rotating 12-hour night shifts, 8 pm to 8 am, "
        "and sleeping roughly 9 am to 3 pm. Focus sessions are for charting during quiet hours "
        "of the shift and for studying for a certification in the late afternoon. Messages "
        "from the ward WhatsApp group and from family genuinely matter and are often time "
        "sensitive. Uses social media lightly, mostly in short breaks, and reads news during "
        "the commute. Notifications arrive at hours a daytime user would be asleep. Being "
        "tired late in a shift makes them switch apps more."
    ),
    "content_creator": (
        "A 24-year-old Instagram and YouTube creator in Mumbai. Social notifications are "
        "partly work: comments from collaborators and brand messages can matter, though most "
        "likes and follows do not. Edits videos in long focus blocks from late morning to mid "
        "afternoon. Receives a very high volume of social notifications all day, peaking in "
        "the evening after posting. Messaging is mostly with a small team and family. "
        "Shopping promos are frequent and ignored. Checking analytics is a strong pull that "
        "often turns into long scrolling."
    ),
}

PERSONA_NAMES: tuple[str, ...] = tuple(PERSONA_BRIEFS)
