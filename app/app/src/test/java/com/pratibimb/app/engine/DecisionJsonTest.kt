package com.pratibimb.app.engine

import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.Category
import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.NotificationFeatures
import com.pratibimb.twin.PhoneState
import com.pratibimb.twin.RealTwinEngine
import com.pratibimb.twin.TwinContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DecisionJsonTest {
    private val params: String = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "contracts/params/twin_params_student_coder.json") }
        .first { it.isFile }
        .readText()

    private val like = NotificationFeatures(
        key = "k", postedAtMillis = 0L, packageName = "com.instagram.android",
        category = Category.SOCIAL, androidCategory = null, channelImportance = 3,
        isOngoing = false, isConversation = false, priorityConversation = false, groupChat = false,
        senderHash = null, senderDisplayName = null, sameSenderRecentPings = 0,
        baitHit = true, urgencyHit = false, otpPattern = false,
    )

    private val context = TwinContext(
        state = PhoneState.FOCUS_STEADY, autonomy = Autonomy.AUTO, minutesUntilRelease = 25.0,
        focusMinutesElapsed = 5.0, fatigueRisk = FatigueRisk.MEDIUM, explorationDraw = 0.5,
        alwaysDeliverSenders = emptySet(), alwaysDeliverPackages = emptySet(), demoLearning = false,
    )

    @Test
    fun aRealDecisionSurvivesTheRoundTrip() {
        val decision = RealTwinEngine().apply { loadParams(params) }.decide(like, context)

        assertEquals(decision, DecisionJson.decode(DecisionJson.encode(decision)))
    }

    @Test
    fun quotesNewlinesAndNonAsciiInReasonsSurvive() {
        val decision = RealTwinEngine().apply { loadParams(params) }.decide(like, context)
            .copy(reason = "Mom said \"call me\"\n\\ जल्दी", explanation = "tab\there")

        val back = DecisionJson.decode(DecisionJson.encode(decision))

        assertEquals(decision.reason, back.reason)
        assertEquals(decision.explanation, back.explanation)
    }

    @Test
    fun nonFiniteNumbersComeBackAsNaN() {
        val decision = RealTwinEngine().apply { loadParams(params) }.decide(like, context)
        val broken = decision.copy(card = decision.card.copy(lMean = Double.POSITIVE_INFINITY))

        assertTrue(DecisionJson.decode(DecisionJson.encode(broken)).card.lMean.isNaN())
    }
}
