package com.pratibimb.app.engine

import com.pratibimb.twin.Action
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.Category
import com.pratibimb.twin.DecisionRule
import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.NotificationFeatures
import com.pratibimb.twin.PhoneState
import com.pratibimb.twin.RealTwinEngine
import com.pratibimb.twin.TwinContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PauseCardRulesTest {
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

    private fun features(pkg: String, category: Category, conversation: Boolean, sender: String?, pings: Int = 0, bait: Boolean = false) =
        NotificationFeatures(
            key = "k", postedAtMillis = 0L, packageName = pkg, category = category, androidCategory = null,
            channelImportance = 4, isOngoing = false, isConversation = conversation, priorityConversation = false,
            groupChat = false, senderHash = sender, senderDisplayName = sender, sameSenderRecentPings = pings,
            baitHit = bait, urgencyHit = false, otpPattern = false,
        )

    private fun ruled(f: NotificationFeatures, signals: FocusSignals) =
        ContentPolicy.apply(engine.decide(f, context), f, ContentFeatures.PLAIN, ContentRuleFlags(), signals)

    @Test
    fun theInstagramLikeIsStillHeldSoNoCard() {
        val like = features("com.instagram.android", Category.SOCIAL, conversation = false, sender = null, bait = true)
        val result = ruled(like, FocusSignals(family = AppFamily.INSTAGRAM))

        assertEquals(Action.HOLD, result.decision.action)
        assertFalse(PauseCardRules.eligible(result.decision, result.rule, whatsAppOneToOne = false))
    }

    @Test
    fun momOnWhatsAppIsStillDeliveredWithoutACard() {
        val mom = features("com.whatsapp", Category.MESSAGING, conversation = true, sender = "Mom", pings = 2)
        val result = ruled(mom, FocusSignals(family = AppFamily.WHATSAPP))

        assertEquals(Action.DELIVER, result.decision.action)
        assertFalse(PauseCardRules.eligible(result.decision, result.rule, whatsAppOneToOne = true))
    }

    @Test
    fun aBurstDeliveryWithARealLossGetsACard() {
        val dm = features("com.instagram.android", Category.SOCIAL, conversation = true, sender = "Rohan")
        val result = ruled(dm, FocusSignals(family = AppFamily.INSTAGRAM, textBurst = 5))

        assertEquals(PauseCardRules.BURST_RULE, result.rule)
        assertEquals(
            result.decision.card.lP50 >= PauseCardRules.MIN_EXPECTED_LOSS_MIN,
            PauseCardRules.eligible(result.decision, result.rule, whatsAppOneToOne = false),
        )
    }

    @Test
    fun aModelDeliveryNeedsFiveMinutesOfExpectedLoss() {
        val base = engine.decide(features("com.google.android.gm", Category.WORK_EMAIL, false, null), context)
        val model = base.copy(action = Action.DELIVER, rule = DecisionRule.MIGHT_MATTER)

        assertTrue(PauseCardRules.eligible(model.copy(card = model.card.copy(lP50 = 6.0)), null, false))
        assertFalse(PauseCardRules.eligible(model.copy(card = model.card.copy(lP50 = 4.9)), null, false))
    }

    @Test
    fun theDemoBarLetsTheInstagramBurstThrough() {
        val dm = features("com.instagram.android", Category.SOCIAL, conversation = true, sender = "Rohan")
        val result = ruled(dm, FocusSignals(family = AppFamily.INSTAGRAM, textBurst = 5))

        assertTrue(PauseCardRules.eligible(result.decision, result.rule, false, BuildMode.DEMO_PAUSE_CARD_MIN_LOSS_MIN))
    }

    @Test
    fun overridesAndContentRulesNeverGetACard() {
        val base = engine.decide(features("com.google.android.gm", Category.WORK_EMAIL, false, null), context)
        val bigLoss = base.copy(action = Action.DELIVER, card = base.card.copy(lP50 = 20.0))

        assertFalse(PauseCardRules.eligible(bigLoss.copy(rule = DecisionRule.OVERRIDE), null, false))
        assertFalse(PauseCardRules.eligible(bigLoss.copy(rule = DecisionRule.MIGHT_MATTER), "mention", false))
        assertFalse(PauseCardRules.eligible(bigLoss.copy(rule = DecisionRule.MIGHT_MATTER), "bank alert", false))
    }

    @Test
    fun atMostThreeCardsASessionTwentyMinutesApartUnlessAskedForLess() {
        val minute = 60_000L

        assertTrue(PauseCardRules.canShow(0L, 0, 0L, askedLess = false))
        assertFalse(PauseCardRules.canShow(10 * minute, 1, 0L + 1, askedLess = false))
        assertTrue(PauseCardRules.canShow(25 * minute, 1, 1L, askedLess = false))
        assertFalse(PauseCardRules.canShow(90 * minute, 3, 1L, askedLess = false))
        assertFalse(PauseCardRules.canShow(0L, 0, 0L, askedLess = true))
    }
}
