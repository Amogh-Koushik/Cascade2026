package com.pratibimb.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DemoReportTest {
    // Gradle runs unit tests from the module folder; walk up until the repo's contracts/.
    private val file: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "contracts/demo/${DemoReport.ASSET}") }
        .first { it.isFile }

    private val demo = DemoReport.parse(file.readText())

    @Test
    fun bundledWeekHasEnoughFocusToFillEveryCard() {
        val history = demo.history
        assertTrue(history.focusMinutesRecorded > 5.0)
        assertNotNull(history.bestFocusWindow())
        assertTrue(history.fragmentingApps().isNotEmpty())
        assertTrue(history.longRunningCategories().isNotEmpty())
        assertTrue(history.appsThatMatter().isNotEmpty())
        assertTrue(history.fatigueByHour().any { it != null })
        assertTrue(history.heldByApp.isNotEmpty() && history.deliveredByApp.isNotEmpty())
    }

    @Test
    fun everyAppInTheWeekHasADisplayName() {
        val history = demo.history
        val packages = history.heldByApp.keys + history.deliveredByApp.keys + history.switchesToApp.keys
        assertTrue(demo.appLabels.keys.containsAll(packages))
    }

    @Test
    fun historySurvivesTheReportStoreRoundTrip() {
        assertEquals(demo.history, ReportHistory.fromJson(demo.history.toJson()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unlabelledFileIsRejected() {
        DemoReport.parse(file.readText().replace("\"label\": \"simulated\"", "\"label\": \"real\""))
    }
}
