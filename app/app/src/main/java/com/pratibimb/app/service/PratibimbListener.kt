package com.pratibimb.app.service

import android.app.Notification
import android.content.ComponentName
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import com.pratibimb.app.data.NotificationLog
import com.pratibimb.app.data.SpikeSettings
import com.pratibimb.app.dnd.AlertPing
import com.pratibimb.app.engine.TwinRouter

/**
 * Receives every notification posted on the phone and routes them to TwinRouter for
 * real-time decision evaluation (HOLD vs DELIVER), feature extraction, and feedback capture.
 */
class PratibimbListener : NotificationListenerService() {

    override fun onListenerConnected() {
        TwinRouter.onListenerConnected(this)
        NotificationLog.setListenerConnected(true)
        NotificationLog.add(SOURCE, "Listener connected")
    }

    // Android can unbind a listener (memory pressure, an update); ask it to bind again, or no
    // notification reaches the twin until access is toggled in Settings.
    override fun onListenerDisconnected() {
        TwinRouter.onListenerDisconnected()
        NotificationLog.setListenerConnected(false)
        NotificationLog.add(SOURCE, "Listener disconnected, asking Android to reconnect")
        requestRebind(ComponentName(this, PratibimbListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val ranking = Ranking().takeIf { rankingMap.getRanking(sbn.key, it) }
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val facts = listOf(
            "importance=${ranking?.importance ?: "?"}",
            "channel=${ranking?.channel?.id ?: notification.channelId}",
            "conversation=${ranking?.isConversation ?: "?"}",
            "group=${extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)}",
            "category=${notification.category ?: "-"}",
            "ongoing=${sbn.isOngoing}",
            // What kind of chat id the app attached (WhatsApp: ...@g.us is a group). Only the kind
            // is logged: the id itself contains a phone number.
            "chat-id=${idKind(notification.shortcutId, sbn.tag, sbn.groupKey)}",
        ).joinToString(" ")
        NotificationLog.add(
            source = sbn.packageName,
            message = "posted: ${maskCodes(title.orEmpty())} | ${maskCodes(text.orEmpty())}\n$facts",
            timeMillis = sbn.postTime,
        )

        // Route live notification into the Twin engine. An error with one notification must
        // not take the listener down for every notification after it, so it is logged instead.
        try {
            TwinRouter.onNotificationPosted(sbn, rankingMap)
        } catch (e: RuntimeException) {
            report("deciding", sbn, e)
        }

        // Optional debug spike ping:
        if (SpikeSettings.pingOnPost.value && sbn.packageName != packageName && !sbn.isOngoing) {
            val result = AlertPing.post(
                context = this,
                title = title ?: sbn.packageName,
                text = text.orEmpty(),
                contentIntent = notification.contentIntent,
            )
            NotificationLog.add(SOURCE, "Alert ping for ${sbn.packageName}: ${result.label}")
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        NotificationLog.add(sbn.packageName, "removed: ${removalReasonName(reason)}")
        try {
            TwinRouter.onNotificationRemoved(sbn, rankingMap, reason)
        } catch (e: RuntimeException) {
            report("handling the removal of", sbn, e)
        }
    }

    private fun report(what: String, sbn: StatusBarNotification, e: RuntimeException) {
        Log.e(TAG, "Error $what ${sbn.packageName}", e)
        val at = e.stackTrace.firstOrNull { it.className.startsWith("com.pratibimb") }?.let { " at ${it.fileName}:${it.lineNumber}" } ?: ""
        NotificationLog.add(SOURCE, "ERROR $what ${sbn.packageName}: ${e::class.simpleName}: ${e.message}$at")
    }

    private companion object {
        const val SOURCE = "listener"
        const val TAG = "PratibimbTwin"

        // OTPs and banking codes are never written to the log, here or in Logcat.
        val CODE = Regex("\\d{4,8}")

        fun maskCodes(text: String): String = CODE.replace(text, "••••")

        fun idKind(vararg ids: String?): String = when {
            ids.any { it?.contains("@g.us") == true } -> "…@g.us (group)"
            ids.any { it?.contains("@s.whatsapp.net") == true } -> "…@s.whatsapp.net (person)"
            ids.all { it.isNullOrEmpty() } -> "-"
            else -> "other"
        }
    }
}

// "tapped" is the signal the twin uses for "opened within 10 minutes".
private fun removalReasonName(reason: Int): String = when (reason) {
    NotificationListenerService.REASON_CLICK -> "tapped"
    NotificationListenerService.REASON_CANCEL -> "dismissed"
    NotificationListenerService.REASON_CANCEL_ALL -> "cleared all"
    NotificationListenerService.REASON_APP_CANCEL -> "cancelled by its app"
    NotificationListenerService.REASON_LISTENER_CANCEL -> "cancelled by a listener"
    NotificationListenerService.REASON_TIMEOUT -> "timed out"
    else -> "other ($reason)"
}
