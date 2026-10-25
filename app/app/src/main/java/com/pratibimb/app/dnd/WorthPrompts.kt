package com.pratibimb.app.dnd

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import com.pratibimb.app.service.WorthFeedbackReceiver
import java.util.concurrent.atomic.AtomicInteger

// What a "Worth it / Not worth it" tap on a delivered alert is about.
data class WorthPrompt(val liveItemId: String, val packageName: String, val senderHash: String?)

// The two buttons asked on one in three delivered alerts. In Mode A they ride on the alert ping;
// in Mode B the real app already made its sound, so they come on a silent notification.
object WorthPrompts {
    const val ACTION = "com.pratibimb.app.WORTH_FEEDBACK"
    const val EXTRA_LIVE_ID = "live_id"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_SENDER = "sender_hash"
    const val EXTRA_WORTH = "worth"
    const val EXTRA_TAG = "notification_tag"
    const val EXTRA_ID = "notification_id"

    private const val CHANNEL_ID = "pratibimb_feedback"
    private const val TIMEOUT_MS = 120_000L

    // Starts above the ping, digest and demo ids so they never collide.
    private val nextId = AtomicInteger(30_000)

    fun actions(context: Context, prompt: WorthPrompt, tag: String?, id: Int): List<Notification.Action> =
        listOf(true, false).map { worth ->
            val intent = Intent(context, WorthFeedbackReceiver::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_LIVE_ID, prompt.liveItemId)
                .putExtra(EXTRA_PACKAGE, prompt.packageName)
                .putExtra(EXTRA_SENDER, prompt.senderHash)
                .putExtra(EXTRA_WORTH, worth)
                .putExtra(EXTRA_TAG, tag)
                .putExtra(EXTRA_ID, id)
            val pending = PendingIntent.getBroadcast(
                context,
                id * 2 + if (worth) 1 else 0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            Notification.Action.Builder(null as Icon?, if (worth) "Worth it" else "Not worth it", pending).build()
        }

    fun postSilent(context: Context, appLabel: String, title: String, prompt: WorthPrompt) {
        val permission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        if (permission != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, "Was it worth it?", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Silent questions that teach the twin what matters to you"
            manager.createNotificationChannel(channel)
        }
        val id = nextId.getAndIncrement()
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Was this worth it?")
            .setContentText("[$appLabel] $title")
            .setTimeoutAfter(TIMEOUT_MS)
        actions(context, prompt, null, id).forEach { builder.addAction(it) }
        manager.notify(id, builder.build())
    }
}
