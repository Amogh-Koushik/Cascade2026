package com.pratibimb.app.engine

import com.pratibimb.twin.Category
import com.pratibimb.twin.NotificationFeatures
import java.util.Locale

// A topic the user said matters right now (params "contexts", from contracts/contexts.py).
data class TopicContext(val id: String, val label: String, val active: Boolean, val phrases: List<String>)

object Contexts {
    // Feeds built to pull attention cannot borrow a context: a sale's "register now" or a reel
    // about placements stays an ordinary social or promo notification.
    private val EXCLUDED = setOf(Category.SOCIAL, Category.SHORT_VIDEO, Category.SHOPPING_PROMO)

    // The first active context with a phrase in the title or text, as a whole word or phrase.
    fun match(title: String, text: String, category: Category, contexts: List<TopicContext>): TopicContext? {
        if (category in EXCLUDED) return null
        val haystack = "$title $text".lowercase(Locale.ROOT)
        return contexts.firstOrNull { context ->
            context.active && context.phrases.any { phrase ->
                Regex("(?<![a-z0-9])${Regex.escape(phrase.lowercase(Locale.ROOT))}(?![a-z0-9])").containsMatchIn(haystack)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun fromParams(params: Map<String, Any?>): List<TopicContext> =
        (params["contexts"] as List<Any?>?).orEmpty().map { raw ->
            val entry = raw as Map<String, Any?>
            TopicContext(
                id = entry["id"] as String,
                label = entry["label"] as String,
                active = entry["active"] as Boolean,
                phrases = (entry["phrases"] as List<Any?>).map { it as String },
            )
        }
}

// How long a held item may wait before release, by what kind of notification it is (params
// policy.cap_by_kind): people get a short leash, feeds the full cap.
data class HoldCaps(val defaultMin: Double, val byKindMin: Map<String, Double>) {
    fun kindOf(features: NotificationFeatures): String? = when {
        features.groupChat -> "group"
        features.isConversation -> "direct"
        features.category == Category.WORK_EMAIL -> "email"
        features.category == Category.SOCIAL || features.category == Category.SHORT_VIDEO -> "social"
        features.category == Category.SHOPPING_PROMO -> "promo"
        else -> null
    }

    fun capFor(features: NotificationFeatures): Double =
        kindOf(features)?.let { byKindMin[it] } ?: defaultMin

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromParams(params: Map<String, Any?>): HoldCaps {
            val policy = params["policy"] as Map<String, Any?>
            val byKind = (policy["cap_by_kind"] as Map<String, Any?>?).orEmpty()
                .mapValues { (it.value as Number).toDouble() }
            return HoldCaps((policy["cap_min"] as Number).toDouble(), byKind)
        }
    }
}
