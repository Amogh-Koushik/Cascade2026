package com.pratibimb.app.engine

import com.pratibimb.twin.FatigueRisk
import org.junit.Assert.assertEquals
import org.junit.Test

class FatigueRulesTest {
    @Test
    fun aFreshDaytimeSessionIsLow() {
        val result = FatigueRules.assess(20.0, 2, 4, 20.0, 14)

        assertEquals(FatigueRisk.LOW, result.risk)
        assertEquals(emptyList<String>(), result.reasons)
    }

    @Test
    fun ninetyMinutesWithoutABreakIsOnePoint() {
        val result = FatigueRules.assess(110.0, 2, 20, 110.0, 14)

        assertEquals(FatigueRisk.MEDIUM, result.risk)
        assertEquals(listOf("1 h 50 min since your last break"), result.reasons)
    }

    @Test
    fun switchingWellAboveTheSessionAverageCounts() {
        // 12 switches in 60 min averages 6 per 30 min; 9 recent is exactly 1.5x.
        val rising = FatigueRules.assess(60.0, 9, 12, 60.0, 14)
        val steady = FatigueRules.assess(60.0, 8, 12, 60.0, 14)

        assertEquals(FatigueRisk.MEDIUM, rising.risk)
        assertEquals(FatigueRisk.LOW, steady.risk)
    }

    @Test
    fun aRiseIsIgnoredEarlyInTheSessionOrWithFewSwitches() {
        assertEquals(FatigueRisk.LOW, FatigueRules.assess(20.0, 8, 8, 20.0, 14).risk)
        assertEquals(FatigueRisk.LOW, FatigueRules.assess(60.0, 3, 3, 60.0, 14).risk)
    }

    @Test
    fun lateHoursCountFromElevenToFive() {
        assertEquals(FatigueRisk.MEDIUM, FatigueRules.assess(10.0, 0, 0, 10.0, 23).risk)
        assertEquals(FatigueRisk.MEDIUM, FatigueRules.assess(10.0, 0, 0, 10.0, 4).risk)
        assertEquals(FatigueRisk.LOW, FatigueRules.assess(10.0, 0, 0, 10.0, 5).risk)
    }

    @Test
    fun twoOrMorePointsIsHigh() {
        val result = FatigueRules.assess(95.0, 0, 0, 95.0, 0)

        assertEquals(FatigueRisk.HIGH, result.risk)
        assertEquals(2, result.reasons.size)
    }

    @Test
    fun durationsReadNaturally() {
        assertEquals("45 min", FatigueRules.formatDuration(45.9))
        assertEquals("2 h", FatigueRules.formatDuration(120.0))
        assertEquals("1 h 5 min", FatigueRules.formatDuration(65.0))
    }
}
