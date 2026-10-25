package com.pratibimb.app.engine

import com.pratibimb.twin.Action
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.Category
import com.pratibimb.twin.Decision
import com.pratibimb.twin.DecisionRule
import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.FeedbackEvent
import com.pratibimb.twin.FutureCard
import com.pratibimb.twin.NotificationFeatures
import com.pratibimb.twin.PhoneState
import com.pratibimb.twin.TwinContext
import com.pratibimb.twin.TwinEngine
import com.pratibimb.twin.WorthLabelKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScenarioDisplay(
    val app: String,
    val title: String,
    val text: String,
)

data class MockScenario(
    val id: String,
    val display: ScenarioDisplay,
    val features: NotificationFeatures,
    val context: TwinContext,
    val decision: Decision,
)

class StubEngine : TwinEngine {

    private val _positiveFeedbackCount = MutableStateFlow(0)
    val positiveFeedbackCount: StateFlow<Int> = _positiveFeedbackCount.asStateFlow()

    private val _negativeFeedbackCount = MutableStateFlow(0)
    val negativeFeedbackCount: StateFlow<Int> = _negativeFeedbackCount.asStateFlow()

    private var activeParams: String = "twin_params_default.json"

    override fun loadParams(paramsJson: String) {
        activeParams = paramsJson
    }

    override fun decide(features: NotificationFeatures, context: TwinContext): Decision {
        // Find matching scenario by key or package name
        val match = SCENARIOS.firstOrNull { it.features.key == features.key }
            ?: SCENARIOS.firstOrNull { it.features.packageName == features.packageName }
            ?: SCENARIOS.first()

        // Apply feedback offset if worth labels were provided
        val feedbackDelta = (_negativeFeedbackCount.value * 0.1) - (_positiveFeedbackCount.value * 0.05)
        val card = match.decision.card
        val adjustedCard = card.copy(
            hP10 = maxOf(0.0, card.hP10 + feedbackDelta),
            hP90 = maxOf(0.1, card.hP90 + feedbackDelta),
            holdBetterPerHundred = (card.holdBetterPerHundred - (_positiveFeedbackCount.value * 2) + (_negativeFeedbackCount.value * 2)).coerceIn(10, 99)
        )

        return match.decision.copy(card = adjustedCard)
    }

    override fun applyFeedback(event: FeedbackEvent) {
        when (event) {
            is FeedbackEvent.WorthLabel -> {
                if (event.label.positive) {
                    _positiveFeedbackCount.value += 1
                } else {
                    _negativeFeedbackCount.value += 1
                }
            }
            is FeedbackEvent.PullOutcome -> {
                if (event.opened) {
                    _negativeFeedbackCount.value += 1
                } else {
                    _positiveFeedbackCount.value += 1
                }
            }
        }
    }

    override fun exportCounts(): String {
        return """{"positive":${_positiveFeedbackCount.value},"negative":${_negativeFeedbackCount.value}}"""
    }

    override fun importCounts(countsJson: String) {
        // Stub parse
    }

    override fun resetCounts() {
        _positiveFeedbackCount.value = 0
        _negativeFeedbackCount.value = 0
    }

    companion object {
        // The 10 canonical scenarios from sample_decisions.json
        private val CANONICAL: List<MockScenario> = listOf(
            MockScenario(
                id = "instagram_like_steady",
                display = ScenarioDisplay(
                    app = "Instagram",
                    title = "rohan_k",
                    text = "Rohan liked your reel"
                ),
                features = NotificationFeatures(
                    key = "n01",
                    postedAtMillis = 1792918800000L,
                    packageName = "com.instagram.android",
                    category = Category.SOCIAL,
                    androidCategory = null,
                    channelImportance = 3,
                    isOngoing = false,
                    isConversation = false,
                    priorityConversation = false,
                    groupChat = false,
                    senderHash = null,
                    senderDisplayName = null,
                    sameSenderRecentPings = 0,
                    baitHit = true,
                    urgencyHit = false,
                    otpPattern = false
                ),
                context = TwinContext(
                    state = PhoneState.FOCUS_STEADY,
                    autonomy = Autonomy.AUTO,
                    minutesUntilRelease = 16.0,
                    focusMinutesElapsed = 34.0,
                    fatigueRisk = FatigueRisk.LOW,
                    explorationDraw = 0.5,
                    alwaysDeliverSenders = emptySet(),
                    alwaysDeliverPackages = emptySet(),
                    demoLearning = false
                ),
                decision = Decision(
                    action = Action.HOLD,
                    rule = DecisionRule.HOLD_BETTER,
                    wouldBeAction = Action.HOLD,
                    suggestOnly = false,
                    explored = false,
                    propensity = 0.9,
                    reason = "Held: high pull, low worth",
                    explanation = "Held: you're 34 min into steady focus. Instagram likes usually pull you in for 3-22 min. Back at your break in 16 min.",
                    card = FutureCard(
                        pullMean = 0.28,
                        pullLo = 0.17,
                        pullHi = 0.41,
                        minsIfOpenedLo = 3.1,
                        minsIfOpenedHi = 22.0,
                        lP10 = 0.9,
                        lP50 = 2.6,
                        lP90 = 7.4,
                        lMean = 2.6,
                        hP10 = 0.0,
                        hP50 = 0.1,
                        hP90 = 1.0,
                        hMean = 0.1,
                        qMean = 0.12,
                        qLo = 0.05,
                        qHi = 0.22,
                        qUpper80 = 0.19,
                        holdBetterPerHundred = 82,
                        netMinutesProtected = 2.5,
                        minutesUntilRelease = 16.0,
                        lowConfidence = false,
                        fatigueRisk = FatigueRisk.LOW
                    )
                )
            ),
            MockScenario(
                id = "instagram_like_restless",
                display = ScenarioDisplay(
                    app = "Instagram",
                    title = "priya_s",
                    text = "Priya sent you a reel"
                ),
                features = NotificationFeatures(
                    key = "n02",
                    postedAtMillis = 1792919100000L,
                    packageName = "com.instagram.android",
                    category = Category.SOCIAL,
                    androidCategory = null,
                    channelImportance = 3,
                    isOngoing = false,
                    isConversation = false,
                    priorityConversation = false,
                    groupChat = false,
                    senderHash = null,
                    senderDisplayName = null,
                    sameSenderRecentPings = 0,
                    baitHit = true,
                    urgencyHit = false,
                    otpPattern = false
                ),
                context = TwinContext(
                    state = PhoneState.FOCUS_RESTLESS,
                    autonomy = Autonomy.AUTO,
                    minutesUntilRelease = 20.0,
                    focusMinutesElapsed = 10.0,
                    fatigueRisk = FatigueRisk.LOW,
                    explorationDraw = 0.5,
                    alwaysDeliverSenders = emptySet(),
                    alwaysDeliverPackages = emptySet(),
                    demoLearning = false
                ),
                decision = Decision(
                    action = Action.HOLD,
                    rule = DecisionRule.HOLD_BETTER,
                    wouldBeAction = Action.HOLD,
                    suggestOnly = false,
                    explored = false,
                    propensity = 0.9,
                    reason = "Held: restless state, pull risk elevated",
                    explanation = "Held: you've switched apps 4 times in 10 min. Social media reels pull for 8-35 min when restless.",
                    card = FutureCard(
                        pullMean = 0.44,
                        pullLo = 0.28,
                        pullHi = 0.62,
                        minsIfOpenedLo = 8.0,
                        minsIfOpenedHi = 35.0,
                        lP10 = 2.2,
                        lP50 = 5.8,
                        lP90 = 14.0,
                        lMean = 6.1,
                        hP10 = 0.0,
                        hP50 = 0.1,
                        hP90 = 0.8,
                        hMean = 0.2,
                        qMean = 0.10,
                        qLo = 0.04,
                        qHi = 0.18,
                        qUpper80 = 0.15,
                        holdBetterPerHundred = 94,
                        netMinutesProtected = 5.9,
                        minutesUntilRelease = 20.0,
                        lowConfidence = false,
                        fatigueRisk = FatigueRisk.LOW
                    )
                )
            ),
            MockScenario(
                id = "whatsapp_group_chat",
                display = ScenarioDisplay(
                    app = "WhatsApp",
                    title = "Batch 2026 Updates",
                    text = "Aman: Does anyone have the slides for module 3?"
                ),
                features = NotificationFeatures(
                    key = "n03",
                    postedAtMillis = 1792919400000L,
                    packageName = "com.whatsapp",
                    category = Category.MESSAGING,
                    androidCategory = "msg",
                    channelImportance = 4,
                    isOngoing = false,
                    isConversation = true,
                    priorityConversation = false,
                    groupChat = true,
                    senderHash = "hash_aman",
                    senderDisplayName = "Aman",
                    sameSenderRecentPings = 0,
                    baitHit = false,
                    urgencyHit = false,
                    otpPattern = false
                ),
                context = TwinContext(
                    state = PhoneState.FOCUS_STEADY,
                    autonomy = Autonomy.AUTO,
                    minutesUntilRelease = 25.0,
                    focusMinutesElapsed = 25.0,
                    fatigueRisk = FatigueRisk.LOW,
                    explorationDraw = 0.3,
                    alwaysDeliverSenders = emptySet(),
                    alwaysDeliverPackages = emptySet(),
                    demoLearning = false
                ),
                decision = Decision(
                    action = Action.DELIVER,
                    rule = DecisionRule.MIGHT_MATTER,
                    wouldBeAction = Action.DELIVER,
                    suggestOnly = false,
                    explored = false,
                    propensity = 0.85,
                    reason = "Delivered: group query might matter",
                    explanation = "Delivered: academic group discussion. Might contain timely course info.",
                    card = FutureCard(
                        pullMean = 0.20,
                        pullLo = 0.10,
                        pullHi = 0.32,
                        minsIfOpenedLo = 1.0,
                        minsIfOpenedHi = 6.0,
                        lP10 = 0.3,
                        lP50 = 1.1,
                        lP90 = 3.2,
                        lMean = 1.2,
                        hP10 = 0.8,
                        hP50 = 2.4,
                        hP90 = 6.0,
                        hMean = 2.5,
                        qMean = 0.45,
                        qLo = 0.30,
                        qHi = 0.65,
                        qUpper80 = 0.58,
                        holdBetterPerHundred = 38,
                        netMinutesProtected = -1.3,
                        minutesUntilRelease = 25.0,
                        lowConfidence = true,
                        fatigueRisk = FatigueRisk.LOW
                    )
                )
            ),
            MockScenario(
                id = "mom_urgent_first",
                display = ScenarioDisplay(
                    app = "WhatsApp",
                    title = "Mom",
                    text = "Are you home yet?"
                ),
                features = NotificationFeatures(
                    key = "n04",
                    postedAtMillis = 1792919700000L,
                    packageName = "com.whatsapp",
                    category = Category.MESSAGING,
                    androidCategory = "msg",
                    channelImportance = 4,
                    isOngoing = false,
                    isConversation = true,
                    priorityConversation = true,
                    groupChat = false,
                    senderHash = "hash_mom",
                    senderDisplayName = "Mom",
                    sameSenderRecentPings = 0,
                    baitHit = false,
                    urgencyHit = false,
                    otpPattern = false
                ),
                context = TwinContext(
                    state = PhoneState.FOCUS_STEADY,
                    autonomy = Autonomy.AUTO,
                    minutesUntilRelease = 30.0,
                    focusMinutesElapsed = 20.0,
                    fatigueRisk = FatigueRisk.LOW,
                    explorationDraw = 0.1,
                    alwaysDeliverSenders = emptySet(),
                    alwaysDeliverPackages = emptySet(),
                    demoLearning = false
                ),
                decision = Decision(
                    action = Action.DELIVER,
                    rule = DecisionRule.MIGHT_MATTER,
                    wouldBeAction = Action.DELIVER,
                    suggestOnly = false,
                    explored = false,
                    propensity = 0.95,
                    reason = "Delivered: priority family contact",
                    explanation = "Delivered: direct message from family contact. Holding cost exceeds focus cost.",
                    card = FutureCard(
                        pullMean = 0.35,
                        pullLo = 0.22,
                        pullHi = 0.50,
                        minsIfOpenedLo = 1.0,
                        minsIfOpenedHi = 4.0,
                        lP10 = 0.5,
                        lP50 = 1.4,
                        lP90 = 3.0,
                        lMean = 1.5,
                        hP10 = 2.0,
                        hP50 = 5.0,
                        hP90 = 12.0,
                        hMean = 5.5,
                        qMean = 0.85,
                        qLo = 0.70,
                        qHi = 0.98,
                        qUpper80 = 0.92,
                        holdBetterPerHundred = 12,
                        netMinutesProtected = -4.0,
                        minutesUntilRelease = 30.0,
                        lowConfidence = false,
                        fatigueRisk = FatigueRisk.LOW
                    )
                )
            ),
            MockScenario(
                id = "mom_urgent_repeat",
                display = ScenarioDisplay(
                    app = "WhatsApp",
                    title = "Mom",
                    text = "Please call back when you see this"
                ),
                features = NotificationFeatures(
                    key = "n05",
                    postedAtMillis = 1792919820000L,
                    packageName = "com.whatsapp",
                    category = Category.MESSAGING,
                    androidCategory = "msg",
                    channelImportance = 4,
                    isOngoing = false,
                    isConversation = true,
                    priorityConversation = true,
                    groupChat = false,
                    senderHash = "hash_mom",
                    senderDisplayName = "Mom",
                    sameSenderRecentPings = 2,
                    baitHit = false,
                    urgencyHit = true,
                    otpPattern = false
                ),
                context = TwinContext(
                    state = PhoneState.FOCUS_STEADY,
                    autonomy = Autonomy.AUTO,
                    minutesUntilRelease = 28.0,
                    focusMinutesElapsed = 22.0,
                    fatigueRisk = FatigueRisk.LOW,
                    explorationDraw = 0.0,
                    alwaysDeliverSenders = emptySet(),
                    alwaysDeliverPackages = emptySet(),
                    demoLearning = false
                ),
                decision = Decision(
                    action = Action.DELIVER,
                    rule = DecisionRule.OVERRIDE,
                    wouldBeAction = Action.DELIVER,
                    suggestOnly = false,
                    explored = false,
                    propensity = 1.0,
                    reason = "Delivered: safety override (repeated ping from Mom)",
                    explanation = "Delivered: 2 pings in 2 minutes from Mom. Never-hold safety override triggered.",
                    card = FutureCard(
                        pullMean = 0.60,
                        pullLo = 0.45,
                        pullHi = 0.80,
                        minsIfOpenedLo = 2.0,
                        minsIfOpenedHi = 5.0,
                        lP10 = 1.0,
                        lP50 = 2.0,
                        lP90 = 4.0,
                        lMean = 2.1,
                        hP10 = 8.0,
                        hP50 = 18.0,
                        hP90 = 35.0,
                        hMean = 19.0,
                        qMean = 0.98,
                        qLo = 0.90,
                        qHi = 1.0,
                        qUpper80 = 0.99,
                        holdBetterPerHundred = 1,
                        netMinutesProtected = -16.9,
                        minutesUntilRelease = 28.0,
                        lowConfidence = false,
                        fatigueRisk = FatigueRisk.LOW
                    )
                )
            ),
            MockScenario(
                id = "sms_otp",
                display = ScenarioDisplay(
                    app = "Messages",
                    title = "HDFC Bank",
                    text = "Your OTP for transaction of Rs. 450.00 is 839201. Valid for 10 mins."
                ),
                features = NotificationFeatures(
                    key = "n06",
                    postedAtMillis = 1792920000000L,
                    packageName = "com.google.android.apps.messaging",
                    category = Category.MESSAGING,
                    androidCategory = "msg",
                    channelImportance = 4,
                    isOngoing = false,
                    isConversation = false,
                    priorityConversation = false,
                    groupChat = false,
                    senderHash = "hdfc_bank",
                    senderDisplayName = "HDFC Bank",
                    sameSenderRecentPings = 0,
                    baitHit = false,
                    urgencyHit = true,
                    otpPattern = true
                ),
                context = TwinContext(
                    state = PhoneState.FOCUS_STEADY,
                    autonomy = Autonomy.AUTO,
                    minutesUntilRelease = 20.0,
                    focusMinutesElapsed = 30.0,
                    fatigueRisk = FatigueRisk.LOW,
                    explorationDraw = 0.0,
                    alwaysDeliverSenders = emptySet(),
                    alwaysDeliverPackages = emptySet(),
                    demoLearning = false
                ),
                decision = Decision(
                    action = Action.DELIVER,
                    rule = DecisionRule.OVERRIDE,
                    wouldBeAction = Action.DELIVER,
                    suggestOnly = false,
                    explored = false,
                    propensity = 1.0,
                    reason = "Delivered: safety override (SMS OTP)",
                    explanation = "Delivered: SMS OTP from banking sender. Hard safety rule delivers immediately.",
                    card = FutureCard(
                        pullMean = 0.90,
                        pullLo = 0.80,
                        pullHi = 0.98,
                        minsIfOpenedLo = 0.5,
                        minsIfOpenedHi = 1.5,
                        lP10 = 0.3,
                        lP50 = 0.8,
                        lP90 = 1.5,
                        lMean = 0.9,
                        hP10 = 10.0,
                        hP50 = 25.0,
                        hP90 = 50.0,
                        hMean = 26.0,
                        qMean = 1.0,
                        qLo = 0.98,
                        qHi = 1.0,
                        qUpper80 = 1.0,
                        holdBetterPerHundred = 0,
                        netMinutesProtected = -25.1,
                        minutesUntilRelease = 20.0,
                        lowConfidence = false,
                        fatigueRisk = FatigueRisk.LOW
                    )
                )
            ),
            MockScenario(
                id = "otp_bait_shopping",
                display = ScenarioDisplay(
                    app = "Flipkart",
                    title = "FLIPKART DEALS",
                    text = "OTP verified! Flat 70% off flash sale unlocks in 5 mins! Grab now!"
                ),
                features = NotificationFeatures(
                    key = "n07",
                    postedAtMillis = 1792920300000L,
                    packageName = "com.flipkart.android",
                    category = Category.SHOPPING_PROMO,
                    androidCategory = "promo",
                    channelImportance = 3,
                    isOngoing = false,
                    isConversation = false,
                    priorityConversation = false,
                    groupChat = false,
                    senderHash = null,
                    senderDisplayName = null,
                    sameSenderRecentPings = 0,
                    baitHit = true,
                    urgencyHit = true,
                    otpPattern = true
                ),
                context = TwinContext(
                    state = PhoneState.FOCUS_STEADY,
                    autonomy = Autonomy.AUTO,
                    minutesUntilRelease = 15.0,
                    focusMinutesElapsed = 35.0,
                    fatigueRisk = FatigueRisk.LOW,
                    explorationDraw = 0.4,
                    alwaysDeliverSenders = emptySet(),
                    alwaysDeliverPackages = emptySet(),
                    demoLearning = false
                ),
                decision = Decision(
                    action = Action.HOLD,
                    rule = DecisionRule.HOLD_BETTER,
                    wouldBeAction = Action.HOLD,
                    suggestOnly = false,
                    explored = false,
                    propensity = 0.95,
                    reason = "Held: shopping promo with OTP bait",
                    explanation = "Held: OTP keywords used in shopping promo. Not a banking or messaging source.",
                    card = FutureCard(
                        pullMean = 0.35,
                        pullLo = 0.20,
                        pullHi = 0.52,
                        minsIfOpenedLo = 5.0,
                        minsIfOpenedHi = 28.0,
                        lP10 = 1.5,
                        lP50 = 4.8,
                        lP90 = 12.0,
                        lMean = 5.2,
                        hP10 = 0.0,
                        hP50 = 0.0,
                        hP90 = 0.2,
                        hMean = 0.05,
                        qMean = 0.03,
                        qLo = 0.0,
                        qHi = 0.08,
                        qUpper80 = 0.05,
                        holdBetterPerHundred = 98,
                        netMinutesProtected = 5.15,
                        minutesUntilRelease = 15.0,
                        lowConfidence = false,
                        fatigueRisk = FatigueRisk.LOW
                    )
                )
            ),
            MockScenario(
                id = "promo_burst_third",
                display = ScenarioDisplay(
                    app = "Swiggy",
                    title = "Hungry?",
                    text = "30% off your favourite Biryani! Order before lunch ends."
                ),
                features = NotificationFeatures(
                    key = "n08",
                    postedAtMillis = 1792920600000L,
                    packageName = "in.swiggy.android",
                    category = Category.SHOPPING_PROMO,
                    androidCategory = "promo",
                    channelImportance = 3,
                    isOngoing = false,
                    isConversation = false,
                    priorityConversation = false,
                    groupChat = false,
                    senderHash = "swiggy",
                    senderDisplayName = "Swiggy",
                    sameSenderRecentPings = 2,
                    baitHit = true,
                    urgencyHit = false,
                    otpPattern = false
                ),
                context = TwinContext(
                    state = PhoneState.FOCUS_STEADY,
                    autonomy = Autonomy.AUTO,
                    minutesUntilRelease = 10.0,
                    focusMinutesElapsed = 40.0,
                    fatigueRisk = FatigueRisk.LOW,
                    explorationDraw = 0.7,
                    alwaysDeliverSenders = emptySet(),
                    alwaysDeliverPackages = emptySet(),
                    demoLearning = false
                ),
                decision = Decision(
                    action = Action.HOLD,
                    rule = DecisionRule.HOLD_BETTER,
                    wouldBeAction = Action.HOLD,
                    suggestOnly = false,
                    explored = false,
                    propensity = 0.98,
                    reason = "Held: promo burst (3rd ping)",
                    explanation = "Held: 3rd promo in 10 minutes. Repeated promotional alerts are held until break.",
                    card = FutureCard(
                        pullMean = 0.30,
                        pullLo = 0.15,
                        pullHi = 0.48,
                        minsIfOpenedLo = 4.0,
                        minsIfOpenedHi = 18.0,
                        lP10 = 1.0,
                        lP50 = 3.5,
                        lP90 = 9.0,
                        lMean = 3.8,
                        hP10 = 0.0,
                        hP50 = 0.0,
                        hP90 = 0.3,
                        hMean = 0.1,
                        qMean = 0.05,
                        qLo = 0.01,
                        qHi = 0.10,
                        qUpper80 = 0.08,
                        holdBetterPerHundred = 96,
                        netMinutesProtected = 3.7,
                        minutesUntilRelease = 10.0,
                        lowConfidence = false,
                        fatigueRisk = FatigueRisk.LOW
                    )
                )
            ),
            MockScenario(
                id = "work_email",
                display = ScenarioDisplay(
                    app = "Gmail",
                    title = "Engineering Lead",
                    text = "Cascade demo checkpoint review notes"
                ),
                features = NotificationFeatures(
                    key = "n09",
                    postedAtMillis = 1792920900000L,
                    packageName = "com.google.android.gm",
                    category = Category.WORK_EMAIL,
                    androidCategory = "email",
                    channelImportance = 3,
                    isOngoing = false,
                    isConversation = false,
                    priorityConversation = false,
                    groupChat = false,
                    senderHash = "lead_hash",
                    senderDisplayName = "Lead",
                    sameSenderRecentPings = 0,
                    baitHit = false,
                    urgencyHit = false,
                    otpPattern = false
                ),
                context = TwinContext(
                    state = PhoneState.FOCUS_STEADY,
                    autonomy = Autonomy.AUTO,
                    minutesUntilRelease = 8.0,
                    focusMinutesElapsed = 42.0,
                    fatigueRisk = FatigueRisk.LOW,
                    explorationDraw = 0.4,
                    alwaysDeliverSenders = emptySet(),
                    alwaysDeliverPackages = emptySet(),
                    demoLearning = false
                ),
                decision = Decision(
                    action = Action.DELIVER,
                    rule = DecisionRule.MIGHT_MATTER,
                    wouldBeAction = Action.DELIVER,
                    suggestOnly = false,
                    explored = false,
                    propensity = 0.8,
                    reason = "Delivered: work email with wide range",
                    explanation = "Delivered: work email might matter. Model range is wide, so deliver is safer.",
                    card = FutureCard(
                        pullMean = 0.25,
                        pullLo = 0.12,
                        pullHi = 0.38,
                        minsIfOpenedLo = 2.0,
                        minsIfOpenedHi = 12.0,
                        lP10 = 0.8,
                        lP50 = 2.2,
                        lP90 = 6.0,
                        lMean = 2.4,
                        hP10 = 1.0,
                        hP50 = 3.5,
                        hP90 = 10.0,
                        hMean = 3.8,
                        qMean = 0.60,
                        qLo = 0.40,
                        qHi = 0.80,
                        qUpper80 = 0.72,
                        holdBetterPerHundred = 41,
                        netMinutesProtected = -1.4,
                        minutesUntilRelease = 8.0,
                        lowConfidence = true,
                        fatigueRisk = FatigueRisk.LOW
                    )
                )
            ),
            MockScenario(
                id = "youtube_free_state",
                display = ScenarioDisplay(
                    app = "YouTube",
                    title = "Tech Lead",
                    text = "New video uploaded: System design interview tips"
                ),
                features = NotificationFeatures(
                    key = "n10",
                    postedAtMillis = 1792921200000L,
                    packageName = "com.google.android.youtube",
                    category = Category.SHORT_VIDEO,
                    androidCategory = null,
                    channelImportance = 3,
                    isOngoing = false,
                    isConversation = false,
                    priorityConversation = false,
                    groupChat = false,
                    senderHash = null,
                    senderDisplayName = null,
                    sameSenderRecentPings = 0,
                    baitHit = true,
                    urgencyHit = false,
                    otpPattern = false
                ),
                context = TwinContext(
                    state = PhoneState.FREE,
                    autonomy = Autonomy.AUTO,
                    minutesUntilRelease = 0.0,
                    focusMinutesElapsed = 0.0,
                    fatigueRisk = FatigueRisk.LOW,
                    explorationDraw = 0.5,
                    alwaysDeliverSenders = emptySet(),
                    alwaysDeliverPackages = emptySet(),
                    demoLearning = false
                ),
                decision = Decision(
                    action = Action.DELIVER,
                    rule = DecisionRule.FREE_STATE,
                    wouldBeAction = Action.DELIVER,
                    suggestOnly = false,
                    explored = false,
                    propensity = 1.0,
                    reason = "Delivered: free state (no active session)",
                    explanation = "Delivered: you're not in a focus session. Pratibimb holds notifications only during focus.",
                    card = FutureCard(
                        pullMean = 0.35,
                        pullLo = 0.20,
                        pullHi = 0.55,
                        minsIfOpenedLo = 5.0,
                        minsIfOpenedHi = 30.0,
                        lP10 = 0.0,
                        lP50 = 0.0,
                        lP90 = 0.0,
                        lMean = 0.0,
                        hP10 = 0.0,
                        hP50 = 0.0,
                        hP90 = 0.0,
                        hMean = 0.0,
                        qMean = 0.20,
                        qLo = 0.10,
                        qHi = 0.35,
                        qUpper80 = 0.28,
                        holdBetterPerHundred = 0,
                        netMinutesProtected = 0.0,
                        minutesUntilRelease = 0.0,
                        lowConfidence = false,
                        fatigueRisk = FatigueRisk.LOW
                    )
                )
            )
        )

        // Plus the placement pair (see DemoScenarios), built from the first one.
        val SCENARIOS: List<MockScenario> = CANONICAL + DemoScenarios.placement(CANONICAL.first())
    }
}
