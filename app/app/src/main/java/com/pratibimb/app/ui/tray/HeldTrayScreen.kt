package com.pratibimb.app.ui.tray

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.ui.HeldTrayItem
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyLg
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.headlineLg
import com.pratibimb.app.ui.theme.labelLg
import com.pratibimb.app.ui.theme.labelMd

@Composable
fun HeldTrayScreen(
    items: List<HeldTrayItem>,
    onReleaseNow: (trayId: String) -> Unit,
    onAlwaysDeliver: (trayId: String) -> Unit,
    onMarkImportant: (trayId: String) -> Unit,
    onReleaseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WarmObsidian.Background)
            .padding(horizontal = 20.dp)
            .padding(bottom = 96.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Held Tray",
                    style = headlineLg(color = WarmObsidian.OnSurface),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                )

                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(WarmObsidian.PrimaryContainer)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${items.size}",
                            style = labelMd(color = WarmObsidian.OnPrimary),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (items.isNotEmpty()) {
                TextButton(
                    onClick = onReleaseAll,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        tint = WarmObsidian.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Release digest",
                        style = labelLg(color = WarmObsidian.Primary),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Held items are released at break or after 45-min cap.",
            style = labelMd(color = WarmObsidian.OnSurfaceVariant),
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(WarmObsidian.SurfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = WarmObsidian.Outline,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tray is clear",
                        style = bodyLg(color = WarmObsidian.OnSurface),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "No notifications are currently held.",
                        style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    HeldItemCard(
                        item = item,
                        onReleaseNow = { onReleaseNow(item.id) },
                        onAlwaysDeliver = { onAlwaysDeliver(item.id) },
                        onMarkImportant = { onMarkImportant(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun HeldItemCard(
    item: HeldTrayItem,
    onReleaseNow: () -> Unit,
    onAlwaysDeliver: () -> Unit,
    onMarkImportant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(18.dp)
    ) {
        Column {
            // App and Timing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.app,
                    style = labelLg(color = WarmObsidian.Secondary),
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "Release in ~${item.minutesUntilRelease} min",
                    style = labelMd(color = WarmObsidian.Outline),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Notification Preview
            Text(
                text = item.title,
                style = bodyLg(color = WarmObsidian.OnSurface),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.text,
                style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            item.flag?.let { flag ->
                Box(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(WarmObsidian.ErrorContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = flag, style = labelMd(color = WarmObsidian.Error), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Held reason tag
            Box(
                modifier = Modifier
                    .clip(ShapePill)
                    .background(WarmObsidian.SurfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item.heldReason,
                    style = labelMd(color = WarmObsidian.Primary),
                    fontSize = 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                color = WarmObsidian.SurfaceContainerHigh,
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 3 Standard Tray Actions (PRD FR-7.4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Action 1: Release now
                Button(
                    onClick = onReleaseNow,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = ShapePill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmObsidian.PrimaryContainer,
                        contentColor = WarmObsidian.OnPrimary,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                ) {
                    Text(text = "Release now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Action 2: Always deliver
                Button(
                    onClick = onAlwaysDeliver,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = ShapePill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmObsidian.SurfaceContainerHigh,
                        contentColor = WarmObsidian.OnSurface,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                ) {
                    Text(text = "Always deliver", fontSize = 12.sp)
                }

                // Action 3: it mattered; releases it and teaches the twin
                Button(
                    onClick = onMarkImportant,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = ShapePill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmObsidian.SurfaceContainerHigh,
                        contentColor = WarmObsidian.Error,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                ) {
                    Text(text = "This was important", fontSize = 12.sp, maxLines = 2)
                }
            }
        }
    }
}
