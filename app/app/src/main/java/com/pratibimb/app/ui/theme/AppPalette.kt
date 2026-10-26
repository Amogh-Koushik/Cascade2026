package com.pratibimb.app.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// One complete set of Material 3 colour roles. Every screen reads these through WarmObsidian
// and the M3 components read them through MaterialTheme, so switching the palette recolours
// the whole app.
data class AppPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceBright: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val outline: Color,
    val outlineVariant: Color,
) {
    fun toColorScheme(): ColorScheme = darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceBright = surfaceBright,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceDim = surface,
    )

    companion object {
        // Warm Obsidian, the app's original palette: its hand-picked colours stay as they were,
        // and the roles it never had (containers, the lowest and brightest surfaces) are toned
        // from its own terracotta, mustard, coral and brown.
        val Original: AppPalette = run {
            fun palette(argb: Long) = MaterialTones.argbToLch(argb.toInt()).let { MaterialTones.TonalPalette(it.h, it.c) }
            val terracotta = palette(0xFFD98C4E)
            val mustard = palette(0xFFEEC154)
            val coral = palette(0xFFFFB59A)
            val brown = palette(0xFF2C1C0A)
            AppPalette(
                primary = Color(0xFFFFB781),
                onPrimary = Color(0xFF4E2600),
                primaryContainer = Color(terracotta.tone(30)),
                onPrimaryContainer = Color(terracotta.tone(90)),
                inversePrimary = Color(terracotta.tone(40)),
                secondary = Color(0xFFEEC154),
                onSecondary = Color(0xFF3F2E00),
                secondaryContainer = Color(mustard.tone(30)),
                onSecondaryContainer = Color(mustard.tone(90)),
                tertiary = Color(0xFFFFB59A),
                onTertiary = Color(0xFF55200A),
                tertiaryContainer = Color(coral.tone(30)),
                onTertiaryContainer = Color(coral.tone(90)),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),
                surface = Color(0xFF1E1002),
                onSurface = Color(0xFFFCDDC0),
                surfaceVariant = Color(0xFF43311D),
                onSurfaceVariant = Color(0xFFD8C2B5),
                surfaceContainerLowest = Color(brown.tone(4)),
                surfaceContainerLow = Color(brown.tone(10)),
                surfaceContainer = Color(0xFF2C1C0A),
                surfaceContainerHigh = Color(0xFF382714),
                surfaceContainerHighest = Color(0xFF43311D),
                surfaceBright = Color(brown.tone(24)),
                inverseSurface = Color(0xFFFCDDC0),
                inverseOnSurface = Color(brown.tone(20)),
                outline = Color(0xFFA08D80),
                outlineVariant = Color(0xFF534439),
            )
        }

        // A mark that reads on any fill: dark on light colours, light on dark ones.
        fun onColorFor(fill: Color): Color =
            if (MaterialTones.toneOf(fill.toArgb()) >= 60) Color(0xFF1B1B1B) else Color.White

        fun fromSeed(seed: Int): AppPalette = fromTones(MaterialTones.darkScheme(seed))

        fun forAccent(accent: Accent, context: Context): AppPalette = when {
            accent == Accent.WALLPAPER -> fromColorScheme(dynamicDarkColorScheme(context))
            accent.seed != null -> fromSeed(accent.seed)
            else -> Original
        }

        private fun fromTones(s: MaterialTones.DarkScheme) = AppPalette(
            primary = Color(s.primary),
            onPrimary = Color(s.onPrimary),
            primaryContainer = Color(s.primaryContainer),
            onPrimaryContainer = Color(s.onPrimaryContainer),
            inversePrimary = Color(s.inversePrimary),
            secondary = Color(s.secondary),
            onSecondary = Color(s.onSecondary),
            secondaryContainer = Color(s.secondaryContainer),
            onSecondaryContainer = Color(s.onSecondaryContainer),
            tertiary = Color(s.tertiary),
            onTertiary = Color(s.onTertiary),
            tertiaryContainer = Color(s.tertiaryContainer),
            onTertiaryContainer = Color(s.onTertiaryContainer),
            error = Color(s.error),
            onError = Color(s.onError),
            errorContainer = Color(s.errorContainer),
            onErrorContainer = Color(s.onErrorContainer),
            surface = Color(s.surface),
            onSurface = Color(s.onSurface),
            surfaceVariant = Color(s.surfaceVariant),
            onSurfaceVariant = Color(s.onSurfaceVariant),
            surfaceContainerLowest = Color(s.surfaceContainerLowest),
            surfaceContainerLow = Color(s.surfaceContainerLow),
            surfaceContainer = Color(s.surfaceContainer),
            surfaceContainerHigh = Color(s.surfaceContainerHigh),
            surfaceContainerHighest = Color(s.surfaceContainerHighest),
            surfaceBright = Color(s.surfaceBright),
            inverseSurface = Color(s.inverseSurface),
            inverseOnSurface = Color(s.inverseOnSurface),
            outline = Color(s.outline),
            outlineVariant = Color(s.outlineVariant),
        )

        private fun fromColorScheme(c: ColorScheme) = AppPalette(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            inversePrimary = c.inversePrimary,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            error = c.error,
            onError = c.onError,
            errorContainer = c.errorContainer,
            onErrorContainer = c.onErrorContainer,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariant,
            surfaceContainerLowest = c.surfaceContainerLowest,
            surfaceContainerLow = c.surfaceContainerLow,
            surfaceContainer = c.surfaceContainer,
            surfaceContainerHigh = c.surfaceContainerHigh,
            surfaceContainerHighest = c.surfaceContainerHighest,
            surfaceBright = c.surfaceBright,
            inverseSurface = c.inverseSurface,
            inverseOnSurface = c.inverseOnSurface,
            outline = c.outline,
            outlineVariant = c.outlineVariant,
        )
    }
}
