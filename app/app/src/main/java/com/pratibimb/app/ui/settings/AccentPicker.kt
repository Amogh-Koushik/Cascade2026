package com.pratibimb.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratibimb.app.ui.theme.Accent
import com.pratibimb.app.ui.theme.AccentSettings
import com.pratibimb.app.ui.theme.AppPalette
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.labelMd

// One swatch per accent, filled with the primary colour it would give the app. Tapping one
// recolours every screen straight away.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val selected by AccentSettings.accent.collectAsStateWithLifecycle()
    val swatches = remember { Accent.entries.associateWith { AppPalette.forAccent(it, context).primary } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(16.dp),
    ) {
        Text(
            text = selected.label,
            style = bodyMd(color = WarmObsidian.OnSurface),
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = when (selected) {
                Accent.ORIGINAL -> "The app's own warm palette."
                Accent.WALLPAPER -> "Colours taken from your wallpaper."
                else -> "A Material colour, toned into a full Material 3 scheme."
            },
            style = labelMd(color = WarmObsidian.Outline),
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Accent.entries.forEach { accent ->
                Swatch(
                    accent = accent,
                    fill = swatches.getValue(accent),
                    isSelected = accent == selected,
                    onClick = { AccentSettings.set(accent) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(
    accent: Accent,
    fill: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) WarmObsidian.OnSurface else WarmObsidian.OutlineVariant,
                shape = CircleShape,
            )
            .padding(if (isSelected) 5.dp else 2.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClickLabel = accent.label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val markTint = AppPalette.onColorFor(fill)
        when {
            isSelected -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "${accent.label}, selected",
                tint = markTint,
                modifier = Modifier.size(18.dp),
            )
            accent == Accent.WALLPAPER -> Icon(
                imageVector = Icons.Outlined.Wallpaper,
                contentDescription = accent.label,
                tint = markTint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
