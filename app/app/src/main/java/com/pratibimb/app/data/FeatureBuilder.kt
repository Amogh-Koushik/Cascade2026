package com.pratibimb.app.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pratibimb.app.engine.PingCounter
import com.pratibimb.twin.NotificationFeatures
import java.security.MessageDigest

/**
 * Builds a [NotificationFeatures] row from a live [StatusBarNotification], by the rules in
 * CONTRACTS.md section 3, so the phone feeds the engine exactly what engine.py would see.
 */
object FeatureBuilder {

    // Recent pings per sender hash, for the repeated-ping override.
    private val pings = PingCounter()

    @Synchronized
    fun build(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        rules: FeatureRules,
        senderSalt: String,
        // False for a reel or a forward under the text-only rule: it is not a new ping.
        countsAsPing: Boolean = true,
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
                channelId.contains("group", ignoreCase = true) ||
                isWhatsAppGroupId(sbn)
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
            sameSenderRecentPings = recentPings(senderHash, sbn.postTime, rules.repeatWindowMillis, countsAsPing),
            baitHit = rules.baitHit(haystack),
            urgencyHit = rules.urgencyHit(haystack),
            otpPattern = rules.otpPattern(haystack, text),
        )
    }

    // WhatsApp names chats by an id: groups end in "@g.us", people in "@s.whatsapp.net". On phones
    // where it posts only a summary ("Test group: 23 new messages"), that id on the shortcut, the
    // tag or the group key is the one sign that the chat is a group.
    fun isWhatsAppGroupId(sbn: StatusBarNotification): Boolean =
        listOf(sbn.notification.shortcutId, sbn.tag, sbn.groupKey).any { it?.contains(WHATSAPP_GROUP_SUFFIX) == true }

    private const val WHATSAPP_GROUP_SUFFIX = "@g.us"

    @Synchronized
    fun reset() {
        pings.clear()
    }

    private fun recentPings(senderHash: String?, postedAt: Long, windowMillis: Long, countsAsPing: Boolean): Int =
        when {
            senderHash == null -> 0
            countsAsPing -> pings.record(senderHash, postedAt, windowMillis)
            else -> pings.peek(senderHash, postedAt, windowMillis)
        }

    // Salted, so the stored hash cannot be matched against a list of names.
    private fun hashSender(name: String, salt: String): String = keyFor(name, salt, "s_")

    // The same salted hash for any name worth remembering without storing it (a group).
    fun keyFor(name: String, salt: String, prefix: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$name".toByteArray())
        return prefix + digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
