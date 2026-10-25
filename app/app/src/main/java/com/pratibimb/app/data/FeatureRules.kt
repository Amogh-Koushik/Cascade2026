package com.pratibimb.app.data

import com.pratibimb.twin.Category
import java.util.Locale

// The parts of twin_params the phone needs to build features, read from the same file the
// engine decides with, so categories and keyword lists always match what engine.py uses
// (CONTRACTS.md section 3).
class FeatureRules(
    private val appCategories: Map<String, String>,
    private val urgency: List<String>,
    private val bait: List<String>,
    private val otpKeywords: List<String>,
    val repeatWindowMillis: Long,
) {
    fun categoryOf(packageName: String): Category = Category.fromKey(appCategories[packageName])

    fun baitHit(haystack: String): Boolean = bait.any { it in haystack }

    fun urgencyHit(haystack: String): Boolean = urgency.any { it in haystack }

    // A keyword in the title or text, and a run of 4 to 8 digits in the text.
    fun otpPattern(haystack: String, text: String): Boolean =
        otpKeywords.any { it in haystack } && DIGIT_RUN.containsMatchIn(text)

    companion object {
        private val DIGIT_RUN = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

        // Title and text joined by a space and lowercased, exactly as lexicon_hits() does.
        fun haystack(title: String, text: String): String = "$title $text".lowercase(Locale.ROOT)

        @Suppress("UNCHECKED_CAST")
        fun fromParams(params: Map<String, Any?>): FeatureRules {
            val lexicons = params["lexicons"] as Map<String, Any?>
            val policy = params["policy"] as Map<String, Any?>
            val repeatPing = policy["repeat_ping"] as Map<String, Any?>
            fun words(key: String) =
                (lexicons[key] as List<Any?>).map { (it as String).lowercase(Locale.ROOT) }
            return FeatureRules(
                appCategories = (params["app_category_map"] as Map<String, Any?>)
                    .mapValues { it.value as String },
                urgency = words("urgency"),
                bait = words("bait"),
                otpKeywords = words("otp_keywords"),
                repeatWindowMillis = ((repeatPing["window_min"] as Number).toDouble() * 60_000).toLong(),
            )
        }
    }
}
