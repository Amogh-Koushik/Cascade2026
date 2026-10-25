package com.pratibimb.app.dnd

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit

// The one channel allowed to ring through Priority mode. Android only honours bypassDnd if
// the channel was created after DND access was granted and the user has not edited it since.
// A deleted channel id comes back with its old settings, so a bad channel is replaced under a
// fresh id rather than recreated under the same one.
object AlertChannel {
    private const val PREFS_NAME = "alert_channel"
    private const val KEY_VERSION = "version"
    private const val ID_PREFIX = "pratibimb_alert_v"

    // Returns the id of a channel that can bypass DND, or null while DND access is missing.
    fun ensure(context: Context): String? {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) return null

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var version = prefs.getInt(KEY_VERSION, 1)
        val existing = manager.getNotificationChannel(channelId(version))

        if (existing != null && isUsable(existing)) return existing.id
        if (existing != null) {
            manager.deleteNotificationChannel(existing.id)
            version += 1
            prefs.edit { putInt(KEY_VERSION, version) }
        }

        val channel = NotificationChannel(
            channelId(version),
            "Focus alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications Pratibimb lets through during a focus session"
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
        return channel.id
    }

    fun describe(context: Context): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = ensure(context) ?: return "No alert channel: Do Not Disturb access is missing"
        val channel = manager.getNotificationChannel(id)
            ?: return "Alert channel $id could not be read back"
        return "Alert channel $id: bypassDnd=${channel.canBypassDnd()}, importance=${channel.importance}"
    }

    private fun channelId(version: Int) = ID_PREFIX + version

    private fun isUsable(channel: NotificationChannel) =
        channel.canBypassDnd() && channel.importance >= NotificationManager.IMPORTANCE_HIGH
}
