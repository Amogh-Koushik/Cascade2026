package com.pratibimb.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import com.pratibimb.app.data.NotificationLog
import com.pratibimb.app.data.SpikeSettings
import com.pratibimb.app.dnd.AlertPing

// Receives every notification posted on the phone. For now it records what Android exposes
// for each one (importance, channel, conversation flag, removal reason), so we can confirm on
// each demo phone that the fields the feature builder will need are really there.
class PratibimbListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationLog.setListenerConnected(true)
        NotificationLog.add(SOURCE, "Listener connected")
    }

    override fun onListenerDisconnected() {
        NotificationLog.setListenerConnected(false)
        NotificationLog.add(SOURCE, "Listener disconnected")
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
        ).joinToString(" ")
        NotificationLog.add(
            source = sbn.packageName,
            message = "posted: ${title.orEmpty()} | ${text.orEmpty()}\n$facts",
            timeMillis = sbn.postTime,
        )

        // Our own pings are skipped so a ping can never trigger another ping.
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
    }

    private companion object {
        const val SOURCE = "listener"
    }
}

// "tapped" is the signal the twin will later use for "opened within 10 minutes".
private fun removalReasonName(reason: Int): String = when (reason) {
    NotificationListenerService.REASON_CLICK -> "tapped"
    NotificationListenerService.REASON_CANCEL -> "dismissed"
    NotificationListenerService.REASON_CANCEL_ALL -> "cleared all"
    NotificationListenerService.REASON_APP_CANCEL -> "cancelled by its app"
    NotificationListenerService.REASON_LISTENER_CANCEL -> "cancelled by a listener"
    NotificationListenerService.REASON_TIMEOUT -> "timed out"
    else -> "other ($reason)"
}
