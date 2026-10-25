package com.pratibimb.app.engine

import java.util.Calendar
import java.util.Locale

// What a notification's text looks like, beyond the keyword lists: a bare photo or reel, a
// forward, a link, a question, a deadline. Read from the title and text only; anything that
// matches no pattern is plain text. Plain Kotlin, so it runs in JVM tests.
enum class ContentKind { TEXT, MEDIA, FORWARD }

data class ContentFeatures(
    val kind: ContentKind,
    val hasUrl: Boolean,
    val hasQuestion: Boolean,
    // The deadline phrase found ("by 6 pm", "last date"), or null.
    val deadlinePhrase: String?,
    // When that deadline falls, if the phrase names a time; null for "today" or "last date".
    val deadlineAtMillis: Long?,
) {
    companion object {
        val PLAIN = ContentFeatures(ContentKind.TEXT, false, false, null, null)
    }
}

object ContentRules {
    // A message that is only an attachment, as messaging apps word it in the notification.
    private val MEDIA_ONLY = Regex(
        "^\\W*(sent )?(a |an )?(photo|video|gif|sticker|voice message|audio|image|document|attachment|" +
            "contact|location)s?\\W*$",
    )
    private val MEDIA_SENT = Regex("\\bsent (you )?(a |an )?(photo|video|gif|sticker|voice message|attachment)\\b")

    // Reels, posts and stories passed along, and anything marked forwarded.
    private val FORWARD = Regex(
        "\\bforwarded\\b|\\b(sent|shared) (you )?(a |an )?(reel|post|story|video)\\b|" +
            "\\bsent (you )?a (reel|post) by\\b",
    )

    private val URL = Regex(
        "https?://\\S+|www\\.\\S+|\\b[a-z0-9-]+\\.(com|in|ly|io|co|org|net|me|xyz|link|app|info|site|live)(/\\S*)?\\b",
    )

    // Clock times: "by 6 pm", "at 6:30pm", "before 18:00", "closes at 5".
    private val CLOCK = Regex("\\b(by|before|at|till|until|closes at|ends at)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")

    // Relative times: "in 30 min", "within 2 hours".
    private val RELATIVE = Regex("\\b(in|within)\\s+(\\d{1,3})\\s*(min|mins|minutes|hr|hrs|hour|hours)\\b")

    // Deadline words with no time of day.
    private val DEADLINE_WORDS = listOf(
        "deadline", "last date", "last day", "due today", "due tomorrow", "closes today", "ends today",
        "by eod", "eod", "by tonight", "tonight", "today only", "kal tak", "aaj tak", "aaj raat",
    )

    fun classify(title: String, text: String, hasPicture: Boolean, postedAtMillis: Long): ContentFeatures {
        val body = text.trim().lowercase(Locale.ROOT)
        val haystack = "$title $text".lowercase(Locale.ROOT)
        val kind = when {
            FORWARD.containsMatchIn(body) -> ContentKind.FORWARD
            body.isEmpty() && hasPicture -> ContentKind.MEDIA
            MEDIA_ONLY.matches(body) || MEDIA_SENT.containsMatchIn(body) -> ContentKind.MEDIA
            else -> ContentKind.TEXT
        }
        val deadline = deadlineIn(haystack, postedAtMillis)
        return ContentFeatures(
            kind = kind,
            hasUrl = URL.containsMatchIn(haystack),
            hasQuestion = '?' in text,
            deadlinePhrase = deadline?.first,
            deadlineAtMillis = deadline?.second,
        )
    }

    private fun deadlineIn(haystack: String, postedAtMillis: Long): Pair<String, Long?>? {
        RELATIVE.find(haystack)?.let { match ->
            val amount = match.groupValues[2].toLong()
            val unitMillis = if (match.groupValues[3].startsWith("h")) 3_600_000L else 60_000L
            return match.value to postedAtMillis + amount * unitMillis
        }
        CLOCK.find(haystack)?.let { match ->
            clockTime(match, postedAtMillis)?.let { return match.value to it }
        }
        val word = DEADLINE_WORDS.firstOrNull { it in haystack } ?: return null
        return word to null
    }

    // The next time today matching the clock phrase; a bare "5" with no am/pm reads as the
    // next 5 o'clock after the message, morning or evening.
    private fun clockTime(match: MatchResult, postedAtMillis: Long): Long? {
        val hourText = match.groupValues[2].toInt()
        val minute = match.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val suffix = match.groupValues[4]
        if (minute > 59 || hourText > 23) return null
        val hours = when {
            suffix == "pm" && hourText in 1..11 -> listOf(hourText + 12)
            suffix == "am" && hourText == 12 -> listOf(0)
            suffix.isNotEmpty() -> listOf(hourText)
            hourText in 1..12 -> listOf(hourText % 12, hourText % 12 + 12)
            else -> listOf(hourText)
        }
        // A bare number with no am/pm and no colon ("at 5") is too vague after "at".
        if (suffix.isEmpty() && match.groupValues[3].isEmpty() && match.groupValues[1] == "at") return null
        return hours
            .map { atToday(postedAtMillis, it, minute) }
            .filter { it >= postedAtMillis }
            .minOrNull()
    }

    private fun atToday(postedAtMillis: Long, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = postedAtMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // A group message mentions you when it tags everyone, or tags one of the names you go by.
    fun mentionsYou(text: String, names: Collection<String>): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        if (listOf("@everyone", "@all", "@channel").any { it in lower }) return true
        return names.any { name -> name.isNotBlank() && "@${name.trim().lowercase(Locale.ROOT)}" in lower }
    }
}
