package com.pratibimb.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratibimb.app.data.NotificationLog
import com.pratibimb.app.data.SpikeSettings
import com.pratibimb.app.dnd.AlertChannel
import com.pratibimb.app.dnd.AlertPing
import com.pratibimb.app.dnd.FocusDnd
import com.pratibimb.app.service.PratibimbListener

class MainActivity : ComponentActivity() {

    private val status = mutableStateOf(PermissionStatus.UNKNOWN)
    private lateinit var focusDnd: FocusDnd

    private val postNotificationsRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focusDnd = FocusDnd(this)
        val actions = SpikeActions(
            openAppInfo = ::openAppInfo,
            openNotificationAccess = ::openNotificationAccess,
            openDndAccess = { openSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
            requestPostNotifications = {
                postNotificationsRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            createAlertChannel = { log(AlertChannel.describe(this)) },
            sendTestPing = ::sendTestPing,
            enterPriority = ::enterPriority,
            restoreFilter = ::restoreFilter,
            setPingOnPost = SpikeSettings::setPingOnPost,
            clearLog = NotificationLog::clear,
        )

        setContent {
            val entries by NotificationLog.entries.collectAsStateWithLifecycle()
            val listenerConnected by NotificationLog.listenerConnected.collectAsStateWithLifecycle()
            val pingOnPost by SpikeSettings.pingOnPost.collectAsStateWithLifecycle()
            MaterialTheme {
                SpikeScreen(
                    status = status.value,
                    listenerConnected = listenerConnected,
                    entries = entries,
                    pingOnPost = pingOnPost,
                    actions = actions,
                )
            }
        }
    }

    // Permissions are granted on other settings screens, so re-read them on every return.
    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        status.value = PermissionStatus.read(this)
    }

    private fun openAppInfo() {
        openSettings(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }

    // Jumps straight to our own listener's switch where the phone supports it,
    // otherwise to the general list of notification listeners.
    private fun openNotificationAccess() {
        val listener = ComponentName(this, PratibimbListener::class.java).flattenToString()
        openSettings(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener),
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
    }

    // Some phone brands remove settings screens, so each intent is tried in order.
    private fun openSettings(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (notFound: ActivityNotFoundException) {
                log("Settings screen ${intent.action} not found on this phone, trying the next one")
            }
        }
        log("This phone has no settings screen for that; open it from the Settings app")
    }

    private fun sendTestPing() {
        val result = AlertPing.post(
            context = this,
            title = "Pratibimb test ping",
            text = "This should ring even in Priority mode",
            contentIntent = null,
        )
        log("Test ping: ${result.label}")
    }

    private fun enterPriority() {
        if (focusDnd.enterPriority()) {
            log("Priority mode on. Messages from other apps should now arrive silently.")
        } else {
            log("Cannot enter Priority mode without Do Not Disturb access, so Mode B applies")
        }
        refreshStatus()
    }

    private fun restoreFilter() {
        if (focusDnd.restore()) {
            log("Previous filter and policy restored")
        } else {
            log("Nothing to restore")
        }
        refreshStatus()
    }

    private fun log(message: String) {
        NotificationLog.add(SOURCE, message)
    }

    private companion object {
        const val SOURCE = "app"
    }
}
