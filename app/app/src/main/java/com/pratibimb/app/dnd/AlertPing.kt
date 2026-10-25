package com.pratibimb.app.dnd

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.atomic.AtomicInteger

enum class PingResult(val label: String) {
    POSTED("posted"),
    NO_POST_PERMISSION("blocked, post-notification permission missing"),
    NO_DND_ACCESS("blocked, Do Not Disturb access missing"),
}

// A short-lived alert on the bypass channel. The original notification stays in the shade
// untouched (silenced by Priority mode, reply buttons intact); the ping only makes the sound,
// opens the original's content when tapped, and removes itself after a few seconds. The tag
// carries the original's key, so a tap on the ping can be credited to the original.
object AlertPing {
    private const val TIMEOUT_MS = 8_000L

    // Starts high so ping ids never collide with small ids used elsewhere in the app.
    private val nextId = AtomicInteger(10_000)

    fun post(
        context: Context,
        title: String,
        text: String,
        contentIntent: PendingIntent?,
        tag: String? = null,
    ): PingResult {
        val permission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        if (permission != PackageManager.PERMISSION_GRANTED) return PingResult.NO_POST_PERMISSION
        val channelId = AlertChannel.ensure(context) ?: return PingResult.NO_DND_ACCESS

        val ping = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(tag, nextId.getAndIncrement(), ping)
        return PingResult.POSTED
    }
}
