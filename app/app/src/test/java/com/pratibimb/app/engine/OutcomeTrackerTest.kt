package com.pratibimb.app.engine

import com.pratibimb.twin.Category
import com.pratibimb.twin.PhoneState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeTrackerTest {
    private val window = 10 * 60_000L

    private fun trackerWithOne(): OutcomeTracker =
        OutcomeTracker(window).apply {
            delivered("k", "com.instagram.android", Category.SOCIAL, PhoneState.FOCUS_STEADY, 0L)
        }

    @Test
    fun tapInsideTheWindowIsAnOpenWithMinutes() {
        val open = trackerWithOne().opened("k", window)!!
        val outcome = open.outcome(5.0)

        assertEquals("com.instagram.android", open.packageName)
        assertEquals(window, open.openedAtMillis)
        assertTrue(outcome.opened)
        assertEquals(5.0, outcome.minutesLost!!, 0.0)
        assertEquals(Category.SOCIAL, outcome.category)
        assertEquals(PhoneState.FOCUS_STEADY, outcome.state)
        assertFalse(outcome.releasedFromTray)
    }

    @Test
    fun tapAfterTheWindowCountsAsNotOpened() {
        val outcome = trackerWithOne().opened("k", window + 1)!!.outcome(5.0)

        assertFalse(outcome.opened)
        assertNull(outcome.minutesLost)
    }

    @Test
    fun dismissIsNotOpened() {
        val outcome = trackerWithOne().dismissed("k")!!

        assertFalse(outcome.opened)
        assertNull(outcome.minutesLost)
    }

    @Test
    fun sweepReturnsOnlyItemsPastTheWindowAndOnlyOnce() {
        val tracker = trackerWithOne()
        tracker.delivered("late", "com.news", Category.NEWS, PhoneState.FREE, window)

        val first = tracker.expired(window + 1)
        val second = tracker.expired(window + 1)

        assertEquals(listOf(Category.SOCIAL), first.map { it.category })
        assertTrue(second.isEmpty())
        assertEquals(1, tracker.pendingCount)
    }

    @Test
    fun unknownOrForgottenKeysGiveNoOutcome() {
        val tracker = trackerWithOne()
        tracker.forget("k")

        assertNull(tracker.opened("k", 1L))
        assertNull(tracker.dismissed("other"))
        assertTrue(tracker.expired(Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun stateIsTheOneAtTheLatestDelivery() {
        val tracker = OutcomeTracker(window)
        tracker.delivered("k", "com.whatsapp", Category.MESSAGING, PhoneState.FREE, 0L)
        tracker.delivered("k", "com.whatsapp", Category.MESSAGING, PhoneState.FOCUS_RESTLESS, 1L)

        assertEquals(PhoneState.FOCUS_RESTLESS, tracker.dismissed("k")!!.state)
    }
}
