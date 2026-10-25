package com.pratibimb.app.engine

import com.pratibimb.app.engine.ForegroundKind.APP_RESUMED
import com.pratibimb.app.engine.ForegroundKind.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRulesTest {
    private val launcher = "com.launcher"
    private val shade = "com.android.systemui"
    private val minute = 60_000L

    private fun resumed(atMin: Double, pkg: String) =
        ForegroundEvent((atMin * minute).toLong(), pkg, APP_RESUMED)

    @Test
    fun goingHomeThenToAnotherAppIsOneSwitch() {
        val events = listOf(resumed(0.0, "ide"), resumed(1.0, launcher), resumed(1.1, "instagram"))

        assertEquals(1, ForegroundRules.countSwitches(events, setOf(launcher, shade)))
    }

    @Test
    fun returningToTheSameAppAfterTheShadeIsNoSwitch() {
        val events = listOf(resumed(0.0, "ide"), resumed(1.0, shade), resumed(1.2, "ide"))

        assertEquals(0, ForegroundRules.countSwitches(events, setOf(launcher, shade)))
    }

    @Test
    fun moreThanThreeSwitchesIsRestless() {
        val apps = listOf("ide", "instagram", "ide", "youtube", "ide")
        val events = apps.mapIndexed { i, app -> resumed(i.toDouble(), app) }
        val switches = ForegroundRules.countSwitches(events.shuffled(java.util.Random(1)), emptySet())

        assertEquals(4, switches)
        assertTrue(ForegroundRules.isRestless(switches))
        assertFalse(ForegroundRules.isRestless(3))
    }

    @Test
    fun visitEndsWhenAnotherAppComesToTheFront() {
        val events = listOf(
            resumed(0.0, "ide"),
            resumed(1.0, "instagram"),
            resumed(3.0, shade),
            resumed(7.0, "ide"),
        )

        val visit = ForegroundRules.visitAfter(events, "instagram", 1 * minute, setOf(shade))

        assertEquals(1 * minute, visit.enteredAtMillis)
        assertEquals(7 * minute, visit.leftAtMillis)
    }

    @Test
    fun visitEndsWhenTheScreenGoesOff() {
        val events = listOf(resumed(1.0, "instagram"), ForegroundEvent(4 * minute, "", SCREEN_OFF))

        assertEquals(4 * minute, ForegroundRules.visitAfter(events, "instagram", minute, emptySet()).leftAtMillis)
    }

    @Test
    fun appReachingTheFrontJustBeforeTheTapIsReportedStillCounts() {
        val events = listOf(resumed(0.99, "instagram"), resumed(2.0, "ide"))

        val visit = ForegroundRules.visitAfter(events, "instagram", minute, emptySet())

        assertEquals(2 * minute, visit.leftAtMillis)
    }

    @Test
    fun minutesLostRunsFromTheTapToLeavingAndIsCapped() {
        val left = AppVisit(enteredAtMillis = minute, leftAtMillis = 7 * minute)
        val longStay = AppVisit(enteredAtMillis = minute, leftAtMillis = 200 * minute)

        assertEquals(6.0, ForegroundRules.minutesLost(left, minute, 8 * minute, 60.0, 5.0, false)!!, 1e-9)
        assertEquals(60.0, ForegroundRules.minutesLost(longStay, minute, 201 * minute, 60.0, 5.0, false)!!, 1e-9)
    }

    @Test
    fun stillInTheAppMeansKeepWaitingUntilTheSessionEnds() {
        val inApp = AppVisit(enteredAtMillis = minute, leftAtMillis = null)

        assertNull(ForegroundRules.minutesLost(inApp, minute, 10 * minute, 60.0, 5.0, false))
        assertEquals(9.0, ForegroundRules.minutesLost(inApp, minute, 10 * minute, 60.0, 5.0, true)!!, 1e-9)
        assertEquals(60.0, ForegroundRules.minutesLost(inApp, 0L, 61 * minute, 60.0, 5.0, false)!!, 1e-9)
    }

    @Test
    fun anAppThatNeverShowsUpFallsBackToTheEstimate() {
        val unseen = AppVisit(enteredAtMillis = null, leftAtMillis = null)

        assertNull(ForegroundRules.minutesLost(unseen, 0L, minute, 60.0, 5.0, false))
        assertEquals(5.0, ForegroundRules.minutesLost(unseen, 0L, 3 * minute, 60.0, 5.0, false)!!, 0.0)
        assertEquals(5.0, ForegroundRules.minutesLost(unseen, 0L, minute, 60.0, 5.0, true)!!, 0.0)
    }
}
