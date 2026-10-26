package com.pratibimb.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The colour tokens every screen uses. They read the active palette (AppPalette), which
// follows the accent picked in Settings; Compose redraws whatever read a token when it changes.
// The name is the original palette's, kept so the screens did not have to change.
object WarmObsidian {
    var palette: AppPalette by mutableStateOf(AppPalette.Original)
        internal set

    val Background: Color get() = palette.surface
    val Surface: Color get() = palette.surface
    val SurfaceContainer: Color get() = palette.surfaceContainer
    val SurfaceContainerHigh: Color get() = palette.surfaceContainerHigh
    val SurfaceContainerHighest: Color get() = palette.surfaceContainerHighest

    val Primary: Color get() = palette.primary
    val OnPrimary: Color get() = palette.onPrimary
    val PrimaryContainer: Color get() = palette.primaryContainer
    val OnPrimaryContainer: Color get() = palette.onPrimaryContainer

    val Secondary: Color get() = palette.secondary
    val OnSecondary: Color get() = palette.onSecondary
    val SecondaryContainer: Color get() = palette.secondaryContainer
    val OnSecondaryContainer: Color get() = palette.onSecondaryContainer

    val Tertiary: Color get() = palette.tertiary
    val OnTertiary: Color get() = palette.onTertiary
    val TertiaryContainer: Color get() = palette.tertiaryContainer

    val OnSurface: Color get() = palette.onSurface
    val OnSurfaceVariant: Color get() = palette.onSurfaceVariant
    val Outline: Color get() = palette.outline
    val OutlineVariant: Color get() = palette.outlineVariant

    val Error: Color get() = palette.error
    val OnError: Color get() = palette.onError
    val ErrorContainer: Color get() = palette.errorContainer

    // Status colours mean the same thing under every accent, so they stay fixed.
    val StatusMint = Color(0xFF86EFAC)
    val StatusLavender = Color(0xFFC4B5FD)
    val StatusCoral = Color(0xFFC97D60)
    val StatusError = Color(0xFFB83232)
}

// Nunito Sans is a Google Font; we load it at runtime via downloadable fonts.
// For now we keep the system default so the app builds without a bundled .ttf.
// Once the font file is added to res/font/, replace `FontFamily.Default` below.
val NunitoSans = FontFamily.Default   // TODO: swap with bundled Nunito Sans

@Composable
fun PratibimbTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    remember { AccentSettings.init(context) }
    MaterialTheme(
        colorScheme = WarmObsidian.palette.toColorScheme(),
        content = content,
    )
}
