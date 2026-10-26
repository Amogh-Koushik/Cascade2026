package com.pratibimb.app.ui.debug

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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.data.LogEntry
import com.pratibimb.app.engine.MockScenario
import com.pratibimb.app.engine.StubEngine
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyLg
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.headlineLg
import com.pratibimb.app.ui.theme.labelLg
import com.pratibimb.app.ui.theme.labelMd
import com.pratibimb.twin.Action
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.PhoneState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_LOG_ROWS = 60
private val LOG_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun DebugScreen(
    isModeA: Boolean,
    phoneState: PhoneState,
    autonomy: Autonomy,
    useLivePermissions: Boolean,
    focusStatus: List<String>,
    logEntries: List<LogEntry>,
    onRefreshStatus: () -> Unit,
    onClearLog: () -> Unit,
    onToggleModeA: (Boolean) -> Unit,
    onSetPhoneState: (PhoneState) -> Unit,
    onSetAutonomy: (Autonomy) -> Unit,
    onToggleLivePermissions: (Boolean) -> Unit,
    onTriggerScenario: (MockScenario) -> Unit,
    onSendTestPing: () -> Unit,
    onResetTwin: () -> Unit,
    onOpenInspector: () -> Unit,
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
                    text = "Debug & Scenarios",
                    style = headlineLg(color = WarmObsidian.OnSurface),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PRD scenarios, hardware toggles & twin reset",
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
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = WarmObsidian.Primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Raw notification facts and the content rules, on their own screen.
        SectionHeader("NOTIFICATION INSPECTOR", actionLabel = "Open", onAction = onOpenInspector)
        Spacer(modifier = Modifier.height(24.dp))

        // Section 0: what is really in force right now, and every real decision as it happens.
        SectionHeader("FOCUS STATUS", actionLabel = "Refresh", onAction = onRefreshStatus)
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface()
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                focusStatus.forEach { line ->
                    Text(text = line, style = bodyMd(color = WarmObsidian.OnSurface), fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader("LIVE LOG (${logEntries.size})", actionLabel = "Clear", onAction = onClearLog)
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface()
                .padding(16.dp),
        ) {
            if (logEntries.isEmpty()) {
                Text(
                    text = "Nothing yet. Start focus and send a notification from another phone.",
                    style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                    fontSize = 13.sp,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    logEntries.take(MAX_LOG_ROWS).forEach { entry -> LogRow(entry) }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Section 1: Scenario Sender (10 sample scenarios, decided live by the real engine)
        Text(
            text = "TRIGGER SAMPLE SCENARIO (chip shows the expected result)",
            style = labelMd(color = WarmObsidian.Primary),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StubEngine.SCENARIOS.forEach { scenario ->
                val isHold = scenario.decision.action == Action.HOLD
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardSurface()
                        .clickable { onTriggerScenario(scenario) }
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = scenario.display.app,
                                    style = labelLg(color = WarmObsidian.Secondary),
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = scenario.display.title,
                                    style = bodyMd(color = WarmObsidian.OnSurface),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = scenario.display.text,
                                style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(ShapePill)
                                .background(
                                    if (isHold) WarmObsidian.PrimaryContainer
                                    else WarmObsidian.StatusMint.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (isHold) "HOLD" else "DELIVER",
                                style = labelMd(
                                    color = if (isHold) WarmObsidian.OnPrimaryContainer else WarmObsidian.StatusMint
                                ),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Section 2: Hardware & Delivery Modes
        Text(
            text = "HARDWARE & ENVIRONMENT SIMULATION",
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Mode A vs Mode B toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isModeA) "Mode A (Priority DND)" else "Mode B (Hold-only)",
                            style = bodyLg(color = WarmObsidian.OnSurface),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (isModeA) "Bypasses DND for urgent pings" else "Cancels at post time; may buzz once",
                            style = labelMd(color = WarmObsidian.OnSurfaceVariant),
                        )
                    }
                    Switch(
                        checked = isModeA,
                        onCheckedChange = onToggleModeA,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WarmObsidian.OnPrimary,
                            checkedTrackColor = WarmObsidian.Primary,
                            uncheckedThumbColor = WarmObsidian.Outline,
                            uncheckedTrackColor = WarmObsidian.SurfaceContainerHigh,
                        ),
                    )
                }

                HorizontalDivider(color = WarmObsidian.SurfaceContainerHigh)

                // Phone State Segmented Selector
                Column {
                    Text(
                        text = "Phone behavior state",
                        style = bodyMd(color = WarmObsidian.OnSurface),
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapePill)
                            .background(WarmObsidian.SurfaceContainerHigh)
                            .padding(4.dp),
                    ) {
                        listOf(PhoneState.FREE, PhoneState.FOCUS_STEADY, PhoneState.FOCUS_RESTLESS).forEach { state ->
                            val isSelected = phoneState == state
                            val label = when (state) {
                                PhoneState.FREE -> "Free"
                                PhoneState.FOCUS_STEADY -> "Steady"
                                PhoneState.FOCUS_RESTLESS -> "Restless"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(ShapePill)
                                    .background(if (isSelected) WarmObsidian.SecondaryContainer else Color.Transparent)
                                    .clickable { onSetPhoneState(state) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = labelMd(
                                        color = if (isSelected) WarmObsidian.OnSecondaryContainer else WarmObsidian.OnSurfaceVariant
                                    ),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = WarmObsidian.SurfaceContainerHigh)

                // Autonomy Mode
                Column {
                    Text(
                        text = "Twin autonomy level",
                        style = bodyMd(color = WarmObsidian.OnSurface),
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapePill)
                            .background(WarmObsidian.SurfaceContainerHigh)
                            .padding(4.dp),
                    ) {
                        listOf(Autonomy.AUTO, Autonomy.SUGGEST, Autonomy.OBSERVE).forEach { a ->
                            val isSelected = autonomy == a
                            val label = when (a) {
                                Autonomy.AUTO -> "Auto"
                                Autonomy.SUGGEST -> "Suggest"
                                Autonomy.OBSERVE -> "Observe"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(ShapePill)
                                    .background(if (isSelected) WarmObsidian.SecondaryContainer else Color.Transparent)
                                    .clickable { onSetAutonomy(a) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = labelMd(
                                        color = if (isSelected) WarmObsidian.OnSecondaryContainer else WarmObsidian.OnSurfaceVariant
                                    ),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 3: Diagnostic actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onSendTestPing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = ShapePill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmObsidian.SurfaceContainerHigh,
                    contentColor = WarmObsidian.OnSurface,
                ),
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test alert ping", fontSize = 12.sp)
            }

            Button(
                onClick = onResetTwin,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = ShapePill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmObsidian.SurfaceContainerHigh,
                    contentColor = WarmObsidian.Error,
                ),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset learning", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = labelMd(color = WarmObsidian.Primary),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
        )
        Text(
            text = actionLabel,
            style = labelMd(color = WarmObsidian.Secondary),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(ShapePill)
                .clickable(onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val time = Instant.ofEpochMilli(entry.timeMillis).atZone(ZoneId.systemDefault()).format(LOG_TIME)
    Column {
        Text(
            text = "$time  ${entry.source}",
            style = labelMd(color = WarmObsidian.OnSurfaceVariant),
            fontSize = 11.sp,
        )
        Text(
            text = entry.message,
            style = bodyMd(color = WarmObsidian.OnSurface),
            fontSize = 13.sp,
        )
    }
}