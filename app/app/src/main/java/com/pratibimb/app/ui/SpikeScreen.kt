package com.pratibimb.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pratibimb.app.data.LogEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SpikeActions(
    val openAppInfo: () -> Unit,
    val openNotificationAccess: () -> Unit,
    val openDndAccess: () -> Unit,
    val requestPostNotifications: () -> Unit,
    val createAlertChannel: () -> Unit,
    val sendTestPing: () -> Unit,
    val enterPriority: () -> Unit,
    val restoreFilter: () -> Unit,
    val setPingOnPost: (Boolean) -> Unit,
    val clearLog: () -> Unit,
)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun SpikeScreen(
    status: PermissionStatus,
    listenerConnected: Boolean,
    entries: List<LogEntry>,
    pingOnPost: Boolean,
    actions: SpikeActions,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Pratibimb spike", style = MaterialTheme.typography.headlineSmall)
        }

        item { SectionTitle("Permissions") }
        item {
            // Android cannot tell us whether this was done, so it is a manual step. On a
            // sideloaded APK it must happen before notification access can be switched on.
            PermissionRow("Allow restricted settings", null, "App info", actions.openAppInfo)
        }
        item {
            PermissionRow(
                label = "Notification access",
                granted = status.notificationAccess,
                actionLabel = "Open",
                onAction = actions.openNotificationAccess,
            )
        }
        item {
            Text(
                text = if (listenerConnected) "Listener: connected" else "Listener: not connected",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            PermissionRow("Do Not Disturb access", status.dndAccess, "Open", actions.openDndAccess)
        }
        item {
            PermissionRow(
                label = "Post notifications",
                granted = status.postNotifications,
                actionLabel = "Allow",
                onAction = actions.requestPostNotifications,
            )
        }

        item { SectionTitle("Do Not Disturb test") }
        item { Text("Current filter: ${interruptionFilterName(status.interruptionFilter)}") }
        item { FullWidthButton("Create alert channel", actions.createAlertChannel) }
        item { FullWidthButton("Send test ping", actions.sendTestPing) }
        item { FullWidthButton("Priority mode on", actions.enterPriority) }
        item { FullWidthButton("Restore previous filter", actions.restoreFilter, outlined = true) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = pingOnPost, onCheckedChange = actions.setPingOnPost)
                Text(
                    text = "Ping when any app posts a notification",
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Log (${entries.size})", Modifier.weight(1f))
                TextButton(onClick = actions.clearLog) { Text("Clear") }
            }
        }
        items(entries, key = { it.id }) { entry -> LogRow(entry) }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 12.dp),
    )
}

// granted = null means Android offers no way to check, so the row shows a manual hint.
@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val (stateText, stateColor) = when (granted) {
        true -> "granted" to MaterialTheme.colorScheme.primary
        false -> "missing" to MaterialTheme.colorScheme.error
        null -> "check by hand" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text(stateText, color = stateColor, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun FullWidthButton(label: String, onClick: () -> Unit, outlined: Boolean = false) {
    val modifier = Modifier.fillMaxWidth()
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val time = Instant.ofEpochMilli(entry.timeMillis)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FORMAT)
    Column {
        Text(
            text = "$time  ${entry.source}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(entry.message, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
