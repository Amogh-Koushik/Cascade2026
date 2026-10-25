package com.pratibimb.app.engine

import com.pratibimb.twin.Action
import com.pratibimb.twin.Decision
import com.pratibimb.twin.DecisionRule
import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.FutureCard
import com.pratibimb.twin.SimpleJsonParser

// A held item's decision as JSON, so the tray can show its Future Card after a restart. Plain
// Kotlin (SimpleJsonParser to read), so the round trip is tested on the JVM.
object DecisionJson {
    fun encode(decision: Decision): String {
        val card = decision.card
        val cardFields = linkedMapOf<String, Any?>(
            "pullMean" to card.pullMean, "pullLo" to card.pullLo, "pullHi" to card.pullHi,
            "minsIfOpenedLo" to card.minsIfOpenedLo, "minsIfOpenedHi" to card.minsIfOpenedHi,
            "lP10" to card.lP10, "lP50" to card.lP50, "lP90" to card.lP90, "lMean" to card.lMean,
            "hP10" to card.hP10, "hP50" to card.hP50, "hP90" to card.hP90, "hMean" to card.hMean,
            "qMean" to card.qMean, "qLo" to card.qLo, "qHi" to card.qHi, "qUpper80" to card.qUpper80,
            "holdBetterPerHundred" to card.holdBetterPerHundred,
            "netMinutesProtected" to card.netMinutesProtected,
            "minutesUntilRelease" to card.minutesUntilRelease,
            "lowConfidence" to card.lowConfidence,
            "fatigueRisk" to card.fatigueRisk.name,
        )
        val fields = linkedMapOf<String, Any?>(
            "action" to decision.action.name,
            "rule" to decision.rule.name,
            "wouldBeAction" to decision.wouldBeAction.name,
            "suggestOnly" to decision.suggestOnly,
            "explored" to decision.explored,
            "propensity" to decision.propensity,
            "reason" to decision.reason,
            "explanation" to decision.explanation,
            "card" to cardFields,
        )
        return JsonText.write(fields)
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(json: String): Decision {
        val fields = SimpleJsonParser(json).parse() as Map<String, Any?>
        val card = fields["card"] as Map<String, Any?>
        fun d(key: String) = (card[key] as Number?)?.toDouble() ?: Double.NaN
        return Decision(
            action = Action.valueOf(fields["action"] as String),
            rule = DecisionRule.valueOf(fields["rule"] as String),
            wouldBeAction = Action.valueOf(fields["wouldBeAction"] as String),
            suggestOnly = fields["suggestOnly"] as Boolean,
            explored = fields["explored"] as Boolean,
            propensity = (fields["propensity"] as Number).toDouble(),
            reason = fields["reason"] as String,
            explanation = fields["explanation"] as String,
            card = FutureCard(
                pullMean = d("pullMean"), pullLo = d("pullLo"), pullHi = d("pullHi"),
                minsIfOpenedLo = d("minsIfOpenedLo"), minsIfOpenedHi = d("minsIfOpenedHi"),
                lP10 = d("lP10"), lP50 = d("lP50"), lP90 = d("lP90"), lMean = d("lMean"),
                hP10 = d("hP10"), hP50 = d("hP50"), hP90 = d("hP90"), hMean = d("hMean"),
                qMean = d("qMean"), qLo = d("qLo"), qHi = d("qHi"), qUpper80 = d("qUpper80"),
                holdBetterPerHundred = (card["holdBetterPerHundred"] as Number).toInt(),
                netMinutesProtected = d("netMinutesProtected"),
                minutesUntilRelease = d("minutesUntilRelease"),
                lowConfidence = card["lowConfidence"] as Boolean,
                fatigueRisk = FatigueRisk.valueOf(card["fatigueRisk"] as String),
            ),
        )
    }
}
