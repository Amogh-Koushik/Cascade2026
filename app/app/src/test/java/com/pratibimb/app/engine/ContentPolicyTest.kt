package com.pratibimb.app.engine

import com.pratibimb.twin.Action
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.Category
import com.pratibimb.twin.Decision
import com.pratibimb.twin.DecisionRule
import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.NotificationFeatures
import com.pratibimb.twin.PhoneState
import com.pratibimb.twin.RealTwinEngine
import com.pratibimb.twin.TwinContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class ContentPolicyTest {
    private val engine = RealTwinEngine().apply {
        loadParams(
            generateSequence(File("").absoluteFile) { it.parentFile }
                .map { File(it, "contracts/params/twin_params_student_coder.json") }
                .first { it.isFile }
                .readText(),
        )
    }

    private val context = TwinContext(
        state = PhoneState.FOCUS_STEADY, autonomy = Autonomy.AUTO, minutesUntilRelease = 25.0,
        focusMinutesElapsed = 5.0, fatigueRisk = FatigueRisk.LOW, explorationDraw = 1.0,
        alwaysDeliverSenders = emptySet(), alwaysDeliverPackages = emptySet(), demoLearning = false,
    )

    private fun message(group: Boolean = false, sender: String? = "s_new", otp: Boolean = false) = NotificationFeatures(
        key = "k", postedAtMillis = 0L, packageName = if (otp) "com.google.android.apps.messaging" else "com.whatsapp",
        category = Category.MESSAGING, androidCategory = null, channelImportance = 4, isOngoing = false,
        isConversation = true, priorityConversation = false, groupChat = group,
        senderHash = if (group) null else sender, senderDisplayName = null, sameSenderRecentPings = 0,
        baitHit = false, urgencyHit = false, otpPattern = otp,
    )

    private val link = ContentFeatures.PLAIN.copy(hasUrl = true)
    private val allOn = ContentRuleFlags(textOnlyRepeatPings = true, holdUnmentionedGroups = true, newSenderLinks = true)

    private fun ruled(features: NotificationFeatures, content: ContentFeatures, flags: ContentRuleFlags, mentions: Boolean = false, newSender: Boolean = true): RuledDecision {
        val decision = engine.decide(features, context)
        return ContentPolicy.apply(decision, features, content, flags, mentions, newSender)
    }

    @Test
    fun withEveryFlagOffNothingChanges() {
        val features = message()
        val decision = engine.decide(features, context)

        assertSame(decision, ContentPolicy.apply(decision, features, link, ContentRuleFlags(), false, true).decision)
    }

    @Test
    fun aNewSendersLinkIsHeldAndFlagged() {
        val result = ruled(message(), link, allOn)

        assertEquals(Action.HOLD, result.decision.action)
        assertEquals("New sender with a link", result.flag)
    }

    @Test
    fun aNewSendersPlainTextIsDeliveredOnce() {
        val first = ruled(message(), ContentFeatures.PLAIN, allOn)
        val later = ruled(message(), ContentFeatures.PLAIN, allOn, newSender = false)

        assertEquals(Action.DELIVER, first.decision.action)
        assertEquals("Delivered: first message from a new sender", first.decision.reason)
        assertNull(later.rule)
    }

    @Test
    fun aGroupMessageWithoutAMentionIsHeldUnlessItMentionsYou() {
        val group = message(group = true)
        val flags = ContentRuleFlags(holdUnmentionedGroups = true)
        val unmentioned = ruled(group, ContentFeatures.PLAIN, flags)
        val mentioned = ruled(group, ContentFeatures.PLAIN, flags, mentions = true)

        assertEquals(Action.HOLD, unmentioned.decision.action)
        assertEquals(engine.decide(group, context).action, mentioned.decision.action)
    }

    private val like = NotificationFeatures(
        key = "like", postedAtMillis = 0L, packageName = "com.google.android.gm", category = Category.WORK_EMAIL,
        androidCategory = null, channelImportance = 3, isOngoing = false, isConversation = false,
        priorityConversation = false, groupChat = false, senderHash = null, senderDisplayName = null,
        sameSenderRecentPings = 0, baitHit = true, urgencyHit = false, otpPattern = false,
    )

    private fun heldByEngine(): Decision = engine.decide(like, context).copy(action = Action.HOLD, rule = DecisionRule.HOLD_BETTER)

    @Test
    fun aDeadlineWithinTwoHoursIsDelivered() {
        val hour = 3_600_000L
        val soon = ContentFeatures.PLAIN.copy(deadlinePhrase = "by 6 pm", deadlineAtMillis = 90 * 60_000L)
        val later = ContentFeatures.PLAIN.copy(deadlinePhrase = "by 9 pm", deadlineAtMillis = 3 * hour)

        val delivered = ContentPolicy.apply(heldByEngine(), like, soon, ContentRuleFlags(), false, false, nowMillis = 0L)
        val stillHeld = ContentPolicy.apply(heldByEngine(), like, later, ContentRuleFlags(), false, false, nowMillis = 0L)

        assertEquals(Action.DELIVER, delivered.decision.action)
        assertEquals("Delivered: deadline within 2 hours (by 6 pm)", delivered.decision.reason)
        assertEquals(Action.HOLD, stillHeld.decision.action)
    }

    @Test
    fun aContextMatchIsDelivered() {
        val result = ContentPolicy.apply(heldByEngine(), like, ContentFeatures.PLAIN, ContentRuleFlags(), false, false, contextLabel = "Placements")

        assertEquals(Action.DELIVER, result.decision.action)
        assertEquals("Delivered: matches your Placements context", result.decision.reason)
    }

    @Test
    fun aNewSendersLinkIsHeldEvenWithADeadlineAndAContext() {
        val bait = ContentFeatures.PLAIN.copy(hasUrl = true, deadlinePhrase = "in 30 min", deadlineAtMillis = 30 * 60_000L)

        val result = ContentPolicy.apply(engine.decide(message(), context), message(), bait, allOn, false, true, "Placements", 0L)

        assertEquals(Action.HOLD, result.decision.action)
        assertEquals("New sender with a link", result.flag)
    }

    @Test
    fun aContextMatchKeepsAGroupMessageFromTheMentionRule() {
        val group = message(group = true)
        val decision = engine.decide(group, context)
        val result = ContentPolicy.apply(decision, group, ContentFeatures.PLAIN, ContentRuleFlags(holdUnmentionedGroups = true), false, false, "Placements")

        assertEquals(decision.action, result.decision.action)
    }

    @Test
    fun safetyOverridesAreNeverChanged() {
        val otp = message(otp = true, sender = null)
        val decision = engine.decide(otp, context)

        assertEquals(DecisionRule.OVERRIDE, decision.rule)
        assertSame(decision, ContentPolicy.apply(decision, otp, link, allOn, false, true).decision)
    }

    @Test
    fun freeStateAndSuggestModeAreLeftAlone() {
        val features = message()
        val free = engine.decide(features, context.copy(state = PhoneState.FREE))
        val suggest: Decision = engine.decide(features, context.copy(autonomy = Autonomy.SUGGEST))

        assertSame(free, ContentPolicy.apply(free, features, link, allOn, false, true).decision)
        assertSame(suggest, ContentPolicy.apply(suggest, features, link, allOn, false, true).decision)
    }
}
