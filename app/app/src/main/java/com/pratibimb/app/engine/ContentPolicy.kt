package com.pratibimb.app.engine

import com.pratibimb.twin.Action
import com.pratibimb.twin.Category
import com.pratibimb.twin.Decision
import com.pratibimb.twin.DecisionRule
import com.pratibimb.twin.NotificationFeatures

// The focus rules, applied after the engine decides, in this order:
//   safety overrides stand (calls, alarms, OTPs, rides, always-deliver); order status and bank
//   alerts are delivered; a new sender's link is held and flagged (switchable); a mention is
//   delivered; snaps, streaks, promos and bots are held; a Placements match or a deadline within
//   two hours is delivered; a genuine urgency keyword (not also a bait hit) bypasses the group
//   hold and the Instagram/Snapchat throttle; a group is delivered once marked worth it 3+
//   times; Instagram and Snapchat 1:1 texts wait until the same person sends the persona's
//   number of texts in 2 minutes (policy.dm_burst_to_deliver: 5 student, 3 nurse);
//   WhatsApp and other 1:1 messengers are delivered; anything else not from a person is held
//   unless it has a genuine urgency keyword.
// Free state, Observe mode and Suggest-mode recommendations are never changed. With focus rules
// switched off, only the switchable rules, the context and the deadline apply.
data class ContentRuleFlags(
    val textOnlyRepeatPings: Boolean = false,
    val newSenderLinks: Boolean = false,
    val focusGate: Boolean = true,
)

// What the rules need to know beyond the engine's features (see MessageSignals).
data class FocusSignals(
    val family: AppFamily = AppFamily.OTHER,
    val orderStatus: Boolean = false,
    val bankAlert: Boolean = false,
    val bot: Boolean = false,
    val streak: Boolean = false,
    val mentionsYou: Boolean = false,
    // The group has been marked worth it more often than not.
    val groupWorth: Boolean = false,
    // Text messages from this 1:1 sender in the last 2 minutes, this one included.
    val textBurst: Int = 0,
    // How many of those get an Instagram/Snapchat chat through (params policy.dm_burst_to_deliver).
    val burstToDeliver: Int = ContentPolicy.BURST_TO_DELIVER,
    val newSender: Boolean = false,
    val contextLabel: String? = null,
    val nowMillis: Long = 0L,
)

data class RuledDecision(
    val decision: Decision,
    // Which rule changed the decision, for the log; null when none did.
    val rule: String?,
    // Shown on the tray item, e.g. "New sender with a link".
    val flag: String? = null,
    // Instagram/Snapchat: the 5th text arrived, so this sender's held messages go out too.
    val releaseSenderBurst: Boolean = false,
)

object ContentPolicy {
    const val DEADLINE_SOON_MILLIS = 2 * 3_600_000L
    const val BURST_TO_DELIVER = 5

    private const val REPEATED_PING = "Always delivered: repeated ping"

    fun apply(
        decision: Decision,
        features: NotificationFeatures,
        content: ContentFeatures,
        flags: ContentRuleFlags,
        signals: FocusSignals,
    ): RuledDecision {
        if (decision.rule == DecisionRule.FREE_STATE || decision.rule == DecisionRule.OBSERVE_MODE || decision.suggestOnly) {
            return unchanged(decision)
        }
        val oneToOne = features.isConversation && !features.groupChat
        val instaOrSnap = oneToOne && (signals.family == AppFamily.INSTAGRAM || signals.family == AppFamily.SNAPCHAT)
        // Safety overrides stand, except that Instagram and Snapchat chats follow their own
        // 5-in-2-minutes rule instead of the repeated-ping override.
        if (decision.rule == DecisionRule.OVERRIDE && !(flags.focusGate && instaOrSnap && decision.reason == REPEATED_PING)) {
            return unchanged(decision)
        }

        if (flags.focusGate && features.category == Category.SHOPPING_PROMO && signals.orderStatus) {
            return deliver(decision, "Delivered: order or delivery status", "order status")
        }
        if (flags.focusGate && signals.bankAlert) return deliver(decision, "Delivered: bank or payment alert", "bank alert")

        if (flags.newSenderLinks && signals.newSender && content.hasUrl) {
            return RuledDecision(hold(decision, "Held and flagged: a new sender's first message has a link"), "new sender with a link", "New sender with a link")
        }
        if (flags.newSenderLinks && signals.newSender) {
            return deliver(decision, "Delivered: first message from a new sender", "new sender, plain text")
        }

        if (flags.focusGate) {
            if (signals.mentionsYou) return deliver(decision, "Delivered: mentions you", "mention")
            if (signals.family == AppFamily.SNAPCHAT && (signals.streak || content.kind != ContentKind.TEXT)) {
                return RuledDecision(hold(decision, "Held: Snapchat snap or streak"), "snap or streak")
            }
            if (features.category == Category.SHOPPING_PROMO) return RuledDecision(hold(decision, "Held: promotion"), "promo")
            if (signals.bot) return RuledDecision(hold(decision, "Held: bot or business sender"), "bot")
        }

        val deadline = content.deadlineAtMillis
        if (deadline != null && deadline >= signals.nowMillis && deadline - signals.nowMillis <= DEADLINE_SOON_MILLIS) {
            return deliver(decision, "Delivered: deadline within 2 hours (${content.deadlinePhrase})", "deadline soon")
        }
        signals.contextLabel?.let { return deliver(decision, "Delivered: matches your $it context", "context $it") }

        if (!flags.focusGate) return unchanged(decision)

        // Urgency keywords ("urgent", "emergency", etc.) override the group hold and the
        // Instagram/Snapchat throttle — but only when the message is NOT also a bait hit
        // (prevents fake "URGENT: 50% off!" promos from leaking through).
        val genuineUrgency = features.urgencyHit && !features.baitHit

        if (features.groupChat) {
            if (genuineUrgency) return deliver(decision, "Delivered: urgent message in group", "urgent group")
            return if (signals.groupWorth) {
                deliver(decision, "Delivered: a group you mark worth it", "group worth it")
            } else {
                RuledDecision(hold(decision, "Held: group message"), "group")
            }
        }
        if (instaOrSnap) {
            if (content.kind == ContentKind.TEXT && signals.textBurst >= signals.burstToDeliver) {
                val who = features.senderDisplayName ?: "This sender"
                val reason = "Delivered: $who ×${signals.textBurst} in 2 minutes"
                return RuledDecision(
                    decision.copy(action = Action.DELIVER, reason = reason, explanation = "$reason."),
                    PauseCardRules.BURST_RULE,
                    releaseSenderBurst = true,
                )
            }
            if (genuineUrgency) return deliver(decision, "Delivered: urgent message", "urgent insta/snap")
            return RuledDecision(
                hold(decision, "Held: single message (${signals.burstToDeliver} texts in 2 minutes get through)"),
                "instagram/snapchat 1:1",
            )
        }
        if (oneToOne) return deliver(decision, "Delivered: message from a person", "1:1 message")

        // Non-person, non-conversation: only deliver if there are genuine urgency signals.
        val urgentBroadcast = features.urgencyHit && !features.baitHit &&
            (features.isConversation || features.category == Category.WORK_EMAIL)
        if (!urgentBroadcast) return RuledDecision(hold(decision, "Held: not a message from a person"), "focus gate")
        return unchanged(decision)
    }

    private fun unchanged(decision: Decision) = RuledDecision(decision, null)

    // A delivery decided here. An engine decision that already delivered keeps its own reason,
    // but the rule is still named, so the pause card knows the model was not what let it through.
    private fun deliver(decision: Decision, reason: String, rule: String): RuledDecision =
        if (decision.action == Action.DELIVER) {
            RuledDecision(decision, rule)
        } else {
            RuledDecision(decision.copy(action = Action.DELIVER, reason = reason, explanation = "$reason."), rule)
        }

    private fun hold(decision: Decision, reason: String): Decision =
        decision.copy(action = Action.HOLD, explored = false, reason = reason, explanation = "$reason.")
}
