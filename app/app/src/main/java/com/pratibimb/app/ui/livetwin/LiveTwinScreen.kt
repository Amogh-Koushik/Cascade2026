package com.pratibimb.app.ui.livetwin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.ui.LiveNotificationItem
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyLg
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.headlineLg
import com.pratibimb.app.ui.theme.labelLg
import com.pratibimb.app.ui.theme.labelMd
import com.pratibimb.twin.Action
import com.pratibimb.twin.DecisionRule
import com.pratibimb.twin.FutureCard
import com.pratibimb.twin.WorthLabelKind

@Composable
fun LiveTwinScreen(
    items: List<LiveNotificationItem>,
    onFeedback: (itemId: String, isWorth: Boolean) -> Unit,
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

        // Title and Subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Live Twin",
                    style = headlineLg(color = WarmObsidian.OnSurface),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "On-device decisions & future simulations",
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
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = WarmObsidian.Primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No live notifications yet.\nTrigger a scenario in Debug to see decisions.",
                    style = bodyLg(color = WarmObsidian.OnSurfaceVariant),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    NotificationDecisionCard(
                        item = item,
                        onFeedback = { isWorth -> onFeedback(item.id, isWorth) },
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationDecisionCard(
    item: LiveNotificationItem,
    onFeedback: (isWorth: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val decision = item.decision
    val isHold = decision.action == Action.HOLD

    Box(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface()
            .clickable { expanded = !expanded }
            .padding(18.dp)
    ) {
        Column {
            // Header Row: App name, time, Decision Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.app,
                        style = labelLg(color = WarmObsidian.Secondary),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = " · ${item.timeLabel}",
                        style = labelMd(color = WarmObsidian.Outline),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Action Pill: HOLD or DELIVER
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(
                                if (isHold) WarmObsidian.PrimaryContainer
                                else WarmObsidian.StatusMint.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isHold) "HOLD" else "DELIVER",
                            style = labelMd(
                                color = if (isHold) WarmObsidian.OnPrimary else WarmObsidian.StatusMint
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = WarmObsidian.Outline,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title & Content
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
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Explainer line (under 25 words plain language)
            Text(
                text = decision.explanation,
                style = bodyMd(color = WarmObsidian.OnSurface),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Rule Tag & Futures Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Rule: ${formatRuleName(decision.rule)}",
                    style = labelMd(color = WarmObsidian.Outline),
                    fontSize = 12.sp,
                )

                Text(
                    text = "~${decision.card.holdBetterPerHundred} of 100 hold-better",
                    style = labelMd(color = WarmObsidian.Secondary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Expanded Future Card Details
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    HorizontalDivider(
                        color = WarmObsidian.SurfaceContainerHigh,
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    FutureCardSection(card = decision.card)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Feedback Buttons: Worth it / Not worth it
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { onFeedback(true) },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = ShapePill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.feedbackLabel == WorthLabelKind.WORTH_IT)
                                    WarmObsidian.SecondaryContainer else WarmObsidian.SurfaceContainerHigh,
                                contentColor = WarmObsidian.OnSurface,
                            ),
                        ) {
                            Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Worth it", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { onFeedback(false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = ShapePill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.feedbackLabel == WorthLabelKind.NOT_WORTH_IT)
                                    WarmObsidian.ErrorContainer else WarmObsidian.SurfaceContainerHigh,
                                contentColor = WarmObsidian.OnSurface,
                            ),
                        ) {
                            Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Not worth it", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FutureCardSection(card: FutureCard) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WarmObsidian.SurfaceContainerHigh.copy(alpha = 0.5f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "FUTURE CARD SIMULATION",
            style = labelMd(color = WarmObsidian.Primary),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricItem(
                label = "P(open within 10m)",
                value = "${(card.pullLo * 100).toInt()}% – ${(card.pullHi * 100).toInt()}%",
            )
            MetricItem(
                label = "Mins if opened",
                value = "%.1f – %.1f min".format(card.minsIfOpenedLo, card.minsIfOpenedHi),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricItem(
                label = "L (focus cost shown now)",
                value = "%.1f – %.1f min".format(card.lP10, card.lP90),
            )
            MetricItem(
                label = "H (holding penalty)",
                value = "%.1f – %.1f min".format(card.hP10, card.hP90),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricItem(
                label = "Est. net protected",
                value = "%.1f min (model)".format(card.netMinutesProtected),
            )
            MetricItem(
                label = "Confidence",
                value = if (card.lowConfidence) "Low confidence" else "High confidence",
            )
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = labelMd(color = WarmObsidian.Outline),
            fontSize = 11.sp,
        )
        Text(
            text = value,
            style = bodyMd(color = WarmObsidian.OnSurface),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

private fun formatRuleName(rule: DecisionRule): String = when (rule) {
    DecisionRule.OVERRIDE -> "Override (safety)"
    DecisionRule.FREE_STATE -> "Free state"
    DecisionRule.OBSERVE_MODE -> "Observe mode"
    DecisionRule.MIGHT_MATTER -> "Might matter"
    DecisionRule.HOLD_BETTER -> "Hold better"
    DecisionRule.DELIVER_BETTER -> "Deliver better"
    DecisionRule.EXPLORATION -> "Exploration (ε)"
}
