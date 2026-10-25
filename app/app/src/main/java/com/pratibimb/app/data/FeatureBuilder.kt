package com.pratibimb.app.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pratibimb.twin.Category
import com.pratibimb.twin.NotificationFeatures

/**
 * Extracts a [NotificationFeatures] row from a live [StatusBarNotification].
 */
object FeatureBuilder {

    private val BAIT_REGEX = Regex(
        "like|liked|reacted|comment|share|watch|check out|trending|viral|" +
        "deal|offer|sale|discount|off|save|cashback|win|won|free|limited|flash",
        RegexOption.IGNORE_CASE
    )

    private val URGENCY_REGEX = Regex(
        "urgent|asap|immediately|right now|emergency|help|call me|" +
        "expires|expiring|last chance|hurry|today only|ends soon",
        RegexOption.IGNORE_CASE
    )

    private val OTP_REGEX = Regex(
        """\b(otp|one.?time|passcode|verification code|auth code)[^\d]{0,30}\d{4,8}|""" +
        """\b\d{4,8}\b.{0,20}(otp|one.?time|passcode|verification|auth code)""",
        RegexOption.IGNORE_CASE
    )

    private val PACKAGE_CATEGORY: Map<String, Category> = mapOf(
        "com.instagram.android"              to Category.SOCIAL,
        "com.facebook.katana"                to Category.SOCIAL,
        "com.twitter.android"                to Category.SOCIAL,
        "com.snapchat.android"               to Category.SOCIAL,
        "com.linkedin.android"               to Category.WORK_EMAIL,
        "com.whatsapp"                       to Category.MESSAGING,
        "org.telegram.messenger"             to Category.MESSAGING,
        "com.google.android.apps.messaging"  to Category.MESSAGING,
        "com.samsung.android.messaging"      to Category.MESSAGING,
        "com.google.android.gm"              to Category.WORK_EMAIL,
        "com.microsoft.office.outlook"       to Category.WORK_EMAIL,
        "com.google.android.youtube"         to Category.SHORT_VIDEO,
        "com.zhiliaoapp.musically"           to Category.SHORT_VIDEO,
        "com.flipkart.android"               to Category.SHOPPING_PROMO,
        "in.amazon.mShop.android.shopping"   to Category.SHOPPING_PROMO,
        "in.swiggy.android"                  to Category.SHOPPING_PROMO,
        "app.zomato"                         to Category.SHOPPING_PROMO,
        "com.phonepe.app"                    to Category.OTHER,
        "net.one97.paytm"                    to Category.OTHER,
        "com.google.android.apps.maps"       to Category.OTHER,
    )

    fun build(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        recentPackagePings: Map<String, Int>,
    ): NotificationFeatures {
        val ranking = NotificationListenerService.Ranking()
            .takeIf { rankingMap.getRanking(sbn.key, it) }
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val body  = "$title $text"

        val senderDisplayName: String? =
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?.takeIf { sbn.notification.category == Notification.CATEGORY_MESSAGE }

        val androidCat = notification.category
        val category: Category = when {
            androidCat == Notification.CATEGORY_MESSAGE   -> Category.MESSAGING
            androidCat == Notification.CATEGORY_EMAIL     -> Category.WORK_EMAIL
            androidCat == Notification.CATEGORY_PROMO     -> Category.SHOPPING_PROMO
            else -> PACKAGE_CATEGORY[sbn.packageName] ?: Category.OTHER
        }

        return NotificationFeatures(
            key               = sbn.key,
            postedAtMillis    = sbn.postTime,
            packageName       = sbn.packageName,
            category          = category,
            androidCategory   = androidCat,
            channelImportance = ranking?.importance ?: (notification.priority + 3),
            isOngoing         = sbn.isOngoing,
            isConversation    = ranking?.isConversation ?: false,
            priorityConversation = ranking?.channel?.isImportantConversation ?: false,
            groupChat         = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false),
            senderHash        = senderDisplayName?.hashCode()?.toString(),
            senderDisplayName = senderDisplayName,
            sameSenderRecentPings = recentPackagePings[sbn.packageName] ?: 0,
            baitHit           = BAIT_REGEX.containsMatchIn(body),
            urgencyHit        = URGENCY_REGEX.containsMatchIn(body),
            otpPattern        = OTP_REGEX.containsMatchIn(body),
        )
    }
}
