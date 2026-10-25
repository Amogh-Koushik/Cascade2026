package com.pratibimb.app.dnd

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.pratibimb.app.engine.MockScenario
import java.util.concurrent.atomic.AtomicInteger

// Posts a Debug scenario as a real notification from this app, on a silent channel, so it goes
// through the same listener, hold and ping path as a real one. The listener treats it as the
// simulated app only because of this channel and the scenario id in its extras.
object DemoNotifier {
    const val CHANNEL_ID = "pratibimb_demo"
    const val EXTRA_SCENARIO = "com.pratibimb.app.DEMO_SCENARIO"

    private val nextId = AtomicInteger(40_000)

    // False when the app may not post notifications; the caller then decides the scenario directly.
    fun post(context: Context, scenario: MockScenario): Boolean {
        val permission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        if (permission != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            // Low importance: shown in the shade, never a sound or a pop-up, in either mode.
            val channel = NotificationChannel(CHANNEL_ID, "Demo notifications", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Sample notifications for the demo. They never make a sound."
            manager.createNotificationChannel(channel)
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(scenario.display.title)
            .setContentText(scenario.display.text)
            .setSubText("Demo · ${scenario.display.app}")
            .setAutoCancel(true)
            .addExtras(Bundle().apply { putString(EXTRA_SCENARIO, scenario.id) })
            .build()
        manager.notify(nextId.getAndIncrement(), notification)
        return true
    }
}
