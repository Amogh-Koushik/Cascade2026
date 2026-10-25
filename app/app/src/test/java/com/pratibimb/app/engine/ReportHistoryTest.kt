package com.pratibimb.app.engine

import com.pratibimb.twin.FatigueRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportHistoryTest {
    @Test
    fun bestHoursNeedTenMinutesOfSteadyFocus() {
        val little = ReportHistory().withFocusTime(10, restless = false, seconds = 300)

        assertNull(little.bestFocusWindow())
    }

    @Test
    fun bestHoursAreTheRunWithTheMostSteadyFocus() {
        val history = ReportHistory()
            .withFocusTime(9, false, 1200)
            .withFocusTime(10, false, 1800)
            .withFocusTime(11, false, 1500)
            .withFocusTime(22, false, 900)
            .withFocusTime(10, true, 5000)

        assertEquals(9..11, history.bestFocusWindow())
        assertEquals((1200 + 1800 + 1500 + 900 + 5000) / 60.0, history.focusMinutesRecorded, 1e-9)
    }

    @Test
    fun appsAreRankedByMinutesLostThenSwitches() {
        val history = ReportHistory()
            .withOpen("instagram", "social", 12.0)
            .withOpen("instagram", "social", 6.0)
            .withOpen("whatsapp", "messaging", 4.0)
            .withSwitch("youtube").withSwitch("youtube").withSwitch("youtube")
            .withOpen("gmail", "work_email", null)

        val apps = history.fragmentingApps()

        assertEquals(listOf("instagram", "whatsapp", "youtube"), apps.map { it.packageName })
        assertEquals(18.0, apps[0].minutesLost, 1e-9)
        assertEquals(2, apps[0].opens)
        assertEquals(3, apps[2].switches)
    }

    @Test
    fun untimedOpensCountButAddNoMinutes() {
        val history = ReportHistory().withOpen("gmail", "work_email", null)

        assertEquals(1, history.opensByCategory["work_email"])
        assertEquals(emptyList<CategoryLength>(), history.longRunningCategories())
    }

    @Test
    fun appsThatMatterUseTheShareOfWorthTaps() {
        val history = ReportHistory()
            .withWorth("whatsapp", true).withWorth("whatsapp", true).withWorth("whatsapp", false)
            .withWorth("gmail", true)
            .withWorth("instagram", false)

        val apps = history.appsThatMatter()

        assertEquals(listOf("gmail", "whatsapp", "instagram"), apps.map { it.packageName })
        assertEquals(2.0 / 3, apps[1].share, 1e-9)
    }

    @Test
    fun fatigueIsTheAverageLevelOfEachHoursReadings() {
        val history = ReportHistory()
            .withFatigue(23, FatigueRisk.HIGH)
            .withFatigue(23, FatigueRisk.LOW)

        val byHour = history.fatigueByHour()

        assertEquals(1.0, byHour[23]!!, 1e-9)
        assertNull(byHour[12])
    }

    @Test
    fun theHistorySurvivesTheRoundTrip() {
        val history = ReportHistory(sinceMillis = 1_792_000_000_000L)
            .withFocusTime(9, false, 900)
            .withFocusTime(23, true, 60)
            .withFatigue(23, FatigueRisk.MEDIUM)
            .withOpen("com.instagram.android", "social", 7.5)
            .withSwitch("com.google.android.youtube")
            .withDecision("com.instagram.android", held = true)
            .withDecision("com.whatsapp", held = false)
            .withWorth("com.whatsapp", true)

        assertEquals(history, ReportHistory.fromJson(history.toJson()))
    }
}
