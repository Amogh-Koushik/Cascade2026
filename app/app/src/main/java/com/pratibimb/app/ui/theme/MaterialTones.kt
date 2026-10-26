package com.pratibimb.app.ui.theme

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Material 3 dark colour schemes from one seed colour, as ARGB ints. A tone is CIELAB L*
// (0 black, 100 white), the same lightness scale Material's HCT uses; hue and chroma are
// CIELAB's, a close stand-in for HCT's. The palettes follow Material's core palette: primary
// at the seed's hue with chroma at least 48, secondary at chroma 16, tertiary 60 degrees round
// at 24, neutrals at 4 and 8, and a fixed red error palette. Plain Kotlin, so it runs in JVM tests.
object MaterialTones {
    data class Lch(val l: Double, val c: Double, val h: Double)

    class TonalPalette(val hue: Double, val chroma: Double) {
        fun tone(t: Int): Int = toneArgb(hue, chroma, t.toDouble())
    }

    // Every role the app's dark scheme uses, at Material 3's dark-scheme tones.
    data class DarkScheme(
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val inversePrimary: Int,
        val secondary: Int,
        val onSecondary: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
        val tertiary: Int,
        val onTertiary: Int,
        val tertiaryContainer: Int,
        val onTertiaryContainer: Int,
        val error: Int,
        val onError: Int,
        val errorContainer: Int,
        val onErrorContainer: Int,
        val surface: Int,
        val onSurface: Int,
        val surfaceVariant: Int,
        val onSurfaceVariant: Int,
        val surfaceContainerLowest: Int,
        val surfaceContainerLow: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val surfaceContainerHighest: Int,
        val surfaceBright: Int,
        val inverseSurface: Int,
        val inverseOnSurface: Int,
        val outline: Int,
        val outlineVariant: Int,
    )

    private const val ERROR_HUE = 25.0
    private const val ERROR_CHROMA = 84.0

    fun darkScheme(seed: Int): DarkScheme {
        val source = argbToLch(seed)
        val primary = TonalPalette(source.h, max(source.c, 48.0))
        val secondary = TonalPalette(source.h, 16.0)
        val tertiary = TonalPalette((source.h + 60.0) % 360.0, 24.0)
        val neutral = TonalPalette(source.h, 4.0)
        val neutralVariant = TonalPalette(source.h, 8.0)
        val error = TonalPalette(ERROR_HUE, ERROR_CHROMA)
        return DarkScheme(
            primary = primary.tone(80),
            onPrimary = primary.tone(20),
            primaryContainer = primary.tone(30),
            onPrimaryContainer = primary.tone(90),
            inversePrimary = primary.tone(40),
            secondary = secondary.tone(80),
            onSecondary = secondary.tone(20),
            secondaryContainer = secondary.tone(30),
            onSecondaryContainer = secondary.tone(90),
            tertiary = tertiary.tone(80),
            onTertiary = tertiary.tone(20),
            tertiaryContainer = tertiary.tone(30),
            onTertiaryContainer = tertiary.tone(90),
            error = error.tone(80),
            onError = error.tone(20),
            errorContainer = error.tone(30),
            onErrorContainer = error.tone(90),
            surface = neutral.tone(6),
            onSurface = neutral.tone(90),
            surfaceVariant = neutralVariant.tone(30),
            onSurfaceVariant = neutralVariant.tone(80),
            surfaceContainerLowest = neutral.tone(4),
            surfaceContainerLow = neutral.tone(10),
            surfaceContainer = neutral.tone(12),
            surfaceContainerHigh = neutral.tone(17),
            surfaceContainerHighest = neutral.tone(22),
            surfaceBright = neutral.tone(24),
            inverseSurface = neutral.tone(90),
            inverseOnSurface = neutral.tone(20),
            outline = neutralVariant.tone(60),
            outlineVariant = neutralVariant.tone(30),
        )
    }

    // The colour at this hue and tone with the most chroma up to the one asked for that sRGB
    // can show; saturated hues run out of gamut near black and white.
    fun toneArgb(hue: Double, chroma: Double, tone: Double): Int {
        lchToArgb(tone, chroma, hue)?.let { return it }
        var low = 0.0
        var high = chroma
        repeat(24) {
            val mid = (low + high) / 2
            if (lchToArgb(tone, mid, hue) != null) low = mid else high = mid
        }
        return lchToArgb(tone, low, hue) ?: grey(tone)
    }

    fun toneOf(argb: Int): Double = argbToLch(argb).l

    // WCAG 2 contrast ratio, 1 to 21.
    fun contrast(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    fun argbToLch(argb: Int): Lch {
        val r = linear((argb shr 16) and 0xFF)
        val g = linear((argb shr 8) and 0xFF)
        val b = linear(argb and 0xFF)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / WHITE_X
        val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / WHITE_Y
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / WHITE_Z
        val fx = labF(x)
        val fy = labF(y)
        val fz = labF(z)
        val l = 116 * fy - 16
        val a = 500 * (fx - fy)
        val bb = 200 * (fy - fz)
        val h = Math.toDegrees(atan2(bb, a)).let { if (it < 0) it + 360 else it }
        return Lch(l, hypot(a, bb), h)
    }

    // Null when the colour is outside sRGB.
    fun lchToArgb(l: Double, c: Double, h: Double): Int? {
        val radians = Math.toRadians(h)
        val a = c * cos(radians)
        val b = c * sin(radians)
        val fy = (l + 16) / 116
        val fx = fy + a / 500
        val fz = fy - b / 200
        val x = labFInverse(fx) * WHITE_X
        val y = labFInverse(fy) * WHITE_Y
        val z = labFInverse(fz) * WHITE_Z
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        val channels = listOf(r, g, bl)
        if (channels.any { it < -GAMUT_SLACK || it > 1 + GAMUT_SLACK }) return null
        val (rr, gg, bb) = channels.map { gamma(it.coerceIn(0.0, 1.0)) }
        return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    private fun grey(tone: Double): Int {
        val v = gamma(labFInverse((tone + 16) / 116))
        return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    private fun relativeLuminance(argb: Int): Double =
        0.2126 * linear((argb shr 16) and 0xFF) + 0.7152 * linear((argb shr 8) and 0xFF) + 0.0722 * linear(argb and 0xFF)

    private fun linear(channel: Int): Double {
        val v = channel / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun gamma(v: Double): Int {
        val encoded = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055
        return (encoded * 255).roundToInt().coerceIn(0, 255)
    }

    private fun labF(t: Double): Double = if (t > EPSILON) cbrt(t) else (KAPPA * t + 16) / 116

    private fun labFInverse(f: Double): Double {
        val cubed = f * f * f
        return if (cubed > EPSILON) cubed else (116 * f - 16) / KAPPA
    }

    // D65 white, and the CIELAB constants.
    private const val WHITE_X = 0.95047
    private const val WHITE_Y = 1.0
    private const val WHITE_Z = 1.08883
    private const val EPSILON = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    // Rounding in the matrices puts in-gamut colours a hair outside [0, 1].
    private const val GAMUT_SLACK = 1e-4
}
