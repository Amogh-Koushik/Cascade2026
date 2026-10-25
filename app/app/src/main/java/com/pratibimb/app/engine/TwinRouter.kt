package com.pratibimb.app.engine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pratibimb.app.data.FeatureBuilder
import com.pratibimb.app.data.NotificationLog
import com.pratibimb.app.dnd.AlertPing
import com.pratibimb.app.dnd.DigestNotifier
import com.pratibimb.app.service.PratibimbListener
import com.pratibimb.app.ui.HeldTrayItem
import com.pratibimb.app.ui.LiveNotificationItem
import com.pratibimb.twin.Action
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.Category
import com.pratibimb.twin.Decision
import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.FeedbackEvent
import com.pratibimb.twin.PhoneState
import com.pratibimb.twin.RealTwinEngine
import com.pratibimb.twin.TwinContext
import com.pratibimb.twin.WorthLabelKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * TwinRouter acts as the central coordinator between the Android NotificationListenerService,
 * the TwinEngine decision engine, and the Pratibimb UI state.
 */
object TwinRouter {
    // Bundled from contracts/params (see app/app/build.gradle.kts). The persona switcher can
    // load another file later; until then every decision uses the student persona.
    private const val PARAMS_ASSET = "twin_params_student_coder.json"
    private const val COUNTS_PREFS = "twin_counts"
    private const val COUNTS_KEY = "counts_json"

    // The plan's hold cap: nothing waits longer than this before release.
    private const val HOLD_CAP_MIN = 45.0

    val engine = RealTwinEngine()

    // Set once params are loaded; until then incoming notifications are left untouched.
    @Volatile
    private var appContext: Context? = null

    val engineStatus: String
        get() = if (appContext != null) "ready ($PARAMS_ASSET)" else "not loaded"

    @Volatile
    private var sessionStartMillis = 0L

    @Volatile
    private var sessionEndMillis = 0L

    @Volatile
    var activeListener: PratibimbListener? = null

    // ── Observable UI states ───────────────────────────────────────────────
    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    private val _liveNotifications = MutableStateFlow<List<LiveNotificationItem>>(emptyList())
    val liveNotifications: StateFlow<List<LiveNotificationItem>> = _liveNotifications.asStateFlow()

    private val _heldTray = MutableStateFlow<List<HeldTrayItem>>(emptyList())
    val heldTray: StateFlow<List<HeldTrayItem>> = _heldTray.asStateFlow()

    private val _heldTodayCount = MutableStateFlow(0)
    val heldTodayCount: StateFlow<Int> = _heldTodayCount.asStateFlow()

    private val _netMinutesProtected = MutableStateFlow(0.0)
    val netMinutesProtected: StateFlow<Double> = _netMinutesProtected.asStateFlow()

    // ── Engine Context States ──────────────────────────────────────────────
    private val _phoneState = MutableStateFlow(PhoneState.FREE)
    val phoneState: StateFlow<PhoneState> = _phoneState.asStateFlow()

    private val _autonomy = MutableStateFlow(Autonomy.AUTO)
    val autonomy: StateFlow<Autonomy> = _autonomy.asStateFlow()

    private val _fatigueRisk = MutableStateFlow(FatigueRisk.LOW)
    val fatigueRisk: StateFlow<FatigueRisk> = _fatigueRisk.asStateFlow()

    private val _selectedPersona = MutableStateFlow("Deep work (balanced)")
    val selectedPersona: StateFlow<String> = _selectedPersona.asStateFlow()

    // Mode A is only claimed once DND access is confirmed in init().
    private val _isModeA = MutableStateFlow(false)
    val isModeA: StateFlow<Boolean> = _isModeA.asStateFlow()

    // Tracks recent notifications per package for burst detection
    private val recentPackagePings = ConcurrentHashMap<String, Int>()

    // Loads the persona params and the counts learned so far. Both the activity and the
    // listener call this, because after a reboot the listener can start first; only the first
    // successful call does the work.
    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        try {
            val paramsJson = app.assets.open(PARAMS_ASSET).bufferedReader().use { it.readText() }
            engine.loadParams(paramsJson)
        } catch (e: IOException) {
            NotificationLog.add("TwinRouter", "Could not load $PARAMS_ASSET: ${e.message}")
            return
        }
        app.getSharedPreferences(COUNTS_PREFS, Context.MODE_PRIVATE)
            .getString(COUNTS_KEY, null)
            ?.let { engine.importCounts(it) }
        _isModeA.value = app.getSystemService(NotificationManager::class.java)
            .isNotificationPolicyAccessGranted
        appContext = app
        NotificationLog.add("TwinRouter", "Engine ready with $PARAMS_ASSET")
    }

    fun startSession(durationMinutes: Int) {
        val now = System.currentTimeMillis()
        sessionStartMillis = now
        sessionEndMillis = now + durationMinutes * 60_000L
    }

    fun endSession() {
        sessionStartMillis = 0L
        sessionEndMillis = 0L
    }

    // D (minutes until a held item would be released) and time in focus come from the running
    // session, because the engine charges the cost of holding by how long the item will wait.
    private fun currentContext(): TwinContext {
        val now = System.currentTimeMillis()
        val inSession = sessionEndMillis > now
        val remainingMin = if (inSession) (sessionEndMillis - now) / 60_000.0 else 0.0
        val elapsedMin = if (inSession) (now - sessionStartMillis) / 60_000.0 else 0.0
        return TwinContext(
            state = _phoneState.value,
            autonomy = _autonomy.value,
            minutesUntilRelease = min(remainingMin, HOLD_CAP_MIN),
            focusMinutesElapsed = elapsedMin,
            fatigueRisk = _fatigueRisk.value,
            explorationDraw = 0.5,
            alwaysDeliverSenders = emptySet(),
            alwaysDeliverPackages = emptySet(),
            demoLearning = true,
        )
    }

    // Every feedback goes through here, so what the twin learns survives a restart.
    private fun learn(event: FeedbackEvent) {
        engine.applyFeedback(event)
        saveCounts()
    }

    private fun saveCounts() {
        appContext?.getSharedPreferences(COUNTS_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(COUNTS_KEY, engine.exportCounts())
            ?.apply()
    }

    fun onListenerConnected(listener: PratibimbListener) {
        init(listener)
        activeListener = listener
        _isListenerConnected.value = true
        NotificationLog.setListenerConnected(true)
        NotificationLog.add("TwinRouter", "PratibimbListener attached")
    }

    fun onListenerDisconnected() {
        activeListener = null
        _isListenerConnected.value = false
        NotificationLog.setListenerConnected(false)
        NotificationLog.add("TwinRouter", "PratibimbListener detached")
    }

    /**
     * Core thin-slice handler: called whenever Android posts a notification.
     */
    fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
    ) {
        val listener = activeListener ?: return
        if (sbn.packageName == listener.packageName) return
        if (sbn.isOngoing) return
        // Without params the engine cannot decide; leaving the notification alone is the safe choice.
        if (appContext == null) return

        // 1. Build features from live StatusBarNotification
        val features = FeatureBuilder.build(sbn, rankingMap, recentPackagePings)
        recentPackagePings[sbn.packageName] = (recentPackagePings[sbn.packageName] ?: 0) + 1

        // 2. Engine evaluates features against the current focus session
        val decision = engine.decide(features, currentContext())

        // 4. Extract display metadata
        val appLabel = getAppLabel(listener, sbn.packageName)
        val extras = sbn.notification.extras
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val title = if (rawTitle.isNotBlank()) rawTitle else appLabel
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val contentIntent = sbn.notification.contentIntent

        NotificationLog.add(
            source = appLabel,
            message = describeDecision(title, features.category, features.isConversation, decision),
            timeMillis = sbn.postTime,
        )

        // 5. Create Live Feed Item
        val liveItem = LiveNotificationItem(
            id = "live_${sbn.key.hashCode()}_${System.currentTimeMillis()}",
            app = appLabel,
            title = title,
            text = text,
            timeLabel = "Just now",
            decision = decision,
            senderDisplayName = features.senderDisplayName,
            packageName = sbn.packageName,
        )
        _liveNotifications.value = (listOf(liveItem) + _liveNotifications.value).take(60)

        // 6. Action Execution: HOLD vs DELIVER
        if (decision.action == Action.HOLD) {
            // Cancel notification from Android system shade to keep user in focus
            try {
                listener.cancelNotification(sbn.key)
                NotificationLog.add("TwinRouter", "Suppressed notification: ${sbn.key}")
            } catch (e: Exception) {
                NotificationLog.add("TwinRouter", "Failed to cancel notification: ${e.message}")
            }

            val trayItem = HeldTrayItem(
                id = "tray_${sbn.key.hashCode()}_${System.currentTimeMillis()}",
                app = appLabel,
                title = title,
                text = text,
                sender = features.senderDisplayName,
                packageName = sbn.packageName,
                heldReason = decision.reason,
                timeAgo = "Just now",
                minutesUntilRelease = decision.card.minutesUntilRelease.toInt().coerceAtLeast(1),
                decision = decision,
                contentIntent = contentIntent,
            )
            _heldTray.value = listOf(trayItem) + _heldTray.value
            _heldTodayCount.value += 1
            _netMinutesProtected.value += decision.card.netMinutesProtected
        } else {
            // DELIVER: If in Mode A (DND on) and user is in focus, send bypass alert
            if (_isModeA.value && _phoneState.value != PhoneState.FREE) {
                val ping = AlertPing.post(
                    context = listener,
                    title = "[$appLabel] $title",
                    text = text,
                    contentIntent = contentIntent,
                )
                NotificationLog.add("alert", "Ping for $appLabel: ${ping.label}")
            }
        }
    }

    // One readable line per decision for the Debug log: what happened, why, and the two
    // numbers behind it.
    private fun describeDecision(
        title: String,
        category: Category,
        isConversation: Boolean,
        decision: Decision,
    ): String {
        val card = decision.card
        val kind = if (isConversation) "${category.key}, conversation" else category.key
        return "${decision.action.name} \"$title\" [$kind] ${decision.rule.name}: ${decision.reason}" +
            " · hold wins ${card.holdBetterPerHundred}/100 · worth up to %.2f".format(card.qUpper80)
    }

    fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        reason: Int,
    ) {
        if (reason == NotificationListenerService.REASON_CLICK) {
            // User opened notification: feedback signal
            learn(
                FeedbackEvent.PullOutcome(
                    category = Category.OTHER,
                    state = _phoneState.value,
                    opened = true,
                    minutesLost = 5.0,
                    releasedFromTray = false,
                )
            )
            NotificationLog.add("TwinRouter", "Feedback: user opened ${sbn.packageName}")
        }
    }

    // ── User Interactive Controls ──────────────────────────────────────────
    fun setModeA(modeA: Boolean) {
        _isModeA.value = modeA
    }

    fun setPhoneState(state: PhoneState) {
        _phoneState.value = state
    }

    fun setAutonomy(autonomy: Autonomy) {
        _autonomy.value = autonomy
    }

    fun setFatigueRisk(risk: FatigueRisk) {
        _fatigueRisk.value = risk
    }

    fun setPersona(persona: String) {
        _selectedPersona.value = persona
    }

    fun releaseHeldItem(context: Context, trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        DigestNotifier.postReleasedNotification(
            context = context,
            app = item.app,
            title = item.title,
            text = item.text,
            contentIntent = item.contentIntent,
        )
        learn(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = null,
                label = WorthLabelKind.RELEASE_NOW,
            )
        )
        _heldTray.value = _heldTray.value.filterNot { it.id == trayId }
    }

    fun releaseAllDigest(context: Context) {
        val currentItems = _heldTray.value
        if (currentItems.isNotEmpty()) {
            DigestNotifier.postBatchDigest(
                context = context,
                count = currentItems.size,
                summaryLines = currentItems.map { "${it.app}: ${it.title}" },
            )
            _heldTray.value = emptyList()
        }
    }

    fun alwaysDeliverSender(trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        learn(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = null,
                label = WorthLabelKind.ALWAYS_DELIVER,
            )
        )
        _heldTray.value = _heldTray.value.filterNot { it.id == trayId }
    }

    fun markHeldWrong(trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        learn(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = null,
                label = WorthLabelKind.SHOULD_HAVE_COME_THROUGH,
            )
        )
        _heldTray.value = _heldTray.value.filterNot { it.id == trayId }
    }

    fun markHeldCorrect(trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        learn(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = null,
                label = WorthLabelKind.CORRECT_HOLD,
            )
        )
    }

    fun submitWorthFeedback(itemId: String, isWorth: Boolean) {
        val label = if (isWorth) WorthLabelKind.WORTH_IT else WorthLabelKind.NOT_WORTH_IT
        _liveNotifications.value = _liveNotifications.value.map { item ->
            if (item.id == itemId) {
                learn(
                    FeedbackEvent.WorthLabel(
                        packageName = item.packageName,
                        senderHash = null,
                        label = label,
                    )
                )
                item.copy(feedbackLabel = label)
            } else {
                item
            }
        }
    }

    fun triggerScenario(scenario: MockScenario) {
        if (appContext == null) {
            NotificationLog.add("TwinRouter", "Scenario ignored: engine params not loaded yet")
            return
        }
        val currentContext = scenario.context.copy(
            state = _phoneState.value,
            autonomy = _autonomy.value,
            fatigueRisk = _fatigueRisk.value,
        )

        val decision = engine.decide(scenario.features, currentContext)
        NotificationLog.add(
            source = "sample: ${scenario.display.app}",
            message = describeDecision(
                scenario.display.title,
                scenario.features.category,
                scenario.features.isConversation,
                decision,
            ),
        )

        val liveItem = LiveNotificationItem(
            id = "sc_${System.currentTimeMillis()}_${scenario.id}",
            app = scenario.display.app,
            title = scenario.display.title,
            text = scenario.display.text,
            timeLabel = "Just now",
            decision = decision,
            senderDisplayName = scenario.features.senderDisplayName,
            packageName = scenario.features.packageName,
        )

        _liveNotifications.value = listOf(liveItem) + _liveNotifications.value

        if (decision.action == Action.HOLD) {
            _heldTodayCount.value += 1
            _netMinutesProtected.value += decision.card.netMinutesProtected

            val trayItem = HeldTrayItem(
                id = "tray_${System.currentTimeMillis()}_${scenario.id}",
                app = scenario.display.app,
                title = scenario.display.title,
                text = scenario.display.text,
                sender = scenario.features.senderDisplayName,
                packageName = scenario.features.packageName,
                heldReason = decision.reason,
                timeAgo = "Just now",
                minutesUntilRelease = decision.card.minutesUntilRelease.toInt().coerceAtLeast(1),
                decision = decision,
            )
            _heldTray.value = listOf(trayItem) + _heldTray.value
        }
    }

    fun resetAll() {
        engine.resetCounts()
        saveCounts()
        recentPackagePings.clear()
        _heldTodayCount.value = 0
        _netMinutesProtected.value = 0.0
        _heldTray.value = emptyList()
    }

    private fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}
