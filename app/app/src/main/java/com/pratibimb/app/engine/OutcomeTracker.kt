package com.pratibimb.app.engine

import com.pratibimb.twin.Category
import com.pratibimb.twin.FeedbackEvent
import com.pratibimb.twin.PhoneState
import java.util.concurrent.ConcurrentHashMap

// Remembers delivered notifications until their pull outcome is known: opened within the open
// window, or not. Without the "not opened" side the twin would only ever learn that things get
// opened, and every pull estimate would drift upward. Plain Kotlin, so it runs in JVM tests.
class OutcomeTracker(@Volatile var openWindowMillis: Long) {
    // The state is the one at delivery, because that is the cell the outcome belongs to.
    private data class Delivered(val category: Category, val state: PhoneState, val atMillis: Long)

    private val pending = ConcurrentHashMap<String, Delivered>()

    val pendingCount: Int get() = pending.size

    fun delivered(key: String, category: Category, state: PhoneState, atMillis: Long) {
        pending[key] = Delivered(category, state, atMillis)
    }

    // A tap after the window closed counts as not opened, the same as if the sweep had run first.
    fun opened(key: String, nowMillis: Long, minutesLost: Double): FeedbackEvent.PullOutcome? {
        val item = pending.remove(key) ?: return null
        val inWindow = nowMillis - item.atMillis <= openWindowMillis
        return outcome(item, opened = inWindow, minutesLost = if (inWindow) minutesLost else null)
    }

    fun dismissed(key: String): FeedbackEvent.PullOutcome? =
        pending.remove(key)?.let { outcome(it, opened = false, minutesLost = null) }

    // Removed for a reason that says nothing about the user, such as the app cancelling it.
    fun forget(key: String) {
        pending.remove(key)
    }

    fun expired(nowMillis: Long): List<FeedbackEvent.PullOutcome> {
        val events = mutableListOf<FeedbackEvent.PullOutcome>()
        for ((key, item) in pending) {
            if (nowMillis - item.atMillis > openWindowMillis && pending.remove(key, item)) {
                events += outcome(item, opened = false, minutesLost = null)
            }
        }
        return events
    }

    fun clear() {
        pending.clear()
    }

    private fun outcome(item: Delivered, opened: Boolean, minutesLost: Double?) =
        FeedbackEvent.PullOutcome(
            category = item.category,
            state = item.state,
            opened = opened,
            minutesLost = minutesLost,
            releasedFromTray = false,
        )
}
