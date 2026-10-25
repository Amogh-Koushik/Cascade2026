package com.pratibimb.app.engine

import java.util.Locale

// Which app family a notification comes from, for the focus rules.
enum class AppFamily { INSTAGRAM, SNAPCHAT, WHATSAPP, TELEGRAM, SMS, OTHER }

// Facts about one notification that the focus rules need beyond the engine's features. Read
// from the package, title, text and Android's own flags. Plain Kotlin, so it runs in JVM tests.
object MessageSignals {
    private val SMS_APPS = setOf("com.google.android.apps.messaging", "com.samsung.android.messaging")

    fun familyOf(packageName: String): AppFamily = when (packageName) {
        "com.instagram.android" -> AppFamily.INSTAGRAM
        "com.snapchat.android" -> AppFamily.SNAPCHAT
        "com.whatsapp", "com.whatsapp.w4b" -> AppFamily.WHATSAPP
        "org.telegram.messenger" -> AppFamily.TELEGRAM
        in SMS_APPS -> AppFamily.SMS
        else -> AppFamily.OTHER
    }

    // Order or ride progress: Android marks it ongoing, a progress bar or transport, or the
    // text names a step of the order.
    private val STATUS_WORDS = listOf(
        "order placed", "order confirmed", "order is confirmed", "is being prepared", "preparing your order",
        "out for delivery", "on the way", "on its way", "arriving", "arrived", "delivered", "picked up",
        "rider", "delivery partner", "has been shipped", "shipped", "reached your location", "order is ready",
    )
    private val STATUS_CATEGORIES = setOf("progress", "transport", "status", "navigation")

    fun isOrderStatus(androidCategory: String?, hasProgress: Boolean, isOngoing: Boolean, title: String, text: String): Boolean {
        if (isOngoing || hasProgress || androidCategory in STATUS_CATEGORIES) return true
        val haystack = "$title $text".lowercase(Locale.ROOT)
        return STATUS_WORDS.any { it in haystack }
    }

    // A bank or UPI alert about money moving: from SMS or a payments app, with banking words.
    private val BANK_WORDS = listOf(
        "debited", "credited", "a/c", "acct", "account", "txn", "transaction", "upi", "balance",
        "withdrawn", "neft", "imps", "rtgs", "card ending", "spent",
    )

    fun isBankAlert(packageName: String, paymentPackages: Collection<String>, title: String, text: String): Boolean {
        if (packageName !in SMS_APPS && packageName !in paymentPackages) return false
        val haystack = "$title $text".lowercase(Locale.ROOT)
        return BANK_WORDS.any { Regex("(?<![a-z])${Regex.escape(it)}(?![a-z])").containsMatchIn(haystack) }
    }

    // Business SMS sender IDs look like "AX-SWIGGY" or "VM-AIRTEL-S".
    private val BUSINESS_SMS_ID = Regex("^[A-Z]{2}-[A-Z0-9]{3,9}(-[A-Z])?$")

    // Telegram bots (a name ending in "bot") and business SMS sender IDs. WhatsApp gives no
    // flag that marks a business chat, so those are not detected here.
    fun isBot(family: AppFamily, title: String): Boolean = when (family) {
        AppFamily.TELEGRAM -> title.trim().lowercase(Locale.ROOT).endsWith("bot")
        AppFamily.SMS -> BUSINESS_SMS_ID.matches(title.trim())
        else -> false
    }

    fun isStreak(text: String): Boolean = "streak" in text.lowercase(Locale.ROOT)
}

// Text messages per sender inside a window, for "5 texts in 2 minutes" (each message counts,
// unlike the repeat-ping override's bursts). Plain Kotlin, so it runs in JVM tests.
class TextBurstCounter(private val windowMillis: Long = 2 * 60_000L) {
    private val times = HashMap<String, ArrayDeque<Long>>()

    // Messages from this sender inside the window, this one included.
    @Synchronized
    fun record(senderKey: String, atMillis: Long): Int {
        val queue = times.getOrPut(senderKey) { ArrayDeque() }
        queue.addLast(atMillis)
        while (queue.isNotEmpty() && atMillis - queue.first() > windowMillis) queue.removeFirst()
        return queue.size
    }

    @Synchronized
    fun clear() {
        times.clear()
    }
}
