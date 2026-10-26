package com.pratibimb.app.ui.onboarding

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyLg
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.headlineLg
import com.pratibimb.app.ui.theme.labelMd

private data class ConsentSection(val icon: ImageVector, val title: String, val points: List<String>)

// Every statement here must stay true of the app; they follow the PRD's privacy statements and
// claim nothing stronger.
private val SECTIONS = listOf(
    ConsentSection(
        icon = Icons.Default.Visibility,
        title = "What Pratibimb reads",
        points = listOf(
            "Each notification as it arrives: the app, title, text, sender and time.",
            "During a focus session, which app is on screen. Never what is inside it.",
        ),
    ),
    ConsentSection(
        icon = Icons.Default.Lock,
        title = "What it stores",
        points = listOf(
            "Learned patterns (counts per app and category), not your messages.",
            "Senders only as salted hashes, so a stored entry cannot be matched to a name.",
            "Held notifications, encrypted, for at most 45 minutes; deleted when released.",
            "OTP and banking notifications are never held, and their codes are never logged.",
        ),
    ),
    ConsentSection(
        icon = Icons.Default.PhoneAndroid,
        title = "What never leaves the phone",
        points = listOf(
            "Every decision is made on this phone. Nothing you receive is sent anywhere.",
        ),
    ),
    ConsentSection(
        icon = Icons.Default.Block,
        title = "What it never asks for",
        points = listOf("Accessibility, contacts, location, microphone or camera."),
    ),
    ConsentSection(
        icon = Icons.Default.Tune,
        title = "What you control",
        points = listOf(
            "How much it may act: Observe, Suggest or Auto.",
            "Release anything held, mark senders as always deliver, and delete all data in Settings.",
        ),
    ),
)

@Composable
fun ConsentScreen(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var agreed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WarmObsidian.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Before you start",
                style = headlineLg(color = WarmObsidian.OnSurface),
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pratibimb needs to read your notifications to decide which can wait. Here is exactly what that means.",
                style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
            )
            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SECTIONS.forEach { ConsentCard(it) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { agreed = !agreed },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = agreed,
                    onCheckedChange = { agreed = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = WarmObsidian.Primary,
                        uncheckedColor = WarmObsidian.Outline,
                        checkmarkColor = WarmObsidian.OnPrimary,
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I understand and agree",
                    style = bodyLg(color = WarmObsidian.OnSurface),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Permissions are only requested after this, so nothing is read without consent.
            Button(
                onClick = onAccept,
                enabled = agreed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = ShapePill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmObsidian.Primary,
                    contentColor = WarmObsidian.OnPrimary,
                    disabledContainerColor = WarmObsidian.SurfaceContainerHigh,
                    disabledContentColor = WarmObsidian.Outline,
                ),
            ) {
                Text(
                    text = "Continue to permissions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun ConsentCard(section: ConsentSection) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = WarmObsidian.Primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = section.title,
                    style = bodyLg(color = WarmObsidian.OnSurface),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            section.points.forEach { point ->
                Text(
                    text = "· $point",
                    style = labelMd(color = WarmObsidian.OnSurfaceVariant),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
