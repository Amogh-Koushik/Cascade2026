package com.pratibimb.app.engine

import com.pratibimb.twin.Category
import com.pratibimb.twin.FeedbackEvent
import com.pratibimb.twin.PhoneState
import java.util.concurrent.ConcurrentHashMap

// A delivered notification the user tapped. The minutes it cost are only known once the user
// leaves that app, so the outcome is built later with outcome(minutesLost).
data class OpenedNotification(
    val packageName: String,
    val category: Category,
    val state: PhoneState,
    val openedAtMillis: Long,
    // A tap after the open window closed counts as not opened.
    val inWindow: Boolean,
) {
    fun outcome(minutesLost: Double?): FeedbackEvent.PullOutcome =
        FeedbackEvent.PullOutcome(
            category = category,
            state = state,
            opened = inWindow,
            minutesLost = if (inWindow) minutesLost else null,
            releasedFromTray = false,
        )
}

// Remembers delivered notifications until their pull outcome is known: opened within the open
// window, or not. Without the "not opened" side the twin would only ever learn that things get
// opened, and every pull estimate would drift upward. Plain Kotlin, so it runs in JVM tests.
class OutcomeTracker(@Volatile var openWindowMillis: Long) {
    // The state is the one at delivery, because that is the cell the outcome belongs to.
    private data class Delivered(
        val packageName: String,
        val category: Category,
        val state: PhoneState,
        val atMillis: Long,
    )

    private val pending = ConcurrentHashMap<String, Delivered>()

    val pendingCount: Int get() = pending.size

    fun delivered(key: String, packageName: String, category: Category, state: PhoneState, atMillis: Long) {
        pending[key] = Delivered(packageName, category, state, atMillis)
    }

    fun opened(key: String, nowMillis: Long): OpenedNotification? {
        val item = pending.remove(key) ?: return null
        return OpenedNotification(
            packageName = item.packageName,
            category = item.category,
            state = item.state,
            openedAtMillis = nowMillis,
            inWindow = nowMillis - item.atMillis <= openWindowMillis,
        )
    }

    fun dismissed(key: String): FeedbackEvent.PullOutcome? =
        pending.remove(key)?.let { notOpened(it) }

    // Removed for a reason that says nothing about the user, such as the app cancelling it.
    fun forget(key: String) {
        pending.remove(key)
    }

    fun expired(nowMillis: Long): List<FeedbackEvent.PullOutcome> {
        val events = mutableListOf<FeedbackEvent.PullOutcome>()
        for ((key, item) in pending) {
            if (nowMillis - item.atMillis > openWindowMillis && pending.remove(key, item)) {
                events += notOpened(item)
            }
        }
        return events
    }

    fun clear() {
        pending.clear()
    }

    private fun notOpened(item: Delivered) =
        FeedbackEvent.PullOutcome(
            category = item.category,
            state = item.state,
            opened = false,
            minutesLost = null,
            releasedFromTray = false,
        )
}
