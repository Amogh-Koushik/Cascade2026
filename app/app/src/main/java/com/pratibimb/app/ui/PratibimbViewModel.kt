package com.pratibimb.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratibimb.app.engine.MockScenario
import com.pratibimb.app.engine.StubEngine
import com.pratibimb.twin.Action
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.Decision
import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.FeedbackEvent
import com.pratibimb.twin.PhoneState
import com.pratibimb.twin.WorthLabelKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LiveNotificationItem(
    val id: String,
    val app: String,
    val title: String,
    val text: String,
    val timeLabel: String,
    val decision: Decision,
    val senderDisplayName: String?,
    val packageName: String,
    val feedbackLabel: WorthLabelKind? = null,
)

data class HeldTrayItem(
    val id: String,
    val app: String,
    val title: String,
    val text: String,
    val sender: String?,
    val packageName: String,
    val heldReason: String,
    val timeAgo: String,
    val minutesUntilRelease: Int,
    val decision: Decision,
)

class PratibimbViewModel : ViewModel() {

    val engine = StubEngine()

    // ── Permissions ────────────────────────────────────────────────────────
    private val _permissionStatus = MutableStateFlow(PermissionStatus.MOCK_CHECKLIST)
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _useLivePermissions = MutableStateFlow(false)
    val useLivePermissions: StateFlow<Boolean> = _useLivePermissions.asStateFlow()

    fun refreshPermissions(context: Context) {
        if (_useLivePermissions.value) {
            _permissionStatus.value = PermissionStatus.read(context)
        }
    }

    fun setLivePermissionMode(enabled: Boolean, context: Context? = null) {
        _useLivePermissions.value = enabled
        if (enabled && context != null) {
            _permissionStatus.value = PermissionStatus.read(context)
        } else if (!enabled) {
            _permissionStatus.value = PermissionStatus.MOCK_CHECKLIST
        }
    }

    fun simulateAllPermissionsGranted() {
        _permissionStatus.value = PermissionStatus(
            restrictedSettingsAllowed = true,
            notificationAccess = true,
            usageAccess = true,
            dndAccess = true,
            postNotifications = true,
            batteryExemption = true,
            interruptionFilter = android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        )
    }

    // ── Delivery mode: Mode A (Priority DND) vs Mode B (Fallback) ──────────
    private val _isModeA = MutableStateFlow(true)
    val isModeA: StateFlow<Boolean> = _isModeA.asStateFlow()

    fun setModeA(modeA: Boolean) {
        _isModeA.value = modeA
    }

    // ── Phone state & context ──────────────────────────────────────────────
    private val _phoneState = MutableStateFlow(PhoneState.FREE)
    val phoneState: StateFlow<PhoneState> = _phoneState.asStateFlow()

    fun setPhoneState(state: PhoneState) {
        _phoneState.value = state
    }

    private val _autonomy = MutableStateFlow(Autonomy.AUTO)
    val autonomy: StateFlow<Autonomy> = _autonomy.asStateFlow()

    fun setAutonomy(autonomy: Autonomy) {
        _autonomy.value = autonomy
    }

    private val _fatigueRisk = MutableStateFlow(FatigueRisk.LOW)
    val fatigueRisk: StateFlow<FatigueRisk> = _fatigueRisk.asStateFlow()

    fun setFatigueRisk(risk: FatigueRisk) {
        _fatigueRisk.value = risk
    }

    private val _selectedPersona = MutableStateFlow("Deep work (balanced)")
    val selectedPersona: StateFlow<String> = _selectedPersona.asStateFlow()

    fun setPersona(persona: String) {
        _selectedPersona.value = persona
    }

    // ── Focus Session ──────────────────────────────────────────────────────
    private val _isFocusActive = MutableStateFlow(false)
    val isFocusActive: StateFlow<Boolean> = _isFocusActive.asStateFlow()

    private val _selectedDurationMinutes = MutableStateFlow(50)
    val selectedDurationMinutes: StateFlow<Int> = _selectedDurationMinutes.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(50 * 60)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private var timerJob: Job? = null

    fun selectDuration(minutes: Int) {
        if (!_isFocusActive.value) {
            _selectedDurationMinutes.value = minutes
            _remainingSeconds.value = minutes * 60
        }
    }

    fun startFocus() {
        _isFocusActive.value = true
        _phoneState.value = PhoneState.FOCUS_STEADY
        _remainingSeconds.value = _selectedDurationMinutes.value * 60

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _remainingSeconds.value > 0) {
                delay(1000)
                _remainingSeconds.value -= 1
            }
            if (_remainingSeconds.value <= 0) {
                stopFocus()
            }
        }
    }

    fun stopFocus() {
        _isFocusActive.value = false
        _phoneState.value = PhoneState.FREE
        timerJob?.cancel()
        _remainingSeconds.value = _selectedDurationMinutes.value * 60
        releaseAllDigest()
    }

    // ── Today metrics ──────────────────────────────────────────────────────
    private val _heldTodayCount = MutableStateFlow(18)
    val heldTodayCount: StateFlow<Int> = _heldTodayCount.asStateFlow()

    private val _netMinutesProtected = MutableStateFlow(42.0)
    val netMinutesProtected: StateFlow<Double> = _netMinutesProtected.asStateFlow()

    // ── Live notifications feed & Tray ─────────────────────────────────────
    private val _liveNotifications = MutableStateFlow<List<LiveNotificationItem>>(emptyList())
    val liveNotifications: StateFlow<List<LiveNotificationItem>> = _liveNotifications.asStateFlow()

    private val _heldTray = MutableStateFlow<List<HeldTrayItem>>(emptyList())
    val heldTray: StateFlow<List<HeldTrayItem>> = _heldTray.asStateFlow()

    init {
        // Pre-populate with realistic initial entries matching PRD scenario examples
        loadInitialData()
    }

    private fun loadInitialData() {
        val initialLive = mutableListOf<LiveNotificationItem>()
        val initialTray = mutableListOf<HeldTrayItem>()

        // Load 4 baseline scenarios into live feed & tray
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

    // ── Scenario execution ─────────────────────────────────────────────────
    fun triggerScenario(scenario: MockScenario) {
        val currentContext = scenario.context.copy(
            state = if (_isFocusActive.value) _phoneState.value else PhoneState.FREE,
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

    // ── Feedback actions ───────────────────────────────────────────────────
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

    fun releaseHeldItem(trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        engine.applyFeedback(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = null,
                label = WorthLabelKind.RELEASE_NOW,
            )
        )
        _heldTray.value = _heldTray.value.filterNot { it.id == trayId }
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

    fun releaseAllDigest() {
        _heldTray.value = emptyList()
    }

    fun resetAllCounts() {
        engine.resetCounts()
        _heldTodayCount.value = 0
        _netMinutesProtected.value = 0.0
        _heldTray.value = emptyList()
    }
}
