package com.pratibimb.app.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.pratibimb.app.data.NotificationLog
import com.pratibimb.app.dnd.AlertPing
import com.pratibimb.app.dnd.FocusDnd
import com.pratibimb.app.ui.navigation.PratibimbApp
import com.pratibimb.app.ui.theme.PratibimbTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PratibimbViewModel by viewModels()
    private lateinit var focusDnd: FocusDnd

    private val postNotificationsRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focusDnd = FocusDnd(this)

        setContent {
            PratibimbTheme {
                PratibimbApp(
                    viewModel = viewModel,
                    onRequestPostNotifications = {
                        postNotificationsRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onSendTestPing = ::sendTestPing,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions(this)
    }

    private fun sendTestPing() {
        val result = AlertPing.post(
            context = this,
            title = "Pratibimb test ping",
            text = "This should ring even in Priority mode",
            contentIntent = null,
        )
        NotificationLog.add("app", "Test ping: ${result.label}")
    }
}
