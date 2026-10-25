package com.pratibimb.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Warm Obsidian colour palette ────────────────────────────────────────────
object WarmObsidian {
    val Background       = Color(0xFF1E1002)
    val Surface          = Color(0xFF1E1002)
    val SurfaceContainer = Color(0xFF2C1C0A)
    val SurfaceContainerHigh = Color(0xFF382714)
    val SurfaceContainerHighest = Color(0xFF43311D)
    val SurfaceDark      = Color(0xFF4A3B32)

    val Primary          = Color(0xFFFFB781)   // #ffb781 on-primary in M3 terms
    val PrimaryContainer = Color(0xFFD98C4E)   // terracotta — used as CTA
    val OnPrimary        = Color(0xFF4E2600)
    val OnPrimaryContainer = Color(0xFF552A00)

    val Secondary        = Color(0xFFEEC154)   // mustard
    val SecondaryContainer = Color(0xFFB38B22)
    val OnSecondary      = Color(0xFF3F2E00)

    val Tertiary         = Color(0xFFFFB59A)   // accent coral
    val TertiaryContainer = Color(0xFFD98A6C)
    val OnTertiary       = Color(0xFF55200A)

    val OnSurface        = Color(0xFFFCDDC0)   // cream parchment
    val OnSurfaceVariant = Color(0xFFD8C2B5)
    val Outline          = Color(0xFFA08D80)
    val OutlineVariant   = Color(0xFF534439)

    val Error            = Color(0xFFFFB4AB)
    val OnError          = Color(0xFF690005)
    val ErrorContainer   = Color(0xFF93000A)

    // Status
    val StatusMint       = Color(0xFF86EFAC)
    val StatusLavender   = Color(0xFFC4B5FD)
    val StatusCoral      = Color(0xFFC97D60)
    val StatusError      = Color(0xFFB83232)
}

private val DarkColorScheme = darkColorScheme(
    primary          = WarmObsidian.Primary,
    onPrimary        = WarmObsidian.OnPrimary,
    primaryContainer = WarmObsidian.PrimaryContainer,
    onPrimaryContainer = WarmObsidian.OnPrimaryContainer,
    secondary        = WarmObsidian.Secondary,
    onSecondary      = WarmObsidian.OnSecondary,
    secondaryContainer = WarmObsidian.SecondaryContainer,
    tertiary         = WarmObsidian.Tertiary,
    onTertiary       = WarmObsidian.OnTertiary,
    tertiaryContainer = WarmObsidian.TertiaryContainer,
    background       = WarmObsidian.Background,
    onBackground     = WarmObsidian.OnSurface,
    surface          = WarmObsidian.Surface,
    onSurface        = WarmObsidian.OnSurface,
    surfaceVariant   = WarmObsidian.SurfaceContainerHighest,
    onSurfaceVariant = WarmObsidian.OnSurfaceVariant,
    outline          = WarmObsidian.Outline,
    outlineVariant   = WarmObsidian.OutlineVariant,
    error            = WarmObsidian.Error,
    onError          = WarmObsidian.OnError,
    errorContainer   = WarmObsidian.ErrorContainer,
)

// Nunito Sans is a Google Font; we load it at runtime via downloadable fonts.
// For now we keep the system default so the app builds without a bundled .ttf.
// Once the font file is added to res/font/, replace `FontFamily.Default` below.
val NunitoSans = FontFamily.Default   // TODO: swap with bundled Nunito Sans

@Composable
fun PratibimbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
