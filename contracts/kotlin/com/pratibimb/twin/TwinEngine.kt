package com.pratibimb.twin

// The seam between the Android app and the twin. The app only talks to this interface, so the
// stub engine (placeholder decisions) and the real engine swap by changing one binding.
// Pure Kotlin with no Android imports, so engines run in plain JVM unit tests.
//
// Every type here has a JSON form with snake_case keys: each property name gets an underscore
// before every uppercase letter and is lowercased (lP10 -> l_p10). Full rules: CONTRACTS.md.

interface TwinEngine {
    // Replaces the parameters (twin_params_<persona>.json). Learned counts are kept, so a
    // persona switch changes the priors, not what was learned about this user.
    fun loadParams(paramsJson: String)

    fun decide(features: NotificationFeatures, context: TwinContext): Decision

    fun applyFeedback(event: FeedbackEvent)

    // Learned counts as JSON, so the app can store them and they survive restarts.
    fun exportCounts(): String

    fun importCounts(countsJson: String)

    fun resetCounts()
}

enum class Category(val key: String) {
    SHORT_VIDEO("short_video"),
    SOCIAL("social"),
    MESSAGING("messaging"),
    SHOPPING_PROMO("shopping_promo"),
    NEWS("news"),
    WORK_EMAIL("work_email"),
    OTHER("other");

    companion object {
        // Apps missing from the params category map, or with an unknown key, count as OTHER.
        fun fromKey(key: String?): Category = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

enum class PhoneState { FREE, FOCUS_STEADY, FOCUS_RESTLESS }

enum class Autonomy { OBSERVE, SUGGEST, AUTO }

enum class Action { DELIVER, HOLD }

enum class FatigueRisk { LOW, MEDIUM, HIGH }

// Which branch of the decision rule produced the action, in the order they are checked.
enum class DecisionRule {
    OVERRIDE,
    FREE_STATE,
    OBSERVE_MODE,
    MIGHT_MATTER,
    HOLD_BETTER,
    DELIVER_BETTER,
    EXPLORATION,
}

enum class WorthLabelKind(val positive: Boolean) {
    WORTH_IT(true),
    RELEASE_NOW(true),
    ALWAYS_DELIVER(true),
    SHOULD_HAVE_COME_THROUGH(true),
    NOT_WORTH_IT(false),
    CORRECT_HOLD(false),
}

// Built by the phone's feature builder from one posted notification.
data class NotificationFeatures(
    val key: String,
    val postedAtMillis: Long,
    val packageName: String,
    val category: Category,
    // Notification.category from Android ("call", "alarm", "navigation", ...), if the app set one.
    val androidCategory: String?,
    val channelImportance: Int,
    val isOngoing: Boolean,
    val isConversation: Boolean,
    val priorityConversation: Boolean,
    val groupChat: Boolean,
    // Salted hash of the sender. Null when the notification has no person behind it.
    val senderHash: String?,
    // Only used to word the explanation ("Mom messaged twice"); never stored.
    val senderDisplayName: String?,
    // Earlier notifications from the same sender within the repeat-ping window, not counting this one.
    val sameSenderRecentPings: Int,
    val baitHit: Boolean,
    val urgencyHit: Boolean,
    val otpPattern: Boolean,
)

data class TwinContext(
    val state: PhoneState,
    val autonomy: Autonomy,
    // D: minutes until a held item would be released, min(remaining session, hold cap).
    val minutesUntilRelease: Double,
    val focusMinutesElapsed: Double,
    // Shown on the card and in explanations only; never used in the hold decision.
    val fatigueRisk: FatigueRisk,
    // u: a fresh Uniform[0, 1) draw from the phone for each decision, logged in the feature
    // row, so exploration is reproducible and test vectors can fix it.
    val explorationDraw: Double,
    val alwaysDeliverSenders: Set<String>,
    val alwaysDeliverPackages: Set<String>,
    // Swaps in the small demo_learning prior weights so a few taps visibly move the ranges.
    val demoLearning: Boolean,
)

data class Decision(
    val action: Action,
    val rule: DecisionRule,
    // What Auto mode would have done. Differs from action in Observe mode.
    val wouldBeAction: Action,
    // Suggest mode: the app shows action as a recommendation and the user decides.
    val suggestOnly: Boolean,
    val explored: Boolean,
    // Probability that the policy takes the logged action (epsilon when explored).
    val propensity: Double,
    val reason: String,
    // Plain-language sentence from a template, under 25 words.
    val explanation: String,
    val card: FutureCard,
)

// Every range is the 10th to 90th percentile over the shared draw bank.
data class FutureCard(
    val pullMean: Double,
    val pullLo: Double,
    val pullHi: Double,
    val minsIfOpenedLo: Double,
    val minsIfOpenedHi: Double,
    // L: focus minutes lost if shown now.
    val lP10: Double,
    val lP50: Double,
    val lP90: Double,
    val lMean: Double,
    // H: cost of holding, the delay penalty if the notification matters.
    val hP10: Double,
    val hP50: Double,
    val hP90: Double,
    val hMean: Double,
    val qMean: Double,
    val qLo: Double,
    val qHi: Double,
    val qUpper80: Double,
    // "In ~N of 100 sampled futures, holding costs less."
    val holdBetterPerHundred: Int,
    // Estimated (model): mean L minus mean H.
    val netMinutesProtected: Double,
    val minutesUntilRelease: Double,
    val lowConfidence: Boolean,
    val fatigueRisk: FatigueRisk,
)

sealed interface FeedbackEvent {
    // A delivered notification once its outcome is final: opened within the open window or not.
    // Items released from the tray at a break are sent with state FREE and releasedFromTray
    // set, which updates only the FREE cell.
    data class PullOutcome(
        val category: Category,
        val state: PhoneState,
        val opened: Boolean,
        // Required when opened: min(t_end - t0, 60) in minutes.
        val minutesLost: Double?,
        val releasedFromTray: Boolean,
    ) : FeedbackEvent

    // An explicit tap. Opening speed and dwell time are never sent as worth labels.
    data class WorthLabel(
        val packageName: String,
        val senderHash: String?,
        val label: WorthLabelKind,
    ) : FeedbackEvent
}
