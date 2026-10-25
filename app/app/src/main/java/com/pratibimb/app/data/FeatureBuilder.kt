package com.pratibimb.app.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pratibimb.twin.NotificationFeatures
import java.security.MessageDigest

/**
 * Builds a [NotificationFeatures] row from a live [StatusBarNotification], by the rules in
 * CONTRACTS.md section 3, so the phone feeds the engine exactly what engine.py would see.
 */
object FeatureBuilder {

    // Earlier conversation notifications per sender hash, for the repeated-ping override.
    private val recentBySender = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun build(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        rules: FeatureRules,
        senderSalt: String,
    ): NotificationFeatures {
        val ranking = NotificationListenerService.Ranking()
            .takeIf { rankingMap.getRanking(sbn.key, it) }
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val haystack = FeatureRules.haystack(title, text)

        // Messaging apps do not always publish Android's conversation shortcut, so a message
        // notification or a MessagingStyle notification counts as a conversation too.
        val isConversation = ranking?.isConversation == true ||
            notification.category == Notification.CATEGORY_MESSAGE ||
            extras.containsKey(Notification.EXTRA_MESSAGES)

        // WhatsApp and Telegram mark groups with a conversation title or a group channel.
        val channelId = ranking?.channel?.id ?: notification.channelId.orEmpty()
        val groupChat = isConversation && (
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
                extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null ||
                channelId.contains("group", ignoreCase = true)
            )

        // A one-to-one chat's title is the person. In a group the title names the group, not
        // who wrote, so a group gets no sender: group chatter can never pass as one person
        // pinging twice.
        val senderDisplayName = title.takeIf { isConversation && !groupChat && it.isNotBlank() }
        val senderHash = senderDisplayName?.let { hashSender(it, senderSalt) }

        return NotificationFeatures(
            key = sbn.key,
            postedAtMillis = sbn.postTime,
            packageName = sbn.packageName,
            category = rules.categoryOf(sbn.packageName),
            androidCategory = notification.category,
            channelImportance = ranking?.importance ?: 3,
            isOngoing = sbn.isOngoing,
            isConversation = isConversation,
            priorityConversation = ranking?.channel?.isImportantConversation == true,
            groupChat = groupChat,
            senderHash = senderHash,
            senderDisplayName = senderDisplayName,
            sameSenderRecentPings = recentPings(senderHash, sbn.postTime, rules.repeatWindowMillis),
            baitHit = rules.baitHit(haystack),
            urgencyHit = rules.urgencyHit(haystack),
            otpPattern = rules.otpPattern(haystack, text),
        )
    }

    @Synchronized
    fun reset() {
        recentBySender.clear()
    }

    // Counts earlier pings from this sender inside the window, then records this one.
    private fun recentPings(senderHash: String?, postedAt: Long, windowMillis: Long): Int {
        if (senderHash == null) return 0
        val times = recentBySender.getOrPut(senderHash) { ArrayDeque() }
        while (times.isNotEmpty() && postedAt - times.first() > windowMillis) times.removeFirst()
        val earlier = times.size
        times.addLast(postedAt)
        return earlier
    }

    // Salted, so the stored hash cannot be matched against a list of names.
    private fun hashSender(name: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$name".toByteArray())
        return "s_" + digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
