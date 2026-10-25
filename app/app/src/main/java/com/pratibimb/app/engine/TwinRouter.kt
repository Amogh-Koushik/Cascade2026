package com.pratibimb.app.engine

import android.app.Notification
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
import com.pratibimb.twin.TwinContext
import com.pratibimb.twin.WorthLabelKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * TwinRouter acts as the central coordinator between the Android NotificationListenerService,
 * the TwinEngine decision engine, and the Pratibimb UI state.
 */
object TwinRouter {
    val engine = StubEngine()

    @Volatile
    var activeListener: PratibimbListener? = null

    // ── Observable UI states ───────────────────────────────────────────────
    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    private val _liveNotifications = MutableStateFlow<List<LiveNotificationItem>>(emptyList())
    val liveNotifications: StateFlow<List<LiveNotificationItem>> = _liveNotifications.asStateFlow()

    private val _heldTray = MutableStateFlow<List<HeldTrayItem>>(emptyList())
    val heldTray: StateFlow<List<HeldTrayItem>> = _heldTray.asStateFlow()

    private val _heldTodayCount = MutableStateFlow(18)
    val heldTodayCount: StateFlow<Int> = _heldTodayCount.asStateFlow()

    private val _netMinutesProtected = MutableStateFlow(42.0)
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

    private val _isModeA = MutableStateFlow(true)
    val isModeA: StateFlow<Boolean> = _isModeA.asStateFlow()

    // Tracks recent notifications per package for burst detection
    private val recentPackagePings = ConcurrentHashMap<String, Int>()

    init {
        loadInitialMockData()
    }

    private fun loadInitialMockData() {
        val initialLive = mutableListOf<LiveNotificationItem>()
        val initialTray = mutableListOf<HeldTrayItem>()

        StubEngine.SCENARIOS.take(4).forEachIndexed { idx, sc ->
            val decision = sc.decision
            val item = LiveNotificationItem(
                id = "init_${sc.id}",
                app = sc.display.app,
                title = sc.display.title,
                text = sc.display.text,
                timeLabel = "${(idx + 1) * 3}m ago",
                decision = decision,
                senderDisplayName = sc.features.senderDisplayName,
                packageName = sc.features.packageName,
            )
            initialLive.add(item)

            if (decision.action == Action.HOLD) {
                initialTray.add(
                    HeldTrayItem(
                        id = "tray_${sc.id}",
                        app = sc.display.app,
                        title = sc.display.title,
                        text = sc.display.text,
                        sender = sc.features.senderDisplayName,
                        packageName = sc.features.packageName,
                        heldReason = decision.reason,
                        timeAgo = "${(idx + 1) * 3}m ago",
                        minutesUntilRelease = decision.card.minutesUntilRelease.toInt(),
                        decision = decision,
                    )
                )
            }
        }
        _liveNotifications.value = initialLive
        _heldTray.value = initialTray
    }

    fun onListenerConnected(listener: PratibimbListener) {
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

        // 1. Build features from live StatusBarNotification
        val features = FeatureBuilder.build(sbn, rankingMap, recentPackagePings)
        recentPackagePings[sbn.packageName] = (recentPackagePings[sbn.packageName] ?: 0) + 1

        // 2. Build current TwinContext
        val context = TwinContext(
            state = _phoneState.value,
            autonomy = _autonomy.value,
            minutesUntilRelease = 25.0,
            focusMinutesElapsed = 10.0,
            fatigueRisk = _fatigueRisk.value,
            explorationDraw = 0.5,
            alwaysDeliverSenders = emptySet(),
            alwaysDeliverPackages = emptySet(),
            demoLearning = true,
        )

        // 3. Engine evaluates features against context
        val decision = engine.decide(features, context)

        // 4. Extract display metadata
        val appLabel = getAppLabel(listener, sbn.packageName)
        val extras = sbn.notification.extras
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val title = if (rawTitle.isNotBlank()) rawTitle else appLabel
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val contentIntent = sbn.notification.contentIntent

        NotificationLog.add(
            source = sbn.packageName,
            message = "decision: ${decision.action.name} (${decision.reason})\nrule=${decision.rule.name} pings=${features.sameSenderRecentPings}",
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
                AlertPing.post(
                    context = listener,
                    title = "[$appLabel] $title",
                    text = text,
                    contentIntent = contentIntent,
                )
            }
        }
    }

    fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        reason: Int,
    ) {
        if (reason == NotificationListenerService.REASON_CLICK) {
            // User opened notification: feedback signal
            engine.applyFeedback(
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
        engine.applyFeedback(
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
        engine.applyFeedback(
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
        engine.applyFeedback(
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
        engine.applyFeedback(
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
                engine.applyFeedback(
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
        val currentContext = scenario.context.copy(
            state = _phoneState.value,
            autonomy = _autonomy.value,
            fatigueRisk = _fatigueRisk.value,
        )

        val decision = engine.decide(scenario.features, currentContext)

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
