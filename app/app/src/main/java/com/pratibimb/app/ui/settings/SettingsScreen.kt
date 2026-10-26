package com.pratibimb.app.ui.settings

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.engine.Personas
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyLg
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.headlineLg
import com.pratibimb.app.ui.theme.labelLg
import com.pratibimb.app.ui.theme.labelMd

@Composable
fun SettingsScreen(
    selectedPersona: String,
    onSelectPersona: (String) -> Unit,
    demoLearning: Boolean,
    onToggleDemoLearning: (Boolean) -> Unit,
    // Returns how many simulated outcomes were applied.
    onRunLearningReplay: () -> Int,
    pauseCard: Boolean,
    onTogglePauseCard: (Boolean) -> Unit,
    onDeleteAllData: () -> Unit,
    onOpenDebug: () -> Unit,
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

        // Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Settings & Privacy",
                    style = headlineLg(color = WarmObsidian.OnSurface),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Persona priors, safety overrides & data controls",
                    style = labelLg(color = WarmObsidian.OnSurfaceVariant),
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(WarmObsidian.SurfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = WarmObsidian.Primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Debug, scenarios and the notification inspector live here, off the bottom bar.
        Button(
            onClick = onOpenDebug,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = ShapePill,
            colors = ButtonDefaults.buttonColors(
                containerColor = WarmObsidian.SurfaceContainerHigh,
                contentColor = WarmObsidian.OnSurface,
            ),
        ) {
            Text(text = "Debug, scenarios & notification inspector", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 1: Persona Switcher
        Text(
            text = "PERSONA PRIORS",
            style = labelMd(color = WarmObsidian.Primary),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // One card per bundled params file; selectedPersona is the persona id.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Personas.all.forEach { persona ->
                val isSelected = selectedPersona == persona.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardSurface()
                        .clickable { onSelectPersona(persona.id) }
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = persona.label,
                                style = bodyLg(color = WarmObsidian.OnSurface),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = persona.description,
                                style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                                fontSize = 13.sp,
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(WarmObsidian.PrimaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = WarmObsidian.OnPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        DemoLearningSection(
            demoLearning = demoLearning,
            onToggleDemoLearning = onToggleDemoLearning,
            onRunLearningReplay = onRunLearningReplay,
            pauseCard = pauseCard,
            onTogglePauseCard = onTogglePauseCard,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section 2: Non-Negotiable Safety Overrides (PRD §5.5)
        Text(
            text = "HARD SAFETY OVERRIDES (NEVER HOLD)",
            style = labelMd(color = WarmObsidian.Primary),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface()
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SafetyRuleRow("Calls, alarms & timers", "Never blocked by policy")
                HorizontalDivider(color = WarmObsidian.SurfaceContainerHigh)
                SafetyRuleRow("Ongoing rides & food delivery", "Live tracking is always delivered")
                HorizontalDivider(color = WarmObsidian.SurfaceContainerHigh)
                SafetyRuleRow("SMS & Banking OTPs", "Delivered instantly (banking & SMS sources only)")
                HorizontalDivider(color = WarmObsidian.SurfaceContainerHigh)
                SafetyRuleRow("Repeated pings from same person", "≥ 2 pings in 3 min trigger immediate delivery")
                HorizontalDivider(color = WarmObsidian.SurfaceContainerHigh)
                SafetyRuleRow("Maximum hold cap: 45 minutes", "Items automatically release if not opened")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 3: Privacy & Data Controls (DPDP Act Compliance)
        Text(
            text = "PRIVACY & LOCAL STORAGE (DPDP ACT 2023)",
            style = labelMd(color = WarmObsidian.Primary),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface()
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = WarmObsidian.StatusMint,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "100% On-Device Guarantee",
                        style = bodyLg(color = WarmObsidian.OnSurface),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "No notification text leaves your phone. Senders are stored as salted hashes. Raw message text is deleted immediately upon release or expiry.",
                    style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDeleteAllData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = ShapePill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmObsidian.SurfaceContainerHigh,
                        contentColor = WarmObsidian.Error,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = WarmObsidian.Error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete all local data & reset twin",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// For the stage demo: wider worth ranges so a few taps visibly move them, and a replay of 30
// simulated outcomes through the normal learning path. Both are labelled as demo-only.
@Composable
private fun DemoLearningSection(
    demoLearning: Boolean,
    onToggleDemoLearning: (Boolean) -> Unit,
    onRunLearningReplay: () -> Int,
    pauseCard: Boolean,
    onTogglePauseCard: (Boolean) -> Unit,
) {
    var replayResult by remember { mutableStateOf<String?>(null) }

    Text(
        text = "DEMO LEARNING",
        style = labelMd(color = WarmObsidian.Primary),
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.08.sp,
    )

    Spacer(modifier = Modifier.height(10.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(18.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Demo learning profile",
                        style = bodyMd(color = WarmObsidian.OnSurface),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Wider worth ranges so a few taps visibly change decisions. Off after restart.",
                        style = labelMd(color = WarmObsidian.Outline),
                        fontSize = 12.sp,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = demoLearning,
                    onCheckedChange = onToggleDemoLearning,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WarmObsidian.Primary,
                        checkedTrackColor = WarmObsidian.PrimaryContainer,
                        uncheckedThumbColor = WarmObsidian.Outline,
                        uncheckedTrackColor = WarmObsidian.SurfaceContainerHigh,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pause card (demo)",
                        style = bodyMd(color = WarmObsidian.OnSurface),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Tapping our alert shows what opening it now would cost first. On in the demo build.",
                        style = labelMd(color = WarmObsidian.Outline),
                        fontSize = 12.sp,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = pauseCard,
                    onCheckedChange = onTogglePauseCard,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WarmObsidian.Primary,
                        checkedTrackColor = WarmObsidian.PrimaryContainer,
                        uncheckedThumbColor = WarmObsidian.Outline,
                        uncheckedTrackColor = WarmObsidian.SurfaceContainerHigh,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val applied = onRunLearningReplay()
                    replayResult = if (applied > 0) {
                        "Learned from $applied simulated Instagram outcomes"
                    } else {
                        "Replay file could not be read; see the Debug log"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = ShapePill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmObsidian.SurfaceContainerHigh,
                    contentColor = WarmObsidian.OnSurface,
                ),
            ) {
                Text(
                    text = "Run learning replay (simulated)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            replayResult?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = labelMd(color = WarmObsidian.Secondary),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun SafetyRuleRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = bodyMd(color = WarmObsidian.OnSurface),
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = labelMd(color = WarmObsidian.Outline),
                fontSize = 12.sp,
            )
        }

        Box(
            modifier = Modifier
                .clip(ShapePill)
                .background(WarmObsidian.SurfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Active",
                style = labelMd(color = WarmObsidian.StatusMint),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
