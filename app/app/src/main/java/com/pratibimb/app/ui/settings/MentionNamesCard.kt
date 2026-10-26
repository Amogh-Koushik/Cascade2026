package com.pratibimb.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.labelMd

// The names people tag the user by in groups. A group message that tags one of them, or tags
// everyone, gets through focus.
@Composable
fun MentionNamesCard(mentionNames: Set<String>, onSave: (String) -> Unit, modifier: Modifier = Modifier) {
    var entry by remember(mentionNames) { mutableStateOf(mentionNames.joinToString(", ")) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(16.dp),
    ) {
        Text(
            text = "Group messages that tag you get through",
            style = bodyMd(color = WarmObsidian.OnSurface),
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Add the names people use when they @ you, separated by commas. @everyone and @all always count.",
            style = labelMd(color = WarmObsidian.Outline),
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = entry,
            onValueChange = { entry = it },
            label = { Text("Your names, e.g. Satvik, Sat") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { onSave(entry) }) {
            Text("Save")
        }
    }
}
