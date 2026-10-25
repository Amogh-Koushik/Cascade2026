package com.pratibimb.app.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pratibimb.app.data.LogEntry
import com.pratibimb.app.data.NotificationLog
import com.pratibimb.app.dnd.AlertChannel
import com.pratibimb.app.dnd.FocusDnd
import com.pratibimb.app.engine.MockScenario
import com.pratibimb.app.engine.PersonaOption
import com.pratibimb.app.engine.Personas
import com.pratibimb.app.engine.TwinRouter
import com.pratibimb.twin.Action
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.Decision
import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.PhoneState
import com.pratibimb.twin.RealTwinEngine
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
    // Salted sender hash, so a tap teaches the twin about this sender and not just the app.
    val senderHash: String? = null,
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
    val contentIntent: PendingIntent? = null,
    val senderHash: String? = null,
    // When it was held; the hold cap releases it once it has waited policy.cap_min minutes.
    val heldAtMillis: Long = System.currentTimeMillis(),
)

class PratibimbViewModel(application: Application) : AndroidViewModel(application) {

    val engine: RealTwinEngine get() = TwinRouter.engine

    // ── Permissions ────────────────────────────────────────────────────────
    // Real permission status by default; the mockup is only for the Debug screen's design view.
    private val _useLivePermissions = MutableStateFlow(true)
    val useLivePermissions: StateFlow<Boolean> = _useLivePermissions.asStateFlow()

    private val _permissionStatus = MutableStateFlow(PermissionStatus.read(application))
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    fun refreshPermissions(context: Context) {
        if (_useLivePermissions.value) {
            _permissionStatus.value = PermissionStatus.read(context)
        }
        // Mode A needs DND access; if it was taken away in Settings, stop claiming Mode A.
        if (TwinRouter.isModeA.value && !focusDnd.hasAccess) {
            setModeA(false)
        }
        refreshFocusStatus()
    }

    fun setLivePermissionMode(enabled: Boolean) {
        _useLivePermissions.value = enabled
        _permissionStatus.value =
            if (enabled) {
                PermissionStatus.read(getApplication<Application>())
            } else {
                PermissionStatus.MOCK_CHECKLIST
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
    val isModeA: StateFlow<Boolean> = TwinRouter.isModeA

    // A switch during a running session takes effect at once, so the phone is never left in a
    // DND state that no longer matches the mode shown on screen. During a break the phone stays
    // unsilenced; the new mode applies when focus resumes.
    fun setModeA(modeA: Boolean) {
        var effective = modeA
        if (_isFocusActive.value && _breakSecondsLeft.value == 0) {
            if (modeA && !focusDnd.enterPriority()) {
                effective = false
                NotificationLog.add("focus", "Priority mode refused by the phone; staying in Mode B")
            } else if (!modeA) {
                focusDnd.restore()
            }
        }
        TwinRouter.setModeA(effective)
        NotificationLog.add("focus", if (effective) "Mode A: Priority DND" else "Mode B: hold-only")
        refreshFocusStatus()
    }

    // ── Phone state & context ──────────────────────────────────────────────
    val phoneState: StateFlow<PhoneState> = TwinRouter.phoneState

    fun setPhoneState(state: PhoneState) {
        TwinRouter.setPhoneState(state)
    }

    val autonomy: StateFlow<Autonomy> = TwinRouter.autonomy

    fun setAutonomy(autonomy: Autonomy) {
        TwinRouter.setAutonomy(autonomy)
    }

    val fatigueRisk: StateFlow<FatigueRisk> = TwinRouter.fatigueRisk

    fun setFatigueRisk(risk: FatigueRisk) {
        TwinRouter.setFatigueRisk(risk)
    }

    val personas: List<PersonaOption> = Personas.all

    // The selected persona's id.
    val selectedPersona: StateFlow<String> = TwinRouter.selectedPersona

    // Takes a persona id (or its label); the learned counts are kept across the switch.
    fun setPersona(idOrLabel: String) {
        TwinRouter.setPersona(idOrLabel)
        refreshFocusStatus()
    }

    val demoLearning: StateFlow<Boolean> = TwinRouter.demoLearning

    fun setDemoLearning(enabled: Boolean) {
        TwinRouter.setDemoLearning(enabled)
    }

    // Returns how many simulated outcomes were applied (0 if the file could not be read).
    fun runLearningReplay(): Int = TwinRouter.runLearningReplay()

    // ── Focus Session ──────────────────────────────────────────────────────
    private val _isFocusActive = MutableStateFlow(false)
    val isFocusActive: StateFlow<Boolean> = _isFocusActive.asStateFlow()

    private val _selectedDurationMinutes = MutableStateFlow(50)
    val selectedDurationMinutes: StateFlow<Int> = _selectedDurationMinutes.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(50 * 60)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    // Seconds left in the current break; 0 when focus is running or stopped.
    private val _breakSecondsLeft = MutableStateFlow(0)
    val breakSecondsLeft: StateFlow<Int> = _breakSecondsLeft.asStateFlow()

    private var timerJob: Job? = null

    private val focusDnd = FocusDnd(application)

    // What the Debug screen shows: the delivery mode, the DND state actually in force, the
    // alert channel, and every real decision as it happens.
    private val _focusStatus = MutableStateFlow<List<String>>(emptyList())
    val focusStatus: StateFlow<List<String>> = _focusStatus.asStateFlow()
    val logEntries: StateFlow<List<LogEntry>> = NotificationLog.entries

    init {
        TwinRouter.init(application)
        // A new view model means no session is running. If the app was killed mid-focus, the
        // saved filter is still pending, so the phone gets its normal sound settings back here.
        if (focusDnd.restore()) {
            NotificationLog.add("focus", "Restored DND settings left over from an earlier session")
        }
        refreshFocusStatus()
    }

    fun refreshFocusStatus() {
        val session = if (_breakSecondsLeft.value > 0) {
            "on a break, ${_breakSecondsLeft.value / 60 + 1} min left"
        } else if (_isFocusActive.value) {
            "running, ${_remainingSeconds.value / 60} min left"
        } else {
            "not running"
        }
        _focusStatus.value = listOf(
            "Mode " + if (TwinRouter.isModeA.value) "A (Priority DND)" else "B (hold-only)",
            "Session $session",
            focusDnd.describe(),
            AlertChannel.describe(getApplication<Application>()),
            "Engine ${TwinRouter.engineStatus}",
            "Listener " + if (TwinRouter.isListenerConnected.value) "connected" else "not connected",
        )
    }

    fun clearLog() {
        NotificationLog.clear()
    }

    fun selectDuration(minutes: Int) {
        if (!_isFocusActive.value) {
            _selectedDurationMinutes.value = minutes
            _remainingSeconds.value = minutes * 60
        }
    }

    fun startFocus() {
        _isFocusActive.value = true
        _breakSecondsLeft.value = 0
        _remainingSeconds.value = _selectedDurationMinutes.value * 60
        enterFocus("Focus started")

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var ticks = 0
            while (isActive && _remainingSeconds.value > 0) {
                delay(1000)
                // A break pauses the focus clock.
                if (_breakSecondsLeft.value > 0) {
                    _breakSecondsLeft.value -= 1
                    if (_breakSecondsLeft.value == 0) enterFocus("Break over, focus resumed")
                } else {
                    _remainingSeconds.value -= 1
                }
                ticks += 1
                if (ticks % TICK_SECONDS == 0) TwinRouter.tick(getApplication())
            }
            if (_remainingSeconds.value <= 0) {
                stopFocus()
            }
        }
    }

    // Starts the twin's session for the focus time left and silences the phone. Mode A uses
    // Priority mode; if the phone refuses (no DND access, or its own DND schedule is in
    // charge), fall back to Mode B: holds still work, but real apps may buzz once.
    private fun enterFocus(event: String) {
        TwinRouter.setPhoneState(PhoneState.FOCUS_STEADY)
        TwinRouter.startSession(_remainingSeconds.value)
        if (TwinRouter.isModeA.value) {
            if (focusDnd.enterPriority()) {
                NotificationLog.add("focus", "$event: Priority DND on")
            } else {
                TwinRouter.setModeA(false)
                NotificationLog.add("focus", "$event: Priority DND refused, using Mode B")
            }
        } else {
            NotificationLog.add("focus", "$event: Mode B (hold-only)")
        }
        refreshFocusStatus()
    }

    // A short break: everything held so far is released, the phone rings normally, and the
    // focus clock waits until the break is over.
    fun takeBreak() {
        if (!_isFocusActive.value || _breakSecondsLeft.value > 0) return
        _breakSecondsLeft.value = BREAK_MINUTES * 60
        TwinRouter.setPhoneState(PhoneState.FREE)
        TwinRouter.endSession()
        focusDnd.restore()
        TwinRouter.releaseAllDigest(getApplication(), "Break time")
        NotificationLog.add("focus", "Break started: $BREAK_MINUTES min, held items released")
        refreshFocusStatus()
    }

    fun stopFocus() {
        _isFocusActive.value = false
        _breakSecondsLeft.value = 0
        TwinRouter.setPhoneState(PhoneState.FREE)
        TwinRouter.endSession()
        // Puts back the user's own filter and policy; does nothing if Priority was never set.
        val restored = focusDnd.restore()
        NotificationLog.add(
            "focus",
            if (restored) "Focus ended: your DND settings restored" else "Focus ended",
        )
        timerJob?.cancel()
        _remainingSeconds.value = _selectedDurationMinutes.value * 60
        TwinRouter.releaseAllDigest(getApplication(), "Focus session complete")
        refreshFocusStatus()
    }

    // The session timer lives in viewModelScope, so the session ends with this view model;
    // the phone must not be left in Priority mode afterwards.
    override fun onCleared() {
        if (_isFocusActive.value) {
            TwinRouter.endSession()
            TwinRouter.setPhoneState(PhoneState.FREE)
            focusDnd.restore()
        }
        super.onCleared()
    }

    // ── Metrics & Feeds (Delegated to TwinRouter singleton) ────────────────
    val heldTodayCount: StateFlow<Int> = TwinRouter.heldTodayCount
    val netMinutesProtected: StateFlow<Double> = TwinRouter.netMinutesProtected
    val liveNotifications: StateFlow<List<LiveNotificationItem>> = TwinRouter.liveNotifications
    val heldTray: StateFlow<List<HeldTrayItem>> = TwinRouter.heldTray
    val isListenerConnected: StateFlow<Boolean> = TwinRouter.isListenerConnected

    // ── Scenario execution & User Actions ──────────────────────────────────
    fun triggerScenario(scenario: MockScenario) {
        TwinRouter.triggerScenario(scenario)
    }

    fun submitWorthFeedback(itemId: String, isWorth: Boolean) {
        TwinRouter.submitWorthFeedback(itemId, isWorth)
    }

    fun releaseHeldItem(trayId: String) {
        TwinRouter.releaseHeldItem(getApplication(), trayId)
    }

    fun alwaysDeliverSender(trayId: String) {
        TwinRouter.alwaysDeliverSender(getApplication(), trayId)
    }

    fun markHeldWrong(trayId: String) {
        TwinRouter.markHeldWrong(trayId)
    }

    fun markHeldCorrect(trayId: String) {
        TwinRouter.markHeldCorrect(trayId)
    }

    fun releaseAllDigest() {
        TwinRouter.releaseAllDigest(getApplication(), "Released from the tray")
    }

    fun resetAllCounts() {
        TwinRouter.resetAll()
    }

    private companion object {
        const val BREAK_MINUTES = 5

        // How often, in seconds, held items are checked against the hold cap and delivered
        // ones against the open window.
        const val TICK_SECONDS = 15
    }
}
