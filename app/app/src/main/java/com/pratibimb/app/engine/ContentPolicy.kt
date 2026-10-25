package com.pratibimb.app.engine

import com.pratibimb.twin.Action
import com.pratibimb.twin.Category
import com.pratibimb.twin.Decision
import com.pratibimb.twin.DecisionRule
import com.pratibimb.twin.NotificationFeatures

// Content rules applied after the engine decides. They never touch a safety override, the free
// state, Observe mode or a Suggest-mode recommendation. In order: a new sender's link is held
// and flagged (safety before urgency, which is how a fake "register by 6 pm" link is caught);
// a new sender's plain text is delivered once; a deadline within two hours, or a match with an
// active context, is delivered; anything that is not a typed message from a person is held
// (focus gate, on by default); a group message that doesn't mention you is held. The other
// three switchable rules are off until the user turns them on; deadline and context always apply.
// The repeat-ping rule (text messages only) acts earlier, in the feature builder.
data class ContentRuleFlags(
    val textOnlyRepeatPings: Boolean = false,
    val holdUnmentionedGroups: Boolean = false,
    val newSenderLinks: Boolean = false,
    // In focus, only a typed message from a person is left to the model; shares, snaps, social
    // activity, email, promos and news wait, unless a person or an email uses urgent words.
    val focusGate: Boolean = true,
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
        // The active context the text matches, if any (see Contexts).
        contextLabel: String? = null,
        nowMillis: Long = 0L,
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
        val deadline = content.deadlineAtMillis
        if (deadline != null && deadline >= nowMillis && deadline - nowMillis <= DEADLINE_SOON_MILLIS) {
            return deliverIfHeld(decision, "Delivered: deadline within 2 hours (${content.deadlinePhrase})", "deadline soon")
        }
        if (contextLabel != null) {
            return deliverIfHeld(decision, "Delivered: matches your $contextLabel context", "context $contextLabel")
        }
        if (flags.focusGate && decision.action == Action.DELIVER) {
            val typedByAPerson = features.isConversation && content.kind == ContentKind.TEXT
            // Urgent words count from people and email only; a promo shouting "urgent" still waits.
            val urgent = features.urgencyHit && (features.isConversation || features.category == Category.WORK_EMAIL)
            if (!typedByAPerson && !urgent) {
                return RuledDecision(hold(decision, "Held: not a typed message from a person"), "focus gate", null)
            }
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

    // Already delivered stays as it is, but the rules after this one no longer apply to it.
    private fun deliverIfHeld(decision: Decision, reason: String, rule: String): RuledDecision =
        if (decision.action == Action.HOLD) {
            RuledDecision(decision.copy(action = Action.DELIVER, reason = reason, explanation = "$reason."), rule, null)
        } else {
            RuledDecision(decision, null, null)
        }

    const val DEADLINE_SOON_MILLIS = 2 * 3_600_000L

    private fun hold(decision: Decision, reason: String): Decision =
        decision.copy(action = Action.HOLD, explored = false, reason = reason, explanation = "$reason.")
}
