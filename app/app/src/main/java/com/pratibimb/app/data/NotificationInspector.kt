package com.pratibimb.app.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pratibimb.app.engine.ContentFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// What Android actually reports for each notification, next to what the twin made of it: the
// raw facts every content rule is built on. In memory only, never written to disk.
data class InspectedPost(
    val id: Long,
    val timeMillis: Long,
    val appLabel: String,
    val packageName: String,
    val title: String,
    val channelId: String,
    val channelName: String?,
    val importance: Int?,
    val androidCategory: String?,
    val isConversation: Boolean?,
    val isGroup: Boolean,
    // Messages carried in a MessagingStyle notification; null when the app does not use it.
    val messageCount: Int?,
    val template: String?,
    val hasPicture: Boolean,
    val content: ContentFeatures,
    // What happened: the decision line, or why it was skipped.
    val outcome: String,
)

object NotificationInspector {
    private const val MAX_POSTS = 60

    // OTP and banking codes never leave the listener in readable form (see the consent screen).
    private val CODE = Regex("\\d{4,8}")

    private var nextId = 0L
    private val _posts = MutableStateFlow<List<InspectedPost>>(emptyList())
    val posts: StateFlow<List<InspectedPost>> = _posts.asStateFlow()

    @Synchronized
    fun record(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        appLabel: String,
        title: String,
        content: ContentFeatures,
        outcome: String,
    ) {
        val ranking = NotificationListenerService.Ranking().takeIf { rankingMap.getRanking(sbn.key, it) }
        val notification = sbn.notification
        val extras = notification.extras
        // The typed getParcelableArray is unreliable on Android 13, so the untyped one is used.
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val post = InspectedPost(
            id = ++nextId,
            timeMillis = sbn.postTime,
            appLabel = appLabel,
            packageName = sbn.packageName,
            title = CODE.replace(title, "••••"),
            channelId = ranking?.channel?.id ?: notification.channelId.orEmpty(),
            channelName = ranking?.channel?.name?.toString(),
            importance = ranking?.importance,
            androidCategory = notification.category,
            isConversation = ranking?.isConversation,
            isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false),
            messageCount = messages?.size,
            template = extras.getString(Notification.EXTRA_TEMPLATE)?.substringAfterLast('$')?.substringAfterLast('.'),
            hasPicture = extras.containsKey(Notification.EXTRA_PICTURE),
            content = content,
            outcome = outcome,
        )
        _posts.update { (listOf(post) + it).take(MAX_POSTS) }
    }

    fun clear() {
        _posts.value = emptyList()
    }
}
