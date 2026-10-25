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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    private fun features(
        pkg: String,
        category: Category,
        conversation: Boolean = false,
        group: Boolean = false,
        sender: String? = null,
        recentPings: Int = 0,
        urgent: Boolean = false,
        otp: Boolean = false,
    ) = NotificationFeatures(
        key = "k", postedAtMillis = 0L, packageName = pkg, category = category, androidCategory = null,
        channelImportance = 4, isOngoing = false, isConversation = conversation, priorityConversation = false,
        groupChat = group, senderHash = if (group) null else sender, senderDisplayName = if (group) null else sender,
        sameSenderRecentPings = recentPings, baitHit = false, urgencyHit = urgent, otpPattern = otp,
    )

    private val instaDm = features("com.instagram.android", Category.SOCIAL, conversation = true, sender = "Rohan")
    private val whatsappDm = features("com.whatsapp", Category.MESSAGING, conversation = true, sender = "Mom")
    private val group = features("com.whatsapp", Category.MESSAGING, conversation = true, group = true)
    private val gmail = features("com.google.android.gm", Category.WORK_EMAIL)
    private val swiggy = features("in.swiggy.android", Category.SHOPPING_PROMO)
    private val text = ContentFeatures.PLAIN
    private val on = ContentRuleFlags()

    private fun decide(
        f: NotificationFeatures,
        signals: FocusSignals = FocusSignals(),
        content: ContentFeatures = text,
        flags: ContentRuleFlags = on,
    ) = ContentPolicy.apply(engine.decide(f, context), f, content, flags, signals)

    private fun insta(burst: Int) = FocusSignals(family = AppFamily.INSTAGRAM, textBurst = burst)

    @Test
    fun instagramDmIsHeldUntilFiveTextsInTwoMinutes() {
        assertEquals(Action.HOLD, decide(instaDm, insta(4)).decision.action)

        val fifth = decide(instaDm, insta(5))
        assertEquals(Action.DELIVER, fifth.decision.action)
        assertEquals("Delivered: Rohan ×5 in 2 minutes", fifth.decision.reason)
        assertTrue(fifth.releaseSenderBurst)
    }

    @Test
    fun reelsAndSnapsNeverCountTowardsTheFive() {
        assertEquals(Action.HOLD, decide(instaDm, insta(9), text.copy(kind = ContentKind.FORWARD)).decision.action)
    }

    @Test
    fun instagramFollowsItsOwnRuleEvenAfterThreeRepeatPings() {
        val pinged = instaDm.copy(sameSenderRecentPings = 2)

        assertEquals(DecisionRule.OVERRIDE, engine.decide(pinged, context).rule)
        assertEquals(Action.HOLD, decide(pinged, insta(3)).decision.action)
    }

    @Test
    fun whatsAppAndOtherOneToOneMessagesAreDelivered() {
        assertEquals(Action.DELIVER, decide(whatsappDm, FocusSignals(family = AppFamily.WHATSAPP)).decision.action)
        val telegram = features("org.telegram.messenger", Category.MESSAGING, conversation = true, sender = "Rahul")
        assertEquals(Action.DELIVER, decide(telegram, FocusSignals(family = AppFamily.TELEGRAM)).decision.action)
    }

    @Test
    fun groupsAreHeldUnlessMarkedWorthItOrTheyMentionYou() {
        assertEquals(Action.HOLD, decide(group).decision.action)
        assertEquals(Action.DELIVER, decide(group, FocusSignals(groupWorth = true)).decision.action)
        assertEquals(Action.DELIVER, decide(group, FocusSignals(mentionsYou = true)).decision.action)
    }

    @Test
    fun snapsStreaksPromosAndBotsAreHeld() {
        val snapDm = features("com.snapchat.android", Category.SOCIAL, conversation = true, sender = "Rohan")
        val snapchat = FocusSignals(family = AppFamily.SNAPCHAT)
        assertEquals(Action.HOLD, decide(snapDm, snapchat, text.copy(kind = ContentKind.FORWARD)).decision.action)
        assertEquals(Action.HOLD, decide(snapDm, snapchat.copy(streak = true)).decision.action)
        assertEquals(Action.HOLD, decide(swiggy).decision.action)
        val bot = features("org.telegram.messenger", Category.MESSAGING, conversation = true, sender = "QuizBot")
        assertEquals(Action.HOLD, decide(bot, FocusSignals(family = AppFamily.TELEGRAM, bot = true)).decision.action)
    }

    @Test
    fun orderStatusAndBankAlertsAreDelivered() {
        assertEquals(Action.DELIVER, decide(swiggy, FocusSignals(orderStatus = true)).decision.action)
        val sms = features("com.google.android.apps.messaging", Category.MESSAGING)
        val bankAlert = FocusSignals(family = AppFamily.SMS, bankAlert = true, bot = true)
        assertEquals(Action.DELIVER, decide(sms, bankAlert).decision.action)
    }

    @Test
    fun safetyOverridesAlwaysStand() {
        val otp = features("com.google.android.apps.messaging", Category.MESSAGING, otp = true)
        val decision = engine.decide(otp, context)

        assertEquals(DecisionRule.OVERRIDE, decision.rule)
        assertSame(decision, ContentPolicy.apply(decision, otp, text, on, FocusSignals(bot = true)).decision)
    }

    @Test
    fun everythingElseIsHeldUnlessUrgentAContextOrADeadline() {
        assertEquals(Action.HOLD, decide(gmail).decision.action)
        assertEquals(Action.DELIVER, decide(gmail.copy(urgencyHit = true)).decision.action)
        assertEquals(Action.DELIVER, decide(gmail, FocusSignals(contextLabel = "Placements")).decision.action)
        val soon = text.copy(deadlinePhrase = "in 30 min", deadlineAtMillis = 30 * 60_000L)
        assertEquals(Action.DELIVER, decide(gmail, FocusSignals(nowMillis = 0L), soon).decision.action)
    }

    @Test
    fun aNewSendersLinkIsHeldAndFlaggedEvenWithAContext() {
        val result = decide(
            whatsappDm,
            FocusSignals(newSender = true, contextLabel = "Placements"),
            text.copy(hasUrl = true),
            on.copy(newSenderLinks = true),
        )

        assertEquals(Action.HOLD, result.decision.action)
        assertEquals("New sender with a link", result.flag)
    }

    @Test
    fun freeStateAndSuggestModeAreLeftAlone() {
        val free: Decision = engine.decide(gmail, context.copy(state = PhoneState.FREE))
        val suggest: Decision = engine.decide(gmail, context.copy(autonomy = Autonomy.SUGGEST))

        assertSame(free, ContentPolicy.apply(free, gmail, text, on, FocusSignals()).decision)
        assertSame(suggest, ContentPolicy.apply(suggest, gmail, text, on, FocusSignals()).decision)
    }

    @Test
    fun withFocusRulesOffTheEngineDecides() {
        val decision = engine.decide(gmail, context)

        assertSame(decision, ContentPolicy.apply(decision, gmail, text, ContentRuleFlags(focusGate = false), FocusSignals()).decision)
    }
}
