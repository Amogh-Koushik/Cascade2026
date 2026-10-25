/**
 * TwinEngine.kt — Pratibimb On-Device Prediction Engine
 * Author: Safal | CASCADE '26
 *
 * Pure Kotlin implementation of the Bayesian attention cost model.
 * Zero Android dependencies — load params from twin_params_*.json,
 * run predictions, and update beliefs from observed outcomes.
 *
 * Ported from engine/engine.py (Amogh). Must pass all 50 test vectors
 * in contracts/test_vectors.json within 1e-6 tolerance (1e-3 for exp).
 */

package org.pratibimb.engine

import kotlin.math.*

// ── Data classes ─────────────────────────────────────────────────────────────

data class NotificationContext(
    val category: String,         // e.g. "social", "messaging"
    val focusState: String,       // "FREE", "FOCUS_STEADY", "FOCUS_RESTLESS"
    val isFocusActive: Boolean,
    val dMinutesUntilRelease: Double,  // minutes remaining in focus session
    val features: NotificationFeatures
)

data class NotificationFeatures(
    val isConversation: Boolean,
    val isGroupChat: Boolean,
    val sameSenderRepeats3m: Boolean,
    val baitHit: Boolean,
    val urgencyHit: Boolean,
    val channelPriority: Double = 0.5  // 0.0 low, 0.5 default, 1.0 high
)

data class PullResult(
    val pOpen: Double,             // P(user opens app within 60s)
    val expectedMinutes: Double,   // E[T_lost] if opened
    val lowerBound80: Double,      // 10th percentile
    val upperBound80: Double       // 90th percentile
)

data class WorthResult(
    val qWorth: Double,            // P(notification genuinely matters) in [0,1]
    val worthScore: Double         // raw logit before sigmoid
)

data class MonteCarloResult(
    val lRangeLow: Double,         // Lower bound expected loss if shown
    val lRangeHigh: Double,        // Upper bound expected loss if shown
    val hRangeLow: Double,         // Lower bound delay penalty if held
    val hRangeHigh: Double,        // Upper bound delay penalty if held
    val piHoldBetter: Double,      // P(holding is better than showing)
    val expectedNetGain: Double    // E[L] - E[H]
)

enum class Decision { DELIVER, HOLD }

data class TwinPrediction(
    val pull: PullResult,
    val worth: WorthResult,
    val monteCarlo: MonteCarloResult,
    val decision: Decision,
    val decisionReason: String,
    val confidence: String         // "LOW", "MEDIUM", "HIGH"
)

// ── Parameter containers ──────────────────────────────────────────────────────

data class CategoryParams(
    val rC: Double,       // pull multiplier for this category
    val muC0: Double,     // lognormal mean (log-minutes)
    val sigmaC: Double    // lognormal std dev
)

data class StateParams(
    val rS: Double,       // open-probability multiplier
    val deltaS: Double    // duration shift in log-space
)

data class PullParams(
    val mG: Double,                            // global base open rate
    val nG: Int,                               // pseudo-observations for global
    val category: Map<String, CategoryParams>,
    val state: Map<String, StateParams>,
    val priorWeights: PriorWeights
)

data class PriorWeights(
    val category: Int,
    val cellBase: Int,
    val cellPool: Double,
    val cellPoolCap: Int,
    val muCategory: Int,
    val muCell: Int
)

data class WorthParams(
    val bias: Double,
    val weights: Map<String, Double>
)

data class PolicyParams(
    val lambda: Double,   // cost weight balancing L vs H
    val w: Double,        // delay penalty weight
    val piStar: Double,   // threshold: P(hold better) > piStar -> HOLD
    val epsilon: Double,  // exploration rate for learning
    val capMin: Double,   // max hold duration in minutes
    val cGlance: Double   // cost of a glance (min) when not opened
)

data class EngineParams(
    val pull: PullParams,
    val worth: WorthParams,
    val policy: PolicyParams,
    val drawBank: List<Triple<Double, Double, Double>>,
    val neverHoldCategories: List<String>
)

// ── TwinEngine ────────────────────────────────────────────────────────────────

class TwinEngine(private val params: EngineParams) {

    /**
     * Main entry: given a notification context, returns a full prediction.
     */
    fun predict(ctx: NotificationContext): TwinPrediction {
        // Safety: never hold urgent categories regardless of model
        if (ctx.category in params.neverHoldCategories) {
            val pull  = computePull(ctx)
            val worth = computeWorth(ctx.features)
            val mc    = runMonteCarlo(pull, worth, ctx.dMinutesUntilRelease)
            return TwinPrediction(
                pull = pull, worth = worth, monteCarlo = mc,
                decision = Decision.DELIVER,
                decisionReason = "NEVER_HOLD_CATEGORY",
                confidence = "HIGH"
            )
        }

        val pull  = computePull(ctx)
        val worth = computeWorth(ctx.features)
        val mc    = runMonteCarlo(pull, worth, ctx.dMinutesUntilRelease)
        val conf  = confidenceLevel(pull)
        val dec   = decide(pull, worth, mc, ctx.isFocusActive, conf)

        return TwinPrediction(
            pull = pull, worth = worth, monteCarlo = mc,
            decision = dec.first, decisionReason = dec.second,
            confidence = conf
        )
    }

    // ── Module A: PULL ─────────────────────────────────────────────────────

    fun computePull(ctx: NotificationContext): PullResult {
        val cp = params.pull.category[ctx.category]
            ?: CategoryParams(1.0, 1.5, 0.7)  // safe fallback
        val sp = params.pull.state[ctx.focusState]
            ?: StateParams(1.0, 0.0)

        // Hierarchical open probability
        val pOpen = (params.pull.mG * cp.rC * sp.rS).coerceIn(0.01, 0.95)

        // Expected duration (lognormal)
        val muAdj   = cp.muC0 + sp.deltaS
        val sigSq   = cp.sigmaC * cp.sigmaC
        val expMins = exp(muAdj + sigSq / 2.0)

        // 80% prediction interval
        val lo = exp(muAdj - 1.2816 * cp.sigmaC)
        val hi = exp(muAdj + 1.2816 * cp.sigmaC)

        return PullResult(pOpen, expMins, lo, hi)
    }

    // ── Module B: WORTH ────────────────────────────────────────────────────

    fun computeWorth(features: NotificationFeatures): WorthResult {
        val w = params.worth.weights
        var logit = params.worth.bias

        if (features.isConversation)       logit += w["is_conversation"]          ?: 0.0
        if (!features.isGroupChat && features.isConversation)
                                           logit += w["priority_conversation"]     ?: 0.0
        if (features.isGroupChat)          logit += w["group_chat"]               ?: 0.0
        if (features.sameSenderRepeats3m)  logit += w["same_sender_repeats_3m"]   ?: 0.0
        if (features.baitHit)              logit += w["bait_hit"]                 ?: 0.0
        if (features.urgencyHit && features.isConversation)
                                           logit += w["urgency_hit_in_conversation"] ?: 0.0

        // Category structural priors
        logit += when {
            features.isConversation -> w["cat_messaging"] ?: 0.0
            features.baitHit        -> w["cat_shopping_promo"] ?: 0.0
            else -> 0.0
        }

        val q = 1.0 / (1.0 + exp(-logit))  // sigmoid
        return WorthResult(q, logit)
    }

    // ── Module C: MONTE CARLO ──────────────────────────────────────────────

    fun runMonteCarlo(
        pull: PullResult,
        worth: WorthResult,
        dRemain: Double
    ): MonteCarloResult {
        val draws = params.drawBank
        val n     = draws.size
        val p     = params.policy

        var sumL = 0.0; var sumH = 0.0
        var countHoldBetter = 0
        val lSamples = DoubleArray(n)
        val hSamples = DoubleArray(n)

        for (i in draws.indices) {
            val (z1, z2, _) = draws[i]

            // Sample open probability around pull.pOpen
            val pSample = (pull.pOpen + 0.05 * z1).coerceIn(0.01, 0.99)

            // Sample duration from lognormal
            val muEst   = ln(pull.expectedMinutes) - 0.5 * 0.6 * 0.6
            val tSample = exp(muEst + 0.6 * z2).coerceIn(0.1, 60.0)

            // L = attention cost of showing now
            val lSample = if (pSample > 0.5) tSample else p.cGlance

            // H = delay penalty = w * dRemain * P(it matters)
            val hSample = p.w * dRemain * worth.qWorth

            lSamples[i] = lSample
            hSamples[i] = hSample
            sumL += lSample
            sumH += hSample
            if (hSample < lSample) countHoldBetter++
        }

        lSamples.sort(); hSamples.sort()
        val lo10 = (n * 0.10).toInt()
        val hi90 = (n * 0.90).toInt().coerceAtMost(n - 1)

        return MonteCarloResult(
            lRangeLow    = lSamples[lo10],
            lRangeHigh   = lSamples[hi90],
            hRangeLow    = hSamples[lo10],
            hRangeHigh   = hSamples[hi90],
            piHoldBetter = countHoldBetter.toDouble() / n,
            expectedNetGain = (sumL - sumH) / n
        )
    }

    // ── Module D: DECIDE ───────────────────────────────────────────────────

    fun decide(
        pull: PullResult,
        worth: WorthResult,
        mc: MonteCarloResult,
        isFocusActive: Boolean,
        confidence: String
    ): Pair<Decision, String> {
        val p = params.policy

        // Rule 1: No active focus session -> always deliver
        if (!isFocusActive) return Decision.DELIVER to "NO_ACTIVE_FOCUS"

        // Rule 2: Low confidence -> deliver (never hold on a guess)
        if (confidence == "LOW") return Decision.DELIVER to "LOW_CONFIDENCE_DEFAULT_DELIVER"

        // Rule 3: High worth -> deliver (notification genuinely matters)
        if (worth.qWorth > 0.65) return Decision.DELIVER to "HIGH_WORTH_NOTIFICATION"

        // Rule 4: Pi threshold + pull threshold -> HOLD
        if (mc.piHoldBetter > p.piStar && pull.pOpen > 0.30) {
            return Decision.HOLD to "HIGH_DERAILMENT_LOW_WORTH"
        }

        return Decision.DELIVER to "BELOW_HOLD_THRESHOLD"
    }

    // ── Confidence ────────────────────────────────────────────────────────

    private fun confidenceLevel(pull: PullResult): String {
        // Proxy: width of 80% interval relative to expected minutes
        val width = pull.upperBound80 - pull.lowerBound80
        return when {
            width > 20.0 -> "LOW"
            width > 10.0 -> "MEDIUM"
            else         -> "HIGH"
        }
    }

    // ── Online Bayesian Update ────────────────────────────────────────────

    /**
     * Call after observing an outcome to update the Beta parameters.
     * Returns updated params map — persist to DataStore.
     */
    fun updateBeta(
        currentAlpha: Double,
        currentBeta: Double,
        didOpen: Boolean
    ): Pair<Double, Double> {
        return if (didOpen) {
            Pair(currentAlpha + 1.0, currentBeta)
        } else {
            Pair(currentAlpha, currentBeta + 1.0)
        }
    }

    /**
     * Welford's online algorithm for updating lognormal mu estimate.
     */
    fun updateMu(
        currentMu: Double,
        currentN: Int,
        observedMinutes: Double
    ): Double {
        val logT = ln(observedMinutes.coerceAtLeast(0.1))
        return currentMu + (logT - currentMu) / (currentN + 1).toDouble()
    }
}

// ── Companion: JSON param loader (stub — fill from DataStore in Android) ──────

object TwinEngineFactory {
    /**
     * Loads EngineParams from a pre-parsed JSON map.
     * In Android, read twin_params_*.json from Assets and parse with Gson/Moshi.
     * This signature is the contract for Satvik's integration.
     */
    fun fromMap(json: Map<String, Any>): TwinEngine {
        // TODO (Task 4): Parse Amogh's engine.py JSON contract into EngineParams
        // Placeholder that will be implemented after engine.py handoff at 7 PM
        throw NotImplementedError("Implement after Amogh hands engine.py at 7 PM")
    }
}
