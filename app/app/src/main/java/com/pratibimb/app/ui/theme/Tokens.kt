package com.pratibimb.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Shared shape tokens ────────────────────────────────────────────────────
val ShapeCard    = RoundedCornerShape(24.dp)
val ShapePill    = RoundedCornerShape(50)
val ShapeChip    = RoundedCornerShape(16.dp)

// ── Shared spacing tokens ─────────────────────────────────────────────────
val SpaceXs  =  4.dp
val SpaceSm  =  8.dp
val SpaceMd  = 12.dp
val SpaceLg  = 20.dp
val SpaceXl  = 24.dp
val Margin   = 20.dp
val Gutter   = 12.dp

// ── Typography convenience extensions (sentence-case rule) ────────────────
@Composable fun bodyMd(color: Color = MaterialTheme.colorScheme.onSurface) =
    MaterialTheme.typography.bodyMedium.copy(color = color, fontSize = 14.sp)

@Composable fun bodyLg(color: Color = MaterialTheme.colorScheme.onSurface) =
    MaterialTheme.typography.bodyLarge.copy(color = color, fontSize = 16.sp)

@Composable fun labelMd(color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    MaterialTheme.typography.labelMedium.copy(color = color, fontSize = 12.sp, letterSpacing = 0.04.sp)

@Composable fun labelLg(color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    MaterialTheme.typography.labelLarge.copy(color = color, fontSize = 14.sp, letterSpacing = 0.02.sp)

@Composable fun titleMd(color: Color = MaterialTheme.colorScheme.onSurface) =
    MaterialTheme.typography.titleMedium.copy(color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

@Composable fun headlineSm(color: Color = MaterialTheme.colorScheme.onSurface) =
    MaterialTheme.typography.headlineSmall.copy(color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

@Composable fun headlineMd(color: Color = MaterialTheme.colorScheme.onSurface) =
    MaterialTheme.typography.headlineMedium.copy(color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)

@Composable fun headlineLg(color: Color = MaterialTheme.colorScheme.onSurface) =
    MaterialTheme.typography.headlineLarge.copy(color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold)

// Tabular numeral style for counters / metrics
@Composable fun heroNumeral(color: Color = MaterialTheme.colorScheme.onSurface) =
    MaterialTheme.typography.headlineLarge.copy(
        color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.32).sp
    )

// ── Card border modifier ──────────────────────────────────────────────────
fun Modifier.cardBorder() = this.border(
    width = 1.dp,
    color = Color(0x408C745C),   // taupe at ~25% alpha
    shape = ShapeCard,
)

fun Modifier.cardSurface() = this
    .clip(ShapeCard)
    .background(Color(0xFF2C1C0A))
    .cardBorder()

// ── Status pill ───────────────────────────────────────────────────────────
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(ShapePill)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = SpaceSm, vertical = 4.dp)
    ) {
        Text(text = text, style = labelMd(color = color))
    }
}
