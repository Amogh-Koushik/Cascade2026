package com.pratibimb.app.engine

import com.pratibimb.twin.Action
import com.pratibimb.twin.Decision
import com.pratibimb.twin.DecisionRule

// When tapping one of our alert pings opens the pause card ("what happens if I allow it")
// instead of the original app. Plain Kotlin, so it runs in JVM tests.
object PauseCardRules {
    const val MIN_EXPECTED_LOSS_MIN = 5.0
    const val MAX_CARDS_PER_SESSION = 3
    const val MIN_GAP_MILLIS = 20 * 60_000L
    const val AUTO_DISMISS_MILLIS = 5_000L
    const val OPEN_COUNTDOWN_SECONDS = 3

    // Model decisions: the twin weighed it and chose to deliver, or was unsure.
    private val MODEL_RULES = setOf(DecisionRule.DELIVER_BETTER, DecisionRule.MIGHT_MATTER, DecisionRule.EXPLORATION)

    // Decided when the notification is delivered: delivered by the model (sure or unsure) or by
    // the Instagram/Snapchat burst rule; never by a safety override (calls, OTPs, rides,
    // always-deliver), a content rule (mention, bank or order status, a person's 1:1 chat,
    // context, deadline) or a WhatsApp 1:1 chat; and the expected focus loss (L p50) is at least
    // params features.pause_card_min_loss_min.
    fun eligible(
        decision: Decision,
        contentRule: String?,
        whatsAppOneToOne: Boolean,
        minLossMin: Double = MIN_EXPECTED_LOSS_MIN,
    ): Boolean {
        if (decision.action != Action.DELIVER || whatsAppOneToOne) return false
        val byModel = contentRule == null && decision.rule in MODEL_RULES
        val byBurst = contentRule == BURST_RULE
        return (byModel || byBurst) && decision.card.lP50 >= minLossMin
    }

    // Decided when the ping is tapped: at most three cards a session, twenty minutes apart,
    // and none once the user asked for fewer.
    fun canShow(nowMillis: Long, shownThisSession: Int, lastShownAtMillis: Long, askedLess: Boolean): Boolean =
        !askedLess &&
            shownThisSession < MAX_CARDS_PER_SESSION &&
            (lastShownAtMillis == 0L || nowMillis - lastShownAtMillis >= MIN_GAP_MILLIS)

    // The rule name ContentPolicy gives the Instagram/Snapchat "5 texts in 2 minutes" delivery.
    const val BURST_RULE = "5 texts in 2 minutes"
}
