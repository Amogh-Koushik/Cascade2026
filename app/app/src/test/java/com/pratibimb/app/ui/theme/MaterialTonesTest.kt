package com.pratibimb.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

class MaterialTonesTest {
    private val seeded = Accent.entries.filter { it.seed != null }

    @Test
    fun aToneLandsOnTheLightnessAskedFor() {
        for (hue in 0 until 360 step 15) {
            for (tone in listOf(4, 6, 12, 17, 22, 30, 60, 80, 90)) {
                val argb = MaterialTones.toneArgb(hue.toDouble(), 48.0, tone.toDouble())
                assertEquals("hue $hue tone $tone", tone.toDouble(), MaterialTones.toneOf(argb), 0.6)
            }
        }
    }

    @Test
    fun outOfGamutChromaIsReducedNotDropped() {
        // Deep blue at tone 90 cannot hold chroma 48; the colour stays blue, just paler.
        val argb = MaterialTones.toneArgb(300.0, 48.0, 90.0)
        val lch = MaterialTones.argbToLch(argb)
        assertTrue(lch.c in 1.0..48.0)
        assertTrue(hueDistance(lch.h, 300.0) < 5)
    }

    @Test
    fun everyAccentMeetsMaterialContrastForTextPairs() {
        for (accent in seeded) {
            val s = MaterialTones.darkScheme(accent.seed!!)
            val pairs = mapOf(
                "primary" to (s.primary to s.onPrimary),
                "primaryContainer" to (s.primaryContainer to s.onPrimaryContainer),
                "secondaryContainer" to (s.secondaryContainer to s.onSecondaryContainer),
                "tertiaryContainer" to (s.tertiaryContainer to s.onTertiaryContainer),
                "surface" to (s.surface to s.onSurface),
                "surfaceContainerHigh" to (s.surfaceContainerHigh to s.onSurfaceVariant),
                "error" to (s.error to s.onError),
            )
            for ((role, colours) in pairs) {
                val ratio = MaterialTones.contrast(colours.first, colours.second)
                assertTrue("${accent.label} $role contrast $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun surfacesStepUpInLightness() {
        for (accent in seeded) {
            val s = MaterialTones.darkScheme(accent.seed!!)
            val tones = listOf(
                s.surfaceContainerLowest,
                s.surface,
                s.surfaceContainerLow,
                s.surfaceContainer,
                s.surfaceContainerHigh,
                s.surfaceContainerHighest,
                s.surfaceBright,
            ).map(MaterialTones::toneOf)
            assertEquals(accent.label, tones.sorted(), tones)
        }
    }

    @Test
    fun primaryKeepsTheSeedsHue() {
        for (accent in seeded.filter { MaterialTones.argbToLch(it.seed!!).c > 30 }) {
            val seedHue = MaterialTones.argbToLch(accent.seed!!).h
            val primaryHue = MaterialTones.argbToLch(MaterialTones.darkScheme(accent.seed).primary).h
            assertTrue("${accent.label}: $seedHue vs $primaryHue", hueDistance(seedHue, primaryHue) < 8)
        }
    }

    @Test
    fun unknownSavedAccentFallsBackToTheOriginal() {
        assertEquals(Accent.ORIGINAL, Accent.fromKey(null))
        assertEquals(Accent.ORIGINAL, Accent.fromKey("NO_SUCH_COLOUR"))
        assertEquals(Accent.TEAL, Accent.fromKey("TEAL"))
    }

    private fun hueDistance(a: Double, b: Double): Double = abs(a - b).let { min(it, 360 - it) }
}
