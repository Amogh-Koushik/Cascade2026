package com.pratibimb.twin

import java.util.Locale
import kotlin.math.*

/**
 * RealTwinEngine — The full on-device Kotlin implementation of Pratibimb.
 * Ported directly from engine/engine.py and its submodules (pull.py, worth.py, sampling.py, policy.py, overrides.py, counts.py).
 * 
 * Zero external Android/third-party dependencies. Runs in plain JVM unit tests.
 */
class RealTwinEngine(initialParamsJson: String = "") : TwinEngine {

    private var params: Map<String, Any?> = emptyMap()
    private var counts: Counts = Counts()

    init {
        if (initialParamsJson.isNotBlank()) {
            loadParams(initialParamsJson)
        }
    }

    override fun loadParams(paramsJson: String) {
        val parser = SimpleJsonParser(paramsJson)
        @Suppress("UNCHECKED_CAST")
        params = parser.parse() as Map<String, Any?>
    }

    fun setParamsMap(newParams: Map<String, Any?>) {
        params = newParams
    }

    override fun decide(features: NotificationFeatures, context: TwinContext): Decision {
        if (params.isEmpty()) {
            throw IllegalStateException("TwinEngine: params not loaded. Call loadParams() first.")
        }

        @Suppress("UNCHECKED_CAST")
        val policy = params["policy"] as Map<String, Any?>
        val weights = PriorWeights.from(params, context.demoLearning)

        val pull = estimatePull(params, weights, counts, features.category, context.state)
        val worth = estimateWorth(params, weights, counts, features)

        @Suppress("UNCHECKED_CAST")
        val drawBank = params["draw_bank"] as List<List<Number>>
        val futures = sampleFutures(pull, worth, drawBank, policy, context.minutesUntilRelease)
        val card = buildCard(pull, worth, futures, policy, context.minutesUntilRelease, context.fatigueRisk)

        val override = checkOverrides(features, context, params)
        return decidePolicy(features.category, card, futures, override, context, policy, worth.usedSender)
    }

    override fun applyFeedback(event: FeedbackEvent) {
        @Suppress("UNCHECKED_CAST")
        val policy = params["policy"] as Map<String, Any?>
        counts.applyEvent(event, policy)
    }

    override fun exportCounts(): String = counts.toJson()

    override fun importCounts(countsJson: String) {
        counts = Counts.fromJson(countsJson)
    }

    override fun resetCounts() {
        counts = Counts()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MODEL 1: PULL (pull.py)
    // ─────────────────────────────────────────────────────────────────────────

    data class PullEstimate(
        val pCs: Double,
        val nP: Double,
        val muCs: Double,
        val sigmaPred: Double
    )

    private fun estimatePull(
        params: Map<String, Any?>,
        weights: PriorWeights,
        counts: Counts,
        category: Category,
        state: PhoneState
    ): PullEstimate {
        val catKey = category.key
        val stateKey = state.name

        @Suppress("UNCHECKED_CAST")
        val pullParams = params["pull"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val globalParams = pullParams["global"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val catParamsMap = (pullParams["category"] as Map<String, Any?>)[catKey] as? Map<String, Any?>
            ?: mapOf("r_c" to 1.0, "mu_c0" to 1.5, "sigma_c" to 0.7)
        @Suppress("UNCHECKED_CAST")
        val stateParamsMap = (pullParams["state"] as Map<String, Any?>)[stateKey] as? Map<String, Any?>
            ?: mapOf("r_s" to 1.0, "delta_s" to 0.0)

        val rC = (catParamsMap["r_c"] as Number).toDouble()
        val muC0 = (catParamsMap["mu_c0"] as Number).toDouble()
        val sigmaC = (catParamsMap["sigma_c"] as Number).toDouble()

        val rS = (stateParamsMap["r_s"] as Number).toDouble()
        val deltaS = (stateParamsMap["delta_s"] as Number).toDouble()

        val categoryTally = counts.categoryTally(catKey)
        val cellTally = counts.cellTally(catKey, stateKey)

        val mG = (globalParams["m_g"] as Number).toDouble()
        val pG = (weights.globalN * mG + counts.globalPull.opens) / (weights.globalN + counts.globalPull.shown)

        val aC = clip(pG * rC, 0.01, 0.95)
        val pC = (weights.category * aC + categoryTally.opens) / (weights.category + categoryTally.shown)

        val aCs = clip(pC * rS, 0.01, 0.95)
        val nCs0 = weights.cellBase + weights.cellPool * min(categoryTally.shown.toDouble(), weights.cellPoolCap)
        val pCs = (nCs0 * aCs + cellTally.opens) / (nCs0 + cellTally.shown)
        val nP = nCs0 + cellTally.shown

        val muC = (weights.muCategory * muC0 + categoryTally.sumX) / (weights.muCategory + categoryTally.nMin)
        val nCs = cellTally.nMin.toDouble()
        val muCs = (weights.muCell * (muC + deltaS) + cellTally.sumX) / (weights.muCell + nCs)

        val squaredDeviation = max(
            0.0,
            cellTally.sumX2 - 2.0 * muCs * cellTally.sumX + nCs * muCs * muCs
        )
        val variance = (weights.sigma * sigmaC.pow(2.0) + squaredDeviation) / (weights.sigma + nCs)
        val sigmaPred = sqrt(variance) * sqrt(1.0 + 1.0 / (weights.muCell + nCs))

        return PullEstimate(pCs, nP, muCs, sigmaPred)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MODEL 2: WORTH (worth.py)
    // ─────────────────────────────────────────────────────────────────────────

    data class WorthEstimate(
        val q: Double,
        val nQ: Double,
        val structuralScore: Double,
        val usedSender: Boolean
    )

    private fun worthFeatures(features: NotificationFeatures): Map<String, Int> {
        val cat = features.category
        return mapOf(
            "is_conversation" to if (features.isConversation) 1 else 0,
            "cat_messaging" to if (cat == Category.MESSAGING) 1 else 0,
            "cat_work_email" to if (cat == Category.WORK_EMAIL) 1 else 0,
            "priority_conversation" to if (features.priorityConversation) 1 else 0,
            "group_chat" to if (features.groupChat) 1 else 0,
            "cat_shopping_promo" to if (cat == Category.SHOPPING_PROMO) 1 else 0,
            "cat_social_nonconversation" to if (cat == Category.SOCIAL && !features.isConversation) 1 else 0,
            "cat_short_video" to if (cat == Category.SHORT_VIDEO) 1 else 0,
            "bait_hit" to if (features.baitHit) 1 else 0,
            "urgency_hit_in_conversation" to if (features.urgencyHit && features.isConversation) 1 else 0,
            "same_sender_repeat" to if (features.sameSenderRecentPings >= 1) 1 else 0
        )
    }

    private fun structuralScore(params: Map<String, Any?>, features: NotificationFeatures): Double {
        @Suppress("UNCHECKED_CAST")
        val worthParams = params["worth"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val weights = worthParams["weights"] as Map<String, Number>
        val bias = (worthParams["bias"] as Number).toDouble()

        val x = worthFeatures(features)
        var total = bias
        for ((name, weight) in weights) {
            val featureVal = x[name] ?: 0
            total += weight.toDouble() * featureVal
        }
        return sigmoid(total)
    }

    private fun estimateWorth(
        params: Map<String, Any?>,
        weights: PriorWeights,
        counts: Counts,
        features: NotificationFeatures
    ): WorthEstimate {
        val s = structuralScore(params, features)
        val app = counts.appTally(features.packageName)
        val qApp = (weights.kApp * s + app.positives) / (weights.kApp + app.labels)

        val sender = counts.senderTally(features.senderHash)
        if (features.isConversation && sender.labels > 0) {
            val q = (weights.kSender * qApp + sender.positives) / (weights.kSender + sender.labels)
            return WorthEstimate(q, weights.kSender + sender.labels, s, usedSender = true)
        }

        return WorthEstimate(qApp, weights.kApp + app.labels, s, usedSender = false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAMPLING & FUTURE CARD (sampling.py)
    // ─────────────────────────────────────────────────────────────────────────

    data class SampledFutures(
        val pull: List<Double>,
        val minutesIfOpened: List<Double>,
        val worth: List<Double>,
        val loss: List<Double>,
        val holdCost: List<Double>,
        val holdingWins: Int
    ) {
        val pi: Double get() = holdingWins.toDouble() / loss.size
        val holdBetterPerHundred: Int get() {
            val n = loss.size
            return (200 * holdingWins + n) / (2 * n)
        }
    }

    private fun sampleFutures(
        pull: PullEstimate,
        worth: WorthEstimate,
        draws: List<List<Number>>,
        policy: Map<String, Any?>,
        minutesUntilRelease: Double
    ): SampledFutures {
        val sP = logitSpread(pull.pCs, pull.nP)
        val sQ = logitSpread(worth.q, worth.nQ)
        val logitP = logit(pull.pCs)
        val logitQ = logit(worth.q)

        val cGlance = (policy["c_glance"] as Number).toDouble()
        val w = (policy["w"] as Number).toDouble()
        val lam = (policy["lambda"] as Number).toDouble()

        val pI = ArrayList<Double>(draws.size)
        val mI = ArrayList<Double>(draws.size)
        val qI = ArrayList<Double>(draws.size)
        val loss = ArrayList<Double>(draws.size)
        val holdCost = ArrayList<Double>(draws.size)
        var holdingWins = 0

        for (triple in draws) {
            val z1 = triple[0].toDouble()
            val z2 = triple[1].toDouble()
            val z3 = triple[2].toDouble()

            val p = sigmoid(logitP + sP * z1)
            val m = exp(pull.muCs + pull.sigmaPred * z2)
            val q = sigmoid(logitQ + sQ * z3)

            pI.add(p)
            mI.add(m)
            qI.add(q)

            val l = p * m + (1.0 - p) * cGlance
            val h = q * w * minutesUntilRelease

            loss.add(l)
            holdCost.add(h)

            if (l > lam * h) {
                holdingWins++
            }
        }

        return SampledFutures(pI, mI, qI, loss, holdCost, holdingWins)
    }

    private fun buildCard(
        pull: PullEstimate,
        worth: WorthEstimate,
        futures: SampledFutures,
        policy: Map<String, Any?>,
        minutesUntilRelease: Double,
        fatigueRisk: FatigueRisk
    ): FutureCard {
        val pullLo = percentile(futures.pull, 0.1)
        val pullHi = percentile(futures.pull, 0.9)
        val qLo = percentile(futures.worth, 0.1)
        val qHi = percentile(futures.worth, 0.9)
        val width = (policy["low_confidence_width"] as Number).toDouble()
        val upper80Z = (policy["upper80_z"] as Number).toDouble()

        val lMean = mean(futures.loss)
        val hMean = mean(futures.holdCost)

        return FutureCard(
            pullMean = pull.pCs,
            pullLo = pullLo,
            pullHi = pullHi,
            minsIfOpenedLo = percentile(futures.minutesIfOpened, 0.1),
            minsIfOpenedHi = percentile(futures.minutesIfOpened, 0.9),
            lP10 = percentile(futures.loss, 0.1),
            lP50 = percentile(futures.loss, 0.5),
            lP90 = percentile(futures.loss, 0.9),
            lMean = lMean,
            hP10 = percentile(futures.holdCost, 0.1),
            hP50 = percentile(futures.holdCost, 0.5),
            hP90 = percentile(futures.holdCost, 0.9),
            hMean = hMean,
            qMean = worth.q,
            qLo = qLo,
            qHi = qHi,
            qUpper80 = upper80(worth.q, worth.nQ, upper80Z),
            holdBetterPerHundred = futures.holdBetterPerHundred,
            netMinutesProtected = lMean - hMean,
            minutesUntilRelease = minutesUntilRelease,
            lowConfidence = (pullHi - pullLo) > width || (qHi - qLo) > width,
            fatigueRisk = fatigueRisk
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OVERRIDES & POLICY DECISION (overrides.py, policy.py, explain.py)
    // ─────────────────────────────────────────────────────────────────────────

    data class Override(val reason: String, val explanation: String)

    private fun checkOverrides(
        features: NotificationFeatures,
        context: TwinContext,
        params: Map<String, Any?>
    ): Override? {
        @Suppress("UNCHECKED_CAST")
        val neverHold = params["never_hold"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val policy = params["policy"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val repeatPing = policy["repeat_ping"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val androidCats = (neverHold["android_categories"] as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val packages = (neverHold["packages"] as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val otpPackages = (neverHold["otp_source_packages"] as? List<String>) ?: emptyList()

        if (features.packageName in context.alwaysDeliverPackages) {
            return Override("Always delivered: you chose this app", "Delivered: you always let this app through.")
        }
        if (features.senderHash != null && features.senderHash in context.alwaysDeliverSenders) {
            return Override("Always delivered: you chose this sender", "Delivered: you always let this sender through.")
        }
        if (features.androidCategory != null && features.androidCategory in androidCats) {
            return Override("Always delivered: calls, alarms and navigation", "Delivered: calls, alarms and navigation always come through.")
        }
        if (features.isOngoing) {
            return Override("Always delivered: ongoing activity", "Delivered: something is in progress, like a timer or a live trip.")
        }
        if (features.packageName in packages) {
            return Override("Always delivered: rides, deliveries and maps", "Delivered: rides, deliveries and maps always come through.")
        }
        if (features.otpPattern && features.packageName in otpPackages) {
            return Override("Always delivered: one-time password", "Delivered: looks like a one-time password from a trusted app.")
        }

        val pings = features.sameSenderRecentPings + 1
        val reqCount = (repeatPing["count"] as Number).toInt()
        val windowMin = (repeatPing["window_min"] as Number).toDouble()

        if (features.isConversation && features.senderHash != null && pings >= reqCount) {
            val who = features.senderDisplayName ?: "This sender"
            val times = if (pings == 2) "twice" else "$pings times"
            val winStr = if (windowMin == windowMin.toInt().toDouble()) "${windowMin.toInt()}" else "$windowMin"
            return Override("Always delivered: repeated ping", "Delivered: $who messaged $times in $winStr minutes.")
        }

        return null
    }

    data class Recommendation(
        val action: Action,
        val rule: DecisionRule,
        val reason: String,
        val explanation: String
    )

    private fun recommend(
        category: Category,
        card: FutureCard,
        futures: SampledFutures,
        context: TwinContext,
        policy: Map<String, Any?>,
        usedSenderHistory: Boolean
    ): Recommendation {
        val mightMatterUpper80 = (policy["might_matter_upper80"] as Number).toDouble()
        val piStar = (policy["pi_star"] as Number).toDouble()

        if (card.qUpper80 >= mightMatterUpper80) {
            return Recommendation(
                Action.DELIVER,
                DecisionRule.MIGHT_MATTER,
                "Delivered: might matter",
                explainMightMatter(usedSenderHistory)
            )
        }
        if (futures.pi >= piStar) {
            return Recommendation(
                Action.HOLD,
                DecisionRule.HOLD_BETTER,
                "Held: costs more to show than to hold",
                explainHeld(category, card, context)
            )
        }
        return Recommendation(
            Action.DELIVER,
            DecisionRule.DELIVER_BETTER,
            "Delivered: cheap to show",
            explainCheapToShow(card)
        )
    }

    private fun decidePolicy(
        category: Category,
        card: FutureCard,
        futures: SampledFutures,
        override: Override?,
        context: TwinContext,
        policy: Map<String, Any?>,
        usedSenderHistory: Boolean
    ): Decision {
        if (override != null) {
            return Decision(
                action = Action.DELIVER,
                rule = DecisionRule.OVERRIDE,
                wouldBeAction = Action.DELIVER,
                suggestOnly = false,
                explored = false,
                propensity = 1.0,
                reason = override.reason,
                explanation = override.explanation,
                card = card
            )
        }

        if (context.state == PhoneState.FREE) {
            return Decision(
                action = Action.DELIVER,
                rule = DecisionRule.FREE_STATE,
                wouldBeAction = Action.DELIVER,
                suggestOnly = false,
                explored = false,
                propensity = 1.0,
                reason = "Delivered: no focus session",
                explanation = "Delivered: no focus session is running.",
                card = card
            )
        }

        val rec = recommend(category, card, futures, context, policy, usedSenderHistory)

        if (context.autonomy == Autonomy.OBSERVE) {
            val verb = if (rec.action == Action.HOLD) "held" else "delivered"
            return Decision(
                action = Action.DELIVER,
                rule = DecisionRule.OBSERVE_MODE,
                wouldBeAction = rec.action,
                suggestOnly = false,
                explored = false,
                propensity = 1.0,
                reason = "Observe mode",
                explanation = "Observe mode: delivered. In Auto it would have been $verb.",
                card = card
            )
        }

        if (context.autonomy == Autonomy.SUGGEST) {
            val verb = if (rec.action == Action.HOLD) "Suggest holding" else "Suggest delivering"
            val rest = rec.explanation.substringAfter(": ")
            return Decision(
                action = rec.action,
                rule = rec.rule,
                wouldBeAction = rec.action,
                suggestOnly = true,
                explored = false,
                propensity = 1.0,
                reason = rec.reason,
                explanation = "$verb: $rest",
                card = card
            )
        }

        // AUTO MODE
        val epsilon = (policy["epsilon"] as Number).toDouble()
        val exploreUpper80 = (policy["explore_upper80"] as Number).toDouble()
        val eligible = rec.action == Action.HOLD && card.qUpper80 < exploreUpper80

        if (eligible && context.explorationDraw < epsilon) {
            return Decision(
                action = Action.DELIVER,
                rule = DecisionRule.EXPLORATION,
                wouldBeAction = rec.action,
                suggestOnly = false,
                explored = true,
                propensity = epsilon,
                reason = "Delivered: exploration",
                explanation = "Delivered to keep learning: now and then the twin checks a hold was right.",
                card = card
            )
        }

        return Decision(
            action = rec.action,
            rule = rec.rule,
            wouldBeAction = rec.action,
            suggestOnly = false,
            explored = false,
            propensity = if (eligible) 1.0 - epsilon else 1.0,
            reason = rec.reason,
            explanation = rec.explanation,
            card = card
        )
    }

    private fun explainHeld(category: Category, card: FutureCard, context: TwinContext): String {
        val focus = if (context.state == PhoneState.FOCUS_RESTLESS) "restless" else "steady"
        val lo = wholeMinutes(card.minsIfOpenedLo)
        val hi = wholeMinutes(card.minsIfOpenedHi)
        val elapsed = wholeMinutes(context.focusMinutesElapsed)
        val release = wholeMinutes(context.minutesUntilRelease)
        val catLabel = when (category) {
            Category.SHORT_VIDEO -> "Short videos"
            Category.SOCIAL -> "Social updates"
            Category.MESSAGING -> "Messages like this"
            Category.SHOPPING_PROMO -> "Promos"
            Category.NEWS -> "News alerts"
            Category.WORK_EMAIL -> "Work emails"
            Category.OTHER -> "Alerts from this app"
        }
        return "Held: $elapsed min into $focus focus. $catLabel can pull you in for $lo-$hi min. Back at your break in $release min."
    }

    private fun explainMightMatter(usedSenderHistory: Boolean): String {
        return if (usedSenderHistory) {
            "Delivered: might matter. You have marked this sender before."
        } else {
            "Delivered: might matter, so it is not worth the risk of holding."
        }
    }

    private fun explainCheapToShow(card: FutureCard): String {
        val lStr = String.format(Locale.ROOT, "%.1f", card.lMean)
        return "Delivered: showing it now costs about $lStr focus minutes (estimated)."
    }

    private fun wholeMinutes(m: Double): Int = max(1, floor(m + 0.5).toInt())

    // ─────────────────────────────────────────────────────────────────────────
    // MATH STATS HELPERS (stats.py)
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        const val PROB_EPS = 1e-6

        fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

        fun clampProb(p: Double): Double = min(max(p, PROB_EPS), 1.0 - PROB_EPS)

        fun logit(p: Double): Double {
            val clamped = clampProb(p)
            return ln(clamped / (1.0 - clamped))
        }

        fun clip(x: Double, lo: Double, hi: Double): Double = min(max(x, lo), hi)

        fun logitSpread(p: Double, n: Double): Double {
            val clamped = clampProb(p)
            return 1.0 / sqrt(n * clamped * (1.0 - clamped))
        }

        fun upper80(q: Double, nQ: Double, z: Double): Double {
            return sigmoid(logit(q) + z * logitSpread(q, nQ))
        }

        fun percentile(values: List<Double>, p: Double): Double {
            if (values.isEmpty()) return 0.0
            val ordered = values.sorted()
            val position = (ordered.size - 1) * p
            val lo = floor(position).toInt()
            val hi = min(lo + 1, ordered.size - 1)
            return ordered[lo] + (position - lo) * (ordered[hi] - ordered[lo])
        }

        fun mean(values: List<Double>): Double {
            if (values.isEmpty()) return 0.0
            var sum = 0.0
            for (v in values) sum += v
            return sum / values.size
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PRIOR WEIGHTS (priors.py)
// ─────────────────────────────────────────────────────────────────────────────

data class PriorWeights(
    val globalN: Double,
    val category: Double,
    val cellBase: Double,
    val cellPool: Double,
    val cellPoolCap: Double,
    val muCategory: Double,
    val muCell: Double,
    val sigma: Double,
    val kApp: Double,
    val kSender: Double
) {
    companion object {
        fun from(params: Map<String, Any?>, demoLearning: Boolean): PriorWeights {
            @Suppress("UNCHECKED_CAST")
            val pullParams = params["pull"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val globalParams = pullParams["global"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val worthParams = params["worth"] as Map<String, Any?>

            @Suppress("UNCHECKED_CAST")
            val pullWeights = (pullParams["prior_weights"] as Map<String, Any?>).toMutableMap()
            var kApp = (worthParams["k_app"] as Number).toDouble()
            var kSender = (worthParams["k_sender"] as Number).toDouble()

            if (demoLearning && params.containsKey("demo_learning")) {
                @Suppress("UNCHECKED_CAST")
                val demo = params["demo_learning"] as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val demoPull = demo["prior_weights"] as? Map<String, Any?>
                if (demoPull != null) {
                    pullWeights.putAll(demoPull)
                }
                if (demo.containsKey("k_app")) kApp = (demo["k_app"] as Number).toDouble()
                if (demo.containsKey("k_sender")) kSender = (demo["k_sender"] as Number).toDouble()
            }

            return PriorWeights(
                globalN = (globalParams["n_g"] as Number).toDouble(),
                category = (pullWeights["category"] as Number).toDouble(),
                cellBase = (pullWeights["cell_base"] as Number).toDouble(),
                cellPool = (pullWeights["cell_pool"] as Number).toDouble(),
                cellPoolCap = (pullWeights["cell_pool_cap"] as Number).toDouble(),
                muCategory = (pullWeights["mu_category"] as Number).toDouble(),
                muCell = (pullWeights["mu_cell"] as Number).toDouble(),
                sigma = (pullWeights["sigma"] as Number).toDouble(),
                kApp = kApp,
                kSender = kSender
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COUNTS & TALLIES (counts.py)
// ─────────────────────────────────────────────────────────────────────────────

data class PullTally(
    var shown: Int = 0,
    var opens: Int = 0,
    var nMin: Int = 0,
    var sumX: Double = 0.0,
    var sumX2: Double = 0.0
) {
    fun record(opened: Boolean, x: Double?) {
        shown++
        if (opened && x != null) {
            opens++
            nMin++
            sumX += x
            sumX2 += x * x
        }
    }
}

data class WorthTally(
    var labels: Int = 0,
    var positives: Int = 0
) {
    fun record(positive: Boolean) {
        labels++
        if (positive) positives++
    }
}

class Counts {
    val globalPull = PullTally()
    val category = HashMap<String, PullTally>()
    val cell = HashMap<String, PullTally>()
    val app = HashMap<String, WorthTally>()
    val sender = HashMap<String, WorthTally>()

    fun categoryTally(cat: String): PullTally = category[cat] ?: PullTally()
    fun cellTally(cat: String, state: String): PullTally = cell["$cat|$state"] ?: PullTally()
    fun appTally(pkg: String): WorthTally = app[pkg] ?: WorthTally()
    fun senderTally(hash: String?): WorthTally = if (hash == null) WorthTally() else (sender[hash] ?: WorthTally())

    fun applyEvent(event: FeedbackEvent, policy: Map<String, Any?>) {
        when (event) {
            is FeedbackEvent.PullOutcome -> {
                if (event.opened && event.minutesLost == null) {
                    throw IllegalArgumentException("An opened notification needs minutesLost")
                }
                if (!event.opened && event.minutesLost != null) {
                    throw IllegalArgumentException("minutesLost is only measured for opened notifications")
                }

                val x = event.minutesLost?.let { logMinutes(it, policy) }
                val catKey = event.category.key

                if (event.releasedFromTray) {
                    val key = "$catKey|FREE"
                    cell.getOrPut(key) { PullTally() }.record(event.opened, x)
                    return
                }

                globalPull.record(event.opened, x)
                category.getOrPut(catKey) { PullTally() }.record(event.opened, x)
                cell.getOrPut("$catKey|${event.state.name}") { PullTally() }.record(event.opened, x)
            }
            is FeedbackEvent.WorthLabel -> {
                val positive = event.label.positive
                app.getOrPut(event.packageName) { WorthTally() }.record(positive)
                if (event.senderHash != null) {
                    sender.getOrPut(event.senderHash) { WorthTally() }.record(positive)
                }
            }
        }
    }

    private fun logMinutes(minutes: Double, policy: Map<String, Any?>): Double {
        val floorMin = (policy["minutes_floor_min"] as Number).toDouble()
        val capMin = (policy["minutes_lost_cap_min"] as Number).toDouble()
        val capped = min(max(minutes, floorMin), capMin)
        return ln(capped)
    }

    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"pull\":{")
        sb.append("\"global\":{\"shown\":${globalPull.shown},\"opens\":${globalPull.opens}},")
        sb.append("\"category\":{")
        var first = true
        for ((k, v) in category.toSortedMap()) {
            if (!first) sb.append(",")
            sb.append("\"$k\":{\"shown\":${v.shown},\"opens\":${v.opens},\"n_min\":${v.nMin},\"sum_x\":${v.sumX}}")
            first = false
        }
        sb.append("},\"cell\":{")
        first = true
        for ((k, v) in cell.toSortedMap()) {
            if (!first) sb.append(",")
            sb.append("\"$k\":{\"shown\":${v.shown},\"opens\":${v.opens},\"n_min\":${v.nMin},\"sum_x\":${v.sumX},\"sum_x2\":${v.sumX2}}")
            first = false
        }
        sb.append("}},\"worth\":{")
        sb.append("\"app\":{")
        first = true
        for ((k, v) in app.toSortedMap()) {
            if (!first) sb.append(",")
            sb.append("\"$k\":{\"labels\":${v.labels},\"positives\":${v.positives}}")
            first = false
        }
        sb.append("},\"sender\":{")
        first = true
        for ((k, v) in sender.toSortedMap()) {
            if (!first) sb.append(",")
            sb.append("\"$k\":{\"labels\":${v.labels},\"positives\":${v.positives}}")
            first = false
        }
        sb.append("}}}")
        return sb.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): Counts {
            val counts = Counts()
            if (jsonStr.isBlank()) return counts
            val parser = SimpleJsonParser(jsonStr)
            @Suppress("UNCHECKED_CAST")
            val root = parser.parse() as? Map<String, Any?> ?: return counts

            @Suppress("UNCHECKED_CAST")
            val pull = root["pull"] as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val worth = root["worth"] as? Map<String, Any?>

            if (pull != null) {
                @Suppress("UNCHECKED_CAST")
                val g = pull["global"] as? Map<String, Any?>
                if (g != null) {
                    counts.globalPull.shown = (g["shown"] as? Number)?.toInt() ?: 0
                    counts.globalPull.opens = (g["opens"] as? Number)?.toInt() ?: 0
                }
                @Suppress("UNCHECKED_CAST")
                val cats = pull["category"] as? Map<String, Map<String, Any?>>
                cats?.forEach { (k, v) ->
                    counts.category[k] = PullTally(
                        shown = (v["shown"] as? Number)?.toInt() ?: 0,
                        opens = (v["opens"] as? Number)?.toInt() ?: 0,
                        nMin = (v["n_min"] as? Number)?.toInt() ?: 0,
                        sumX = (v["sum_x"] as? Number)?.toDouble() ?: 0.0
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val cells = pull["cell"] as? Map<String, Map<String, Any?>>
                cells?.forEach { (k, v) ->
                    counts.cell[k] = PullTally(
                        shown = (v["shown"] as? Number)?.toInt() ?: 0,
                        opens = (v["opens"] as? Number)?.toInt() ?: 0,
                        nMin = (v["n_min"] as? Number)?.toInt() ?: 0,
                        sumX = (v["sum_x"] as? Number)?.toDouble() ?: 0.0,
                        sumX2 = (v["sum_x2"] as? Number)?.toDouble() ?: 0.0
                    )
                }
            }

            if (worth != null) {
                @Suppress("UNCHECKED_CAST")
                val apps = worth["app"] as? Map<String, Map<String, Any?>>
                apps?.forEach { (k, v) ->
                    counts.app[k] = WorthTally(
                        labels = (v["labels"] as? Number)?.toInt() ?: 0,
                        positives = (v["positives"] as? Number)?.toInt() ?: 0
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val senders = worth["sender"] as? Map<String, Map<String, Any?>>
                senders?.forEach { (k, v) ->
                    counts.sender[k] = WorthTally(
                        labels = (v["labels"] as? Number)?.toInt() ?: 0,
                        positives = (v["positives"] as? Number)?.toInt() ?: 0
                    )
                }
            }

            return counts
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZERO-DEPENDENCY PURE KOTLIN JSON PARSER
// ─────────────────────────────────────────────────────────────────────────────

class SimpleJsonParser(private val src: String) {
    private var pos = 0

    fun parse(): Any? {
        skipWhitespace()
        if (pos >= src.length) return null
        return parseValue()
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        if (pos >= src.length) return null
        return when (val c = src[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            else -> if (c == '-' || c in '0'..'9') parseNumber() else throw IllegalArgumentException("Unexpected char '$c' at $pos")
        }
    }

    private fun parseObject(): Map<String, Any?> {
        match('{')
        val map = HashMap<String, Any?>()
        skipWhitespace()
        if (pos < src.length && src[pos] == '}') {
            pos++
            return map
        }
        while (pos < src.length) {
            val key = parseString()
            skipWhitespace()
            match(':')
            val value = parseValue()
            map[key] = value
            skipWhitespace()
            if (pos < src.length && src[pos] == '}') {
                pos++
                break
            }
            match(',')
        }
        return map
    }

    private fun parseArray(): List<Any?> {
        match('[')
        val list = ArrayList<Any?>()
        skipWhitespace()
        if (pos < src.length && src[pos] == ']') {
            pos++
            return list
        }
        while (pos < src.length) {
            list.add(parseValue())
            skipWhitespace()
            if (pos < src.length && src[pos] == ']') {
                pos++
                break
            }
            match(',')
        }
        return list
    }

    private fun parseString(): String {
        match('"')
        val sb = StringBuilder()
        while (pos < src.length) {
            val c = src[pos++]
            if (c == '"') return sb.toString()
            if (c == '\\') {
                if (pos >= src.length) break
                val esc = src[pos++]
                when (esc) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        val hex = src.substring(pos, pos + 4)
                        pos += 4
                        sb.append(hex.toInt(16).toChar())
                    }
                    else -> sb.append(esc)
                }
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun parseNumber(): Number {
        val start = pos
        if (pos < src.length && src[pos] == '-') pos++
        while (pos < src.length && src[pos] in '0'..'9') pos++
        var isFloat = false
        if (pos < src.length && src[pos] == '.') {
            isFloat = true
            pos++
            while (pos < src.length && src[pos] in '0'..'9') pos++
        }
        if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
            isFloat = true
            pos++
            if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
            while (pos < src.length && src[pos] in '0'..'9') pos++
        }
        val text = src.substring(start, pos)
        return if (isFloat) text.toDouble() else text.toLong()
    }

    private fun parseBoolean(): Boolean {
        return if (src.startsWith("true", pos)) {
            pos += 4
            true
        } else if (src.startsWith("false", pos)) {
            pos += 5
            false
        } else throw IllegalArgumentException("Invalid boolean at $pos")
    }

    private fun parseNull(): Any? {
        if (src.startsWith("null", pos)) {
            pos += 4
            return null
        }
        throw IllegalArgumentException("Invalid null at $pos")
    }

    private fun skipWhitespace() {
        while (pos < src.length && src[pos] in " \t\r\n") pos++
    }

    private fun match(expected: Char) {
        skipWhitespace()
        if (pos >= src.length || src[pos] != expected) {
            throw IllegalArgumentException("Expected '$expected' at $pos, found '${if (pos < src.length) src[pos] else "EOF"}'")
        }
        pos++
    }
}
