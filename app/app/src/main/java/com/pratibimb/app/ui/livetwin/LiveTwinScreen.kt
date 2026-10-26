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
import kotlin.math.roundToInt

@Composable
fun LiveTwinScreen(
    items: List<LiveNotificationItem>,
    onFeedback: (itemId: String, isWorth: Boolean) -> Unit,
    developerMode: Boolean,
    resumeMinutes: Double,
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
                    text = "What your twin decided, and what it expected to happen",
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
                    text = if (developerMode) {
                        "No live notifications yet.\nTrigger a scenario in Debug to see decisions."
                    } else {
                        "Nothing yet.\nNotifications that arrive during focus show up here, with what your twin decided."
                    },
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
                        developerMode = developerMode,
                        resumeMinutes = resumeMinutes,
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
    developerMode: Boolean,
    resumeMinutes: Double,
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
                            text = if (isHold) "Held for later" else "Delivered",
                            style = labelMd(
                                color = if (isHold) WarmObsidian.OnPrimaryContainer else WarmObsidian.StatusMint
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

            // How the sampled futures came out, in plain words; the rule name is for developers.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (developerMode) {
                    Text(
                        text = "Rule: ${formatRuleName(decision.rule)}",
                        style = labelMd(color = WarmObsidian.Outline),
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = "Waiting was better in ${decision.card.holdBetterPerHundred} of 100 likely outcomes",
                    style = labelMd(color = WarmObsidian.Secondary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (!expanded) {
                Text(
                    text = "Tap to see what opening it would cost",
                    style = labelMd(color = WarmObsidian.Outline),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
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

                    WhatHappensSection(card = decision.card, resumeMinutes = resumeMinutes)
                    if (developerMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FutureCardSection(card = decision.card)
                    }

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

// "If I allow this right now, what happens?" in plain words: the chance it pulls you in, the
// time in the app, getting back into the task, and what waiting would cost instead.
@Composable
fun WhatHappensSection(card: FutureCard, resumeMinutes: Double) {
    val pull = "${(card.pullLo * 100).toInt()}–${(card.pullHi * 100).toInt()}%"
    val browsing = "${card.minsIfOpenedLo.roundToInt()}–${card.minsIfOpenedHi.roundToInt()} min"
    val resume = resumeMinutes.roundToInt()
    val total = "${(card.minsIfOpenedLo + resumeMinutes).roundToInt()}–${(card.minsIfOpenedHi + resumeMinutes).roundToInt()} min"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WarmObsidian.SurfaceContainerHigh.copy(alpha = 0.5f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "If you open it now",
            style = labelMd(color = WarmObsidian.Primary),
            fontWeight = FontWeight.Bold,
        )
        ChainStep("Chance it pulls you in", pull)
        ChainStep("Time in the app", browsing)
        if (resume > 0) ChainStep("Getting back into your task", "about $resume min")
        ChainStep("Focus lost if you open it", total)
        HorizontalDivider(color = WarmObsidian.SurfaceContainerHigh, thickness = 1.dp)
        Text(
            text = "If it waits",
            style = labelMd(color = WarmObsidian.Primary),
            fontWeight = FontWeight.Bold,
        )
        ChainStep("Delivered at your break", "in ${card.minutesUntilRelease.roundToInt().coerceAtLeast(1)} min")
        if (card.netMinutesProtected > 0) {
            ChainStep("Focus saved by waiting", "about ${card.netMinutesProtected.roundToInt()} min")
        }
        Text(
            text = if (card.lowConfidence) {
                "Your twin is still learning this one, so it leans towards letting it through."
            } else {
                "Estimates from your on-device twin. Time lost is a lower bound."
            },
            style = labelMd(color = WarmObsidian.Outline),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ChainStep(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = bodyMd(color = WarmObsidian.OnSurfaceVariant), fontSize = 13.sp)
        Text(text = value, style = bodyMd(color = WarmObsidian.OnSurface), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

// The model's raw numbers, shown in developer mode only.
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
            text = "MODEL NUMBERS (DEVELOPER)",
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
