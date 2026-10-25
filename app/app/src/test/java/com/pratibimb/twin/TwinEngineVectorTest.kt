package com.pratibimb.twin

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

// Replays every case in contracts/test_vectors.json against RealTwinEngine, following
// CONTRACTS.md section 10, so the phone's engine is held to engine.py's numbers.
// Run: ./gradlew test   (from app/)
@RunWith(Parameterized::class)
class TwinEngineVectorTest(private val id: String, private val vector: Map<String, Any?>) {

    @Test
    fun matchesTheReferenceEngine() {
        val params = obj(parse(PARAMS_TEXT))
        for (patch in list(vector["param_patches"])) {
            val p = obj(patch)
            applyPatch(params, list(p["path"]), p["value"])
        }
        val engine = RealTwinEngine()
        engine.setParamsMap(params)
        for (raw in list(vector["events"])) engine.applyFeedback(event(obj(raw)))

        val decision = engine.decide(
            features(obj(vector["features"])),
            context(obj(vector["context"])),
        )
        val expected = obj(vector["expected"])
        val problems = mutableListOf<String>()
        compare(expected["decision"], decisionMap(decision), "decision", problems)
        compare(expected["counts_after_events"], parse(engine.exportCounts()), "counts", problems)

        assertTrue("$id:\n  " + problems.joinToString("\n  "), problems.isEmpty())
    }

    companion object {
        private const val ABSOLUTE_TOLERANCE = 1e-6
        private const val EXP_TOLERANCE = 1e-3

        // Computed through exp() on minutes, where JVM and CPython math libraries can differ.
        private val EXP_FIELDS = setOf(
            "mins_if_opened_lo", "mins_if_opened_hi", "l_p10", "l_p50", "l_p90", "l_mean",
            "net_minutes_protected",
        )

        // Screen wording only; not part of what the two engines must agree on.
        private val UNCOMPARED = setOf("explanation")

        private val CONTRACTS_DIR: File = findContractsDir()
        private val DOCUMENT: Map<String, Any?> =
            obj(parse(File(CONTRACTS_DIR, "test_vectors.json").readText()))
        private val PARAMS_TEXT: String =
            File(CONTRACTS_DIR, "params/${DOCUMENT["params_file"]}").readText()

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun vectors(): List<Array<Any?>> =
            list(DOCUMENT["vectors"]).map { raw -> obj(raw).let { arrayOf(it["id"], it) } }

        // Gradle runs unit tests from the module folder; walk up until the repo's contracts/.
        private fun findContractsDir(): File {
            var dir: File? = File("").absoluteFile
            while (dir != null) {
                val candidate = File(dir, "contracts/test_vectors.json")
                if (candidate.isFile) return candidate.parentFile
                dir = dir.parentFile
            }
            error("contracts/test_vectors.json not found above ${File("").absolutePath}")
        }

        private fun parse(text: String): Any? = SimpleJsonParser(text).parse()

        @Suppress("UNCHECKED_CAST")
        private fun obj(value: Any?): Map<String, Any?> = value as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        private fun list(value: Any?): List<Any?> = value as List<Any?>

        private fun dbl(value: Any?): Double = (value as Number).toDouble()

        @Suppress("UNCHECKED_CAST")
        private fun applyPatch(params: Map<String, Any?>, path: List<Any?>, value: Any?) {
            var target = params as MutableMap<String, Any?>
            for (key in path.dropLast(1)) target = target[key as String] as MutableMap<String, Any?>
            val last = path.last() as String
            require(last in target) { "patch path $path does not exist in the params" }
            target[last] = value
        }

        private fun features(m: Map<String, Any?>) = NotificationFeatures(
            key = m["key"] as String,
            postedAtMillis = (m["posted_at_millis"] as Number).toLong(),
            packageName = m["package_name"] as String,
            category = Category.fromKey(m["category"] as String?),
            androidCategory = m["android_category"] as String?,
            channelImportance = (m["channel_importance"] as Number).toInt(),
            isOngoing = m["is_ongoing"] as Boolean,
            isConversation = m["is_conversation"] as Boolean,
            priorityConversation = m["priority_conversation"] as Boolean,
            groupChat = m["group_chat"] as Boolean,
            senderHash = m["sender_hash"] as String?,
            senderDisplayName = m["sender_display_name"] as String?,
            sameSenderRecentPings = (m["same_sender_recent_pings"] as Number).toInt(),
            baitHit = m["bait_hit"] as Boolean,
            urgencyHit = m["urgency_hit"] as Boolean,
            otpPattern = m["otp_pattern"] as Boolean,
        )

        private fun context(m: Map<String, Any?>) = TwinContext(
            state = PhoneState.valueOf(m["state"] as String),
            autonomy = Autonomy.valueOf(m["autonomy"] as String),
            minutesUntilRelease = dbl(m["minutes_until_release"]),
            focusMinutesElapsed = dbl(m["focus_minutes_elapsed"]),
            fatigueRisk = FatigueRisk.valueOf(m["fatigue_risk"] as String),
            explorationDraw = dbl(m["exploration_draw"]),
            alwaysDeliverSenders = list(m["always_deliver_senders"]).map { it as String }.toSet(),
            alwaysDeliverPackages = list(m["always_deliver_packages"]).map { it as String }.toSet(),
            demoLearning = m["demo_learning"] as Boolean,
        )

        private fun event(m: Map<String, Any?>): FeedbackEvent = when (m["type"]) {
            "pull_outcome" -> FeedbackEvent.PullOutcome(
                category = Category.fromKey(m["category"] as String?),
                state = PhoneState.valueOf(m["state"] as String),
                opened = m["opened"] as Boolean,
                minutesLost = (m["minutes_lost"] as Number?)?.toDouble(),
                releasedFromTray = m["released_from_tray"] as Boolean,
            )
            "worth_label" -> FeedbackEvent.WorthLabel(
                packageName = m["package_name"] as String,
                senderHash = m["sender_hash"] as String?,
                label = WorthLabelKind.valueOf(m["label"] as String),
            )
            else -> error("unknown feedback event type ${m["type"]}")
        }

        private fun cardMap(c: FutureCard): Map<String, Any?> = linkedMapOf(
            "pull_mean" to c.pullMean, "pull_lo" to c.pullLo, "pull_hi" to c.pullHi,
            "mins_if_opened_lo" to c.minsIfOpenedLo, "mins_if_opened_hi" to c.minsIfOpenedHi,
            "l_p10" to c.lP10, "l_p50" to c.lP50, "l_p90" to c.lP90, "l_mean" to c.lMean,
            "h_p10" to c.hP10, "h_p50" to c.hP50, "h_p90" to c.hP90, "h_mean" to c.hMean,
            "q_mean" to c.qMean, "q_lo" to c.qLo, "q_hi" to c.qHi, "q_upper80" to c.qUpper80,
            "hold_better_per_hundred" to c.holdBetterPerHundred,
            "net_minutes_protected" to c.netMinutesProtected,
            "minutes_until_release" to c.minutesUntilRelease,
            "low_confidence" to c.lowConfidence,
            "fatigue_risk" to c.fatigueRisk.name,
        )

        private fun decisionMap(d: Decision): Map<String, Any?> = linkedMapOf(
            "action" to d.action.name,
            "rule" to d.rule.name,
            "would_be_action" to d.wouldBeAction.name,
            "suggest_only" to d.suggestOnly,
            "explored" to d.explored,
            "propensity" to d.propensity,
            "reason" to d.reason,
            "explanation" to d.explanation,
            "card" to cardMap(d.card),
        )

        // Same rule as engine/vectors.py: exact for text, booleans and whole numbers; a small
        // tolerance for fractional numbers, wider for the exp() fields.
        private fun compare(expected: Any?, actual: Any?, path: String, out: MutableList<String>) {
            val field = path.substringAfterLast('.')
            if (field in UNCOMPARED) return
            when {
                expected is Map<*, *> && actual is Map<*, *> -> {
                    if (expected.keys != actual.keys) {
                        out += "$path: keys differ, expected ${expected.keys}, got ${actual.keys}"
                        return
                    }
                    for (key in expected.keys) compare(expected[key], actual[key], "$path.$key", out)
                }
                expected is Number && actual is Number -> {
                    val tolerance = if (field in EXP_FIELDS) EXP_TOLERANCE else ABSOLUTE_TOLERANCE
                    val whole = expected is Int || expected is Long
                    val e = expected.toDouble()
                    val a = actual.toDouble()
                    if (if (whole) e != a else abs(e - a) > tolerance) {
                        out += "$path: expected $expected, got $actual"
                    }
                }
                expected != actual -> out += "$path: expected $expected, got $actual"
            }
        }
    }
}
