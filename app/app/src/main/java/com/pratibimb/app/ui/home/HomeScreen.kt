package com.pratibimb.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.ui.theme.ShapeCard
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyLg
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.headlineLg
import com.pratibimb.app.ui.theme.heroNumeral
import com.pratibimb.app.ui.theme.labelLg
import com.pratibimb.app.ui.theme.labelMd
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.PhoneState

@Composable
fun HomeScreen(
    isFocusActive: Boolean,
    selectedDurationMinutes: Int,
    remainingSeconds: Int,
    isModeA: Boolean,
    phoneState: PhoneState,
    fatigueRisk: FatigueRisk,
    autonomy: Autonomy,
    heldTodayCount: Int,
    netMinutesProtected: Double,
    breakSecondsLeft: Int,
    onSelectDuration: (Int) -> Unit,
    onStartFocus: () -> Unit,
    onStopFocus: () -> Unit,
    onTakeBreak: () -> Unit,
    onOpenPersona: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WarmObsidian.Background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 96.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Top App Bar: Large "Home" title + "Demo build" pill badge + Persona avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Home",
                    style = headlineLg(color = WarmObsidian.OnSurface),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Restrained "Demo build" badge
                Box(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(WarmObsidian.SurfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Demo build",
                        style = labelMd(color = WarmObsidian.Secondary),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Persona avatar on far right
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(WarmObsidian.SurfaceContainer)
                    .clickable(onClick = onOpenPersona),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Persona",
                    tint = WarmObsidian.Primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Module 1: Hero
        // Segmented button row with 3 segments: "25 min", "50 min", "90 min"
        if (!isFocusActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapePill)
                    .background(WarmObsidian.SurfaceContainer)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(25, 50, 90).forEach { mins ->
                    val isSelected = selectedDurationMinutes == mins
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ShapePill)
                            .background(
                                if (isSelected) WarmObsidian.PrimaryContainer
                                else Color.Transparent
                            )
                            .clickable { onSelectDuration(mins) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$mins min",
                            style = bodyMd(
                                color = if (isSelected) WarmObsidian.OnPrimary
                                else WarmObsidian.OnSurfaceVariant
                            ),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary filled pill button: "Start focus" (terracotta #D98C4E)
            Button(
                onClick = onStartFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = ShapePill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmObsidian.PrimaryContainer,
                    contentColor = WarmObsidian.OnPrimary,
                ),
            ) {
                Text(
                    text = "Start focus",
                    style = bodyLg(color = WarmObsidian.OnPrimary),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
            }
        } else {
            // Active Focus State
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            val timeFormatted = "%02d:%02d".format(mins, secs)
            val onBreak = breakSecondsLeft > 0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .cardSurface()
                    .padding(24.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (onBreak) "On a break · focus paused" else "Focus session active",
                        style = labelLg(color = WarmObsidian.Secondary),
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = timeFormatted,
                        style = heroNumeral(color = WarmObsidian.OnSurface),
                        fontSize = 48.sp,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            onBreak -> "Break ends in %d:%02d · notifications ring normally"
                                .format(breakSecondsLeft / 60, breakSecondsLeft % 60)
                            isModeA -> "Silent mode active · Alarms only"
                            else -> "Hold mode active"
                        },
                        style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // A break releases everything held so far and pauses the focus clock.
                    if (!onBreak) {
                        Button(
                            onClick = onTakeBreak,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = ShapePill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarmObsidian.PrimaryContainer,
                                contentColor = WarmObsidian.OnPrimary,
                            ),
                        ) {
                            Text(
                                text = "Take a 5-min break",
                                style = bodyMd(color = WarmObsidian.OnPrimary),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Button(
                        onClick = onStopFocus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = ShapePill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmObsidian.SurfaceContainerHighest,
                            contentColor = WarmObsidian.OnSurface,
                        ),
                    ) {
                        Text(
                            text = "End session & release digest",
                            style = bodyMd(color = WarmObsidian.OnSurface),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Module 2: Plain status list rows (no boxes, no card borders, clean rows with icon + label + trailing value)
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatusListRow(
                icon = Icons.Default.NotificationsOff,
                label = "Silent mode",
                value = if (isModeA) "Priority" else "Hold-only",
            )
            HorizontalDivider(
                color = WarmObsidian.SurfaceContainer,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            StatusListRow(
                icon = Icons.Default.PhoneAndroid,
                label = "Phone state",
                value = when (phoneState) {
                    PhoneState.FREE -> "Free"
                    PhoneState.FOCUS_STEADY -> "Steady"
                    PhoneState.FOCUS_RESTLESS -> "Restless"
                },
            )
            HorizontalDivider(
                color = WarmObsidian.SurfaceContainer,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            StatusListRow(
                icon = Icons.Default.Speed,
                label = "Fatigue risk (proxy)",
                value = when (fatigueRisk) {
                    FatigueRisk.LOW -> "Low"
                    FatigueRisk.MEDIUM -> "Medium"
                    FatigueRisk.HIGH -> "High"
                },
            )
            HorizontalDivider(
                color = WarmObsidian.SurfaceContainer,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            StatusListRow(
                icon = Icons.Default.Psychology,
                label = "Autonomy",
                value = when (autonomy) {
                    Autonomy.AUTO -> "Auto"
                    Autonomy.SUGGEST -> "Suggest"
                    Autonomy.OBSERVE -> "Observe"
                },
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Module 3: Today summary & footnote
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface()
                .padding(20.dp),
        ) {
            Column {
                Text(
                    text = "Held today $heldTodayCount",
                    style = headlineLg(color = WarmObsidian.OnSurface),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "~${netMinutesProtected.toInt()} min protected · estimated (model)",
                    style = bodyLg(color = WarmObsidian.Secondary),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Footnote: "Estimates from your on-device model. Minutes lost is a lower bound."
        Text(
            text = "Estimates from your on-device model. Minutes lost is a lower bound.",
            style = labelMd(color = WarmObsidian.Outline),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun StatusListRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WarmObsidian.Outline,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                fontSize = 15.sp,
            )
        }

        Text(
            text = value,
            style = bodyLg(color = WarmObsidian.OnSurface),
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
    }
}
