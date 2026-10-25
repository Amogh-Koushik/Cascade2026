package com.pratibimb.app.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pratibimb.app.engine.MockScenario
import com.pratibimb.app.engine.StubEngine
import com.pratibimb.app.engine.TwinRouter
import com.pratibimb.twin.Action
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.Decision
import com.pratibimb.twin.FatigueRisk
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
    val contentIntent: PendingIntent? = null,
)

class PratibimbViewModel(application: Application) : AndroidViewModel(application) {

    val engine: StubEngine get() = TwinRouter.engine

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
    val isModeA: StateFlow<Boolean> = TwinRouter.isModeA

    fun setModeA(modeA: Boolean) {
        TwinRouter.setModeA(modeA)
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

    val selectedPersona: StateFlow<String> = TwinRouter.selectedPersona

    fun setPersona(persona: String) {
        TwinRouter.setPersona(persona)
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
        TwinRouter.setPhoneState(PhoneState.FOCUS_STEADY)
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
        TwinRouter.setPhoneState(PhoneState.FREE)
        timerJob?.cancel()
        _remainingSeconds.value = _selectedDurationMinutes.value * 60
        TwinRouter.releaseAllDigest(getApplication())
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
        TwinRouter.alwaysDeliverSender(trayId)
    }

    fun markHeldWrong(trayId: String) {
        TwinRouter.markHeldWrong(trayId)
    }

    fun markHeldCorrect(trayId: String) {
        TwinRouter.markHeldCorrect(trayId)
    }

    fun releaseAllDigest() {
        TwinRouter.releaseAllDigest(getApplication())
    }

    fun resetAllCounts() {
        TwinRouter.resetAll()
    }
}
