package com.pratibimb.app.ui.onboarding

import android.content.Context
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.ui.PermissionIntents
import com.pratibimb.app.ui.PermissionStatus
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyLg
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.headlineLg
import com.pratibimb.app.ui.theme.labelMd

private data class PermissionItemData(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val icon: ImageVector,
    val onAction: (Context) -> Unit,
)

@Composable
fun PermissionChecklistScreen(
    status: PermissionStatus,
    onRequestPostNotifications: () -> Unit,
    onContinue: (isModeA: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val permissionItems = listOf(
        PermissionItemData(
            title = "Allow restricted settings",
            description = "Do this first, before notification access.",
            isGranted = status.restrictedSettingsAllowed,
            icon = Icons.Default.Lock,
            onAction = { PermissionIntents.openAppDetails(it) },
        ),
        PermissionItemData(
            title = "Notification access",
            description = "Required to inspect notification cost on-device.",
            isGranted = status.notificationAccess,
            icon = Icons.Default.Notifications,
            onAction = { PermissionIntents.openNotificationAccess(it) },
        ),
        PermissionItemData(
            title = "Usage access",
            description = "Required to detect current app and phone focus state.",
            isGranted = status.usageAccess,
            icon = Icons.Default.QueryStats,
            onAction = { PermissionIntents.openUsageAccess(it) },
        ),
        PermissionItemData(
            title = "Do Not Disturb access",
            description = "Required to mute ringtones during active sessions.",
            isGranted = status.dndAccess,
            icon = Icons.Default.DoNotDisturbOn,
            onAction = { PermissionIntents.openDndAccess(it) },
        ),
        PermissionItemData(
            title = "Post notifications",
            description = "Required to deliver digests and released items.",
            isGranted = status.postNotifications,
            icon = Icons.Default.NotificationsActive,
            onAction = { onRequestPostNotifications() },
        ),
        PermissionItemData(
            title = "Battery optimisation exemption",
            description = "Prevents the system from killing background inference.",
            isGranted = status.batteryExemption,
            icon = Icons.Default.BatteryAlert,
            onAction = { PermissionIntents.openBatteryOptimization(it) },
        ),
    )

    val progress = status.grantedCount.toFloat() / status.totalCount.toFloat()
    val canProceed = status.grantedCount >= 3 // In demo/setup, allow proceeding once minimums are checked

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WarmObsidian.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Title
            Text(
                text = "Set up Pratibimb",
                style = headlineLg(color = WarmObsidian.OnSurface),
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress text: "3 of 6 done"
            Text(
                text = "${status.grantedCount} of ${status.totalCount} done",
                style = labelMd(color = WarmObsidian.OnSurfaceVariant),
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Linear Progress Indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(ShapePill),
                color = WarmObsidian.PrimaryContainer,
                trackColor = WarmObsidian.SurfaceContainerHigh,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Permission Rows in exact order
            permissionItems.forEachIndexed { index, item ->
                PermissionRow(
                    index = index + 1,
                    item = item,
                    onOpenSettings = { item.onAction(context) },
                )
                if (index < permissionItems.size - 1) {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Sticky Bottom Area
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(WarmObsidian.Background.copy(alpha = 0.95f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = {
                    val modeA = status.dndAccess
                    onContinue(modeA)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = ShapePill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmObsidian.PrimaryContainer,
                    contentColor = WarmObsidian.OnPrimary,
                    disabledContainerColor = WarmObsidian.PrimaryContainer.copy(alpha = 0.38f),
                    disabledContentColor = WarmObsidian.OnPrimary.copy(alpha = 0.38f),
                ),
                enabled = canProceed,
            ) {
                Text(
                    text = "Continue",
                    style = bodyLg(color = if (canProceed) WarmObsidian.OnPrimary else WarmObsidian.OnSurfaceVariant.copy(alpha = 0.5f)),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footnote
            Text(
                text = "Estimates from your on-device model. Decisions stay private.",
                style = labelMd(color = WarmObsidian.Outline),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    index: Int,
    item: PermissionItemData,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Left Icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (item.isGranted) WarmObsidian.PrimaryContainer.copy(alpha = 0.15f)
                    else WarmObsidian.SurfaceContainerHigh
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (item.isGranted) Icons.Default.Check else item.icon,
                contentDescription = null,
                tint = if (item.isGranted) WarmObsidian.PrimaryContainer else WarmObsidian.Outline,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Middle Info Column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = item.title,
                style = bodyLg(color = WarmObsidian.OnSurface),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.description,
                style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            // Text button: "Open settings"
            TextButton(
                onClick = onOpenSettings,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text(
                    text = "Open settings",
                    style = labelMd(color = WarmObsidian.Primary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Trailing Status Text ("Granted" or "Missing")
        Box(
            modifier = Modifier.padding(top = 4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = if (item.isGranted) "Granted" else "Missing",
                style = labelMd(
                    color = if (item.isGranted) WarmObsidian.StatusMint else WarmObsidian.Secondary
                ),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
        }
    }
}
