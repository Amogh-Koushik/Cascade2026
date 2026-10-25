package com.pratibimb.app.engine

import com.pratibimb.twin.Action
import com.pratibimb.twin.Decision
import com.pratibimb.twin.DecisionRule
import com.pratibimb.twin.NotificationFeatures

// Content rules the user can switch on one at a time, applied after the engine decides. They
// never touch a safety override, the free state, Observe mode or a Suggest-mode recommendation.
// The repeat-ping rule (text messages only) acts earlier, in the feature builder.
data class ContentRuleFlags(
    val textOnlyRepeatPings: Boolean = false,
    val holdUnmentionedGroups: Boolean = false,
    val newSenderLinks: Boolean = false,
)

data class RuledDecision(
    val decision: Decision,
    // Which content rule changed the decision, for the log; null when none did.
    val rule: String?,
    // Shown on the tray item, e.g. "New sender with a link".
    val flag: String?,
)

object ContentPolicy {
    private val UNTOUCHABLE = setOf(DecisionRule.OVERRIDE, DecisionRule.FREE_STATE, DecisionRule.OBSERVE_MODE)

    fun apply(
        decision: Decision,
        features: NotificationFeatures,
        content: ContentFeatures,
        flags: ContentRuleFlags,
        mentionsYou: Boolean,
        newSender: Boolean,
    ): RuledDecision {
        if (decision.rule in UNTOUCHABLE || decision.suggestOnly) return RuledDecision(decision, null, null)
        if (flags.newSenderLinks && newSender && content.hasUrl) {
            return RuledDecision(
                hold(decision, "Held and flagged: a new sender's first message has a link"),
                "new sender with a link",
                "New sender with a link",
            )
        }
        if (flags.newSenderLinks && newSender) {
            return RuledDecision(
                decision.copy(
                    action = Action.DELIVER,
                    reason = "Delivered: first message from a new sender",
                    explanation = "Delivered: the first plain message from someone new always comes through once.",
                ),
                "new sender, plain text",
                null,
            )
        }
        if (flags.holdUnmentionedGroups && features.groupChat && !mentionsYou && decision.action == Action.DELIVER) {
            return RuledDecision(
                hold(decision, "Held: group message that doesn't mention you"),
                "group without a mention",
                null,
            )
        }
        return RuledDecision(decision, null, null)
    }

    private fun hold(decision: Decision, reason: String): Decision =
        decision.copy(action = Action.HOLD, explored = false, reason = reason, explanation = "$reason.")
}
