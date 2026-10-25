package com.pratibimb.app.dnd

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.pratibimb.app.ui.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handles posting released notifications and batch focus digests back to the user's notification shade.
 */
object DigestNotifier {
    private const val CHANNEL_ID = "pratibimb_digest"
    private val nextId = AtomicInteger(20_000)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Digest",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Released notifications held during your focus session"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun postReleasedNotification(
        context: Context,
        app: String,
        title: String,
        text: String,
        contentIntent: PendingIntent? = null,
    ) {
        val permission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        if (permission != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)

        val appIntent = contentIntent ?: PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("[$app] $title")
            .setContentText(text)
            .setContentIntent(appIntent)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(nextId.getAndIncrement(), notification)
    }

    fun postBatchDigest(
        context: Context,
        count: Int,
        summaryLines: List<String>,
    ) {
        if (count <= 0) return
        val permission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        if (permission != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)

        val appIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val inboxStyle = Notification.InboxStyle()
            .setBigContentTitle("Focus Session Digest ($count held)")
        summaryLines.take(5).forEach { inboxStyle.addLine(it) }
        if (summaryLines.size > 5) {
            inboxStyle.setSummaryText("+${summaryLines.size - 5} more")
        }

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Focus Session Complete")
            .setContentText("$count notification${if (count > 1) "s were" else " was"} held during your focus session.")
            .setStyle(inboxStyle)
            .setContentIntent(appIntent)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(nextId.getAndIncrement(), notification)
    }
}
