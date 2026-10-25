package com.pratibimb.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.data.InspectedPost
import com.pratibimb.app.engine.ContentRuleFlags
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.labelMd
import com.pratibimb.app.ui.theme.titleMd
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InspectorScreen(
    posts: List<InspectedPost>,
    ruleFlags: ContentRuleFlags,
    mentionNames: Set<String>,
    onSetRuleFlags: (ContentRuleFlags) -> Unit,
    onSetMentionNames: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WarmObsidian.Background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = WarmObsidian.OnSurface)
                }
                Text(text = "Notification inspector", style = titleMd(color = WarmObsidian.OnSurface), modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text(text = "Clear", color = WarmObsidian.Primary) }
            }
            Text(
                text = "What Android reports for each notification, and what the twin made of it. Kept in memory only.",
                style = labelMd(color = WarmObsidian.OnSurfaceVariant),
            )
        }
        item { RulesCard(ruleFlags, mentionNames, onSetRuleFlags, onSetMentionNames) }
        if (posts.isEmpty()) {
            item {
                Text(
                    text = "No notifications yet. Anything posted on the phone appears here as it arrives.",
                    style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(posts, key = { it.id }) { PostCard(it) }
        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
private fun RulesCard(
    flags: ContentRuleFlags,
    mentionNames: Set<String>,
    onSetRuleFlags: (ContentRuleFlags) -> Unit,
    onSetMentionNames: (String) -> Unit,
) {
    var names by remember(mentionNames) { mutableStateOf(mentionNames.joinToString(", ")) }
    Box(modifier = Modifier.fillMaxWidth().cardSurface().padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Content rules", style = titleMd(color = WarmObsidian.OnSurface))
            RuleSwitch(
                "Focus rules (on by default)",
                "WhatsApp and other 1:1 chats come through; Instagram/Snapchat at 5 texts in 2 min; groups, snaps, promos, bots and the rest wait.",
                flags.focusGate,
            ) { onSetRuleFlags(flags.copy(focusGate = it)) }
            RuleSwitch(
                "Repeat pings count text only",
                "Reels, photos and forwards no longer count towards a repeated ping.",
                flags.textOnlyRepeatPings,
            ) { onSetRuleFlags(flags.copy(textOnlyRepeatPings = it)) }
            RuleSwitch(
                "New senders: hold links, deliver plain text once",
                "A first message with a link is held and flagged in the tray.",
                flags.newSenderLinks,
            ) { onSetRuleFlags(flags.copy(newSenderLinks = it)) }
            OutlinedTextField(
                value = names,
                onValueChange = { names = it },
                label = { Text("Names you're mentioned by (comma-separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WarmObsidian.PrimaryContainer,
                    unfocusedBorderColor = WarmObsidian.Outline,
                    focusedTextColor = WarmObsidian.OnSurface,
                    unfocusedTextColor = WarmObsidian.OnSurface,
                    focusedLabelColor = WarmObsidian.Primary,
                    unfocusedLabelColor = WarmObsidian.OnSurfaceVariant,
                ),
            )
            TextButton(onClick = { onSetMentionNames(names) }) {
                Text(text = "Save names", color = WarmObsidian.Primary)
            }
        }
    }
}

@Composable
private fun RuleSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = bodyMd(color = WarmObsidian.OnSurface), fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = labelMd(color = WarmObsidian.Outline))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WarmObsidian.Primary,
                checkedTrackColor = WarmObsidian.PrimaryContainer,
                uncheckedThumbColor = WarmObsidian.Outline,
                uncheckedTrackColor = WarmObsidian.SurfaceContainerHigh,
            ),
        )
    }
}

@Composable
private fun PostCard(post: InspectedPost) {
    val time = remember(post.timeMillis) { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(post.timeMillis)) }
    val content = post.content
    val contentLine = buildList {
        add(content.kind.name.lowercase())
        if (content.hasUrl) add("link")
        if (content.hasQuestion) add("question")
        content.deadlinePhrase?.let { phrase ->
            val at = content.deadlineAtMillis?.let { " (${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))})" } ?: ""
            add("deadline \"$phrase\"$at")
        }
    }.joinToString(" · ")

    Box(modifier = Modifier.fillMaxWidth().cardSurface().padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = post.appLabel, style = bodyMd(color = WarmObsidian.Secondary), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(text = time, style = labelMd(color = WarmObsidian.Outline))
            }
            Text(text = post.title.ifBlank { "(no title)" }, style = bodyMd(color = WarmObsidian.OnSurface))
            Fact("Channel", post.channelName?.let { "$it (${post.channelId})" } ?: post.channelId.ifBlank { "-" })
            Fact("Importance", post.importance?.toString() ?: "-")
            Fact("Category", post.androidCategory ?: "-")
            Fact("Conversation", post.isConversation?.let { if (it) "yes" else "no" } ?: "unknown")
            Fact("Group", if (post.isGroup) "yes" else "no")
            Fact("Messages", post.messageCount?.toString() ?: "not MessagingStyle")
            Fact("Template", post.template ?: "-")
            Fact("Picture", if (post.hasPicture) "yes" else "no")
            Fact("Content", contentLine)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = post.outcome, style = labelMd(color = WarmObsidian.Primary), fontSize = 12.sp)
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        Text(text = label, style = labelMd(color = WarmObsidian.OnSurfaceVariant), modifier = Modifier.width(96.dp))
        Text(text = value, style = labelMd(color = WarmObsidian.OnSurface))
    }
}
