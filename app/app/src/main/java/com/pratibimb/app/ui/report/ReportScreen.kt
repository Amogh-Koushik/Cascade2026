package com.pratibimb.app.ui.report

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.data.NotificationLog
import com.pratibimb.app.engine.AppFragment
import com.pratibimb.app.engine.AppWorth
import com.pratibimb.app.engine.CategoryLength
import com.pratibimb.app.engine.DemoReport
import com.pratibimb.app.engine.ReportHistory
import com.pratibimb.app.ui.theme.ShapeCard
import com.pratibimb.app.ui.theme.ShapeChip
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyLg
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.headlineLg
import com.pratibimb.app.ui.theme.labelLg
import com.pratibimb.app.ui.theme.labelMd
import com.pratibimb.app.ui.theme.titleMd
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ReportScreen: Answers the 6 core Problem Statement (PS) questions in plain, user-friendly English:
 *
 * 1. When the user focuses best  -> Best focus hours & 24h peak histogram
 * 2. What causes interruptions   -> Filtered noise vs priority delivery breakdown
 * 3. What types of content cause prolonged sessions -> Time-sink categories with duration per open
 * 4. Which notifications actually matter            -> High-value contacts & apps marked "Worth it"
 * 5. When the user is mentally fatigued            -> Smooth 24h circadian fatigue risk proxy curve
 * 6. Which applications fragment concentration     -> Apps that fragment focus with minutes lost
 *
 * Plus actionable Suggested Rules derived from the report insights.
 */
@Composable
fun ReportScreen(
    history: ReportHistory,
    appLabel: (String) -> String,
    modifier: Modifier = Modifier,
) {
    // Until the phone has 5 minutes of its own focus, show the bundled simulated week so the
    // report can be seen without waiting a week. Every number in it comes from the file.
    val context = LocalContext.current
    val demo = remember { loadDemoReport(context) }
    val hasLiveData = history.focusMinutesRecorded >= 5.0
    var isDemoMode by remember { mutableStateOf(!hasLiveData && demo != null) }
    var refreshKey by remember { mutableStateOf(0) }

    val shownDemo = demo?.takeIf { isDemoMode }
    val activeHistory = shownDemo?.history ?: history
    val labelFor: (String) -> String = if (shownDemo != null) {
        { packageName -> shownDemo.appLabels[packageName] ?: appLabel(packageName) }
    } else {
        appLabel
    }
    val sinceDate = if (shownDemo != null) {
        "Simulated week of our student coder persona (tap the pill for live)"
    } else {
        val dateStr = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(history.sinceMillis))
        "From this phone since $dateStr · ${formatMinutes(history.focusMinutesRecorded)} focus"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WarmObsidian.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // ── Top Header ──────────────────────────────────────────────────────────
        ReportHeader(
            isDemoMode = isDemoMode,
            onToggleDemo = { if (demo != null) isDemoMode = !isDemoMode },
            onRefresh = { refreshKey++ },
            subtitle = sinceDate,
        )

        // ── Question 1: When the user focuses best ─────────────────────────────
        BestFocusHoursCard(history = activeHistory)

        // ── Question 6: Which applications fragment concentration ──────────────
        FragmentingAppsCard(
            history = activeHistory,
            appLabel = labelFor,
        )

        // ── Question 2: What causes interruptions ──────────────────────────────
        InterruptionsCard(
            history = activeHistory,
            appLabel = labelFor,
        )

        // ── Question 3: What types of content cause prolonged sessions ─────────
        LongRunningCategoriesCard(
            history = activeHistory,
        )

        // ── Question 4: Which notifications actually matter ───────────────────
        NotificationsThatMatterCard(
            history = activeHistory,
            appLabel = labelFor,
        )

        // ── Question 5: When the user is mentally fatigued ─────────────────────
        FatigueRiskCircadianCard(
            history = activeHistory,
        )

        // Approving a suggestion does not create a rule yet, so the card shows only in the
        // labelled simulated view.
        if (shownDemo != null) SuggestedRulesCard(history = activeHistory, appLabel = labelFor)

        // ── Privacy & Audit Footer ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = WarmObsidian.Outline,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Processed 100% on-device. Minutes lost is a lower bound estimate.",
                style = labelMd(color = WarmObsidian.Outline),
            )
        }
    }
}

// ── Top Header Component ──────────────────────────────────────────────────────
@Composable
private fun ReportHeader(
    isDemoMode: Boolean,
    onToggleDemo: () -> Unit,
    onRefresh: () -> Unit,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Report",
                style = headlineLg(color = WarmObsidian.OnSurface),
                fontSize = 28.sp,
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Interactive Demo / Live Toggle Pill
                Box(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(WarmObsidian.SurfaceContainerHighest)
                        .clickable { onToggleDemo() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = if (isDemoMode) "Simulated week" else "Live on-device",
                        style = labelMd(color = if (isDemoMode) WarmObsidian.Secondary else WarmObsidian.StatusMint),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = WarmObsidian.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Subtitle badge showing simulation state or live recording status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Circle,
                contentDescription = null,
                tint = if (isDemoMode) WarmObsidian.Secondary else WarmObsidian.StatusMint,
                modifier = Modifier.size(8.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = subtitle,
                style = labelMd(color = WarmObsidian.OnSurfaceVariant),
            )
        }
    }
}

// ── Base Card Container ───────────────────────────────────────────────────────
@Composable
private fun QuestionCard(
    title: String,
    icon: ImageVector,
    tag: String? = null,
    insight: String? = null,
    footnote: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = WarmObsidian.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = title,
                        style = titleMd(color = WarmObsidian.OnSurface),
                        fontSize = 17.sp,
                    )
                }

                tag?.let {
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(WarmObsidian.SurfaceContainerHighest)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = it,
                            style = labelMd(color = WarmObsidian.OnSurfaceVariant),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            insight?.let {
                Text(
                    text = it,
                    style = labelLg(color = WarmObsidian.Primary),
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                )
            }

            content()

            footnote?.let {
                Text(
                    text = it,
                    style = labelMd(color = WarmObsidian.Outline),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

// ── Question 1: When the user focuses best ────────────────────────────────────
@Composable
private fun BestFocusHoursCard(history: ReportHistory) {
    val window = history.bestFocusWindow()
    val peakText = if (window != null) {
        "Peak window: %02d:00–%02d:00".format(window.first % 24, (window.last + 1) % 24)
    } else {
        "Needs at least 10 minutes of recorded focus to map peaks."
    }

    QuestionCard(
        title = "Best focus hours",
        icon = Icons.Outlined.WbSunny,
        insight = peakText,
        footnote = "Based on phone behaviour and declared focus.",
    ) {
        if (history.steadySecondsByHour.sum() == 0L) {
            EmptyNotice("No focus sessions completed yet. Start a session from the Home tab to build your peak chart.")
        } else {
            val values = history.steadySecondsByHour.map { it.toDouble() }
            HourBarsHistogram(
                values = values,
                highlight = { hour -> window?.contains(hour) == true },
            )
        }
    }
}

// ── Question 6: Which applications fragment concentration ─────────────────────
@Composable
private fun FragmentingAppsCard(history: ReportHistory, appLabel: (String) -> String) {
    val apps = history.fragmentingApps()

    QuestionCard(
        title = "Apps that fragment focus",
        icon = Icons.Outlined.ElectricBolt,
        tag = "lower bound",
        insight = if (apps.isNotEmpty()) "Context switches to these apps pull you away from deep work." else null,
    ) {
        if (apps.isEmpty()) {
            EmptyNotice("No app switches or opens recorded during focus sessions yet.")
        } else {
            val top = apps.maxOf { it.minutesLost.coerceAtLeast(it.switches.toDouble()) }.coerceAtLeast(1.0)
            apps.take(4).forEach { app ->
                val timeLost = if (app.minutesLost > 0) formatMinutes(app.minutesLost) else "${app.switches} switches"
                val subtitle = buildList {
                    if (app.opens > 0) add("${app.opens} opens")
                    if (app.switches > 0) add("${app.switches} quick switches")
                }.joinToString(" · ")

                ProgressBarRow(
                    title = appLabel(app.packageName),
                    subtitle = subtitle.ifEmpty { null },
                    metric = timeLost,
                    fraction = (app.minutesLost.coerceAtLeast(app.switches.toDouble()) / top).coerceIn(0.08, 1.0),
                    accentColor = WarmObsidian.StatusMint,
                )
            }
        }
    }
}

// ── Question 2: What causes interruptions ─────────────────────────────────────
@Composable
private fun InterruptionsCard(
    history: ReportHistory,
    appLabel: (String) -> String,
) {
    val heldTotal = history.heldByApp.values.sum()
    val deliveredTotal = history.deliveredByApp.values.sum()
    val totalNotifs = heldTotal + deliveredTotal

    val filterPct = if (totalNotifs > 0) ((heldTotal.toDouble() / totalNotifs) * 100).toInt() else 0
    val mostHeld = history.heldByApp.entries.sortedByDescending { it.value }.take(2).map { appLabel(it.key) }

    QuestionCard(
        title = "What causes interruptions",
        icon = Icons.Outlined.NotificationsActive,
        tag = if (filterPct > 0) "$filterPct% noise held" else null,
        insight = if (mostHeld.isNotEmpty()) "Held most: ${mostHeld.joinToString(" and ")}." else null,
        footnote = "Held notifications rest in the Tray until focus ends or an urgent keyword appears.",
    ) {
        if (totalNotifs == 0) {
            EmptyNotice("No notifications evaluated during focus sessions yet.")
        } else {
            // High-level filter split bar
            val heldCount = heldTotal
            val deliveredCount = deliveredTotal

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$heldCount held (peace preserved)",
                        style = labelMd(color = WarmObsidian.StatusMint),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$deliveredCount delivered (allowed through)",
                        style = labelMd(color = WarmObsidian.Primary),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Split ratio bar
                val total = (heldCount + deliveredCount).coerceAtLeast(1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(ShapePill)
                        .background(WarmObsidian.SurfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(heldCount.toFloat() / total)
                            .fillMaxHeight()
                            .background(WarmObsidian.StatusMint),
                    )
                    Box(
                        modifier = Modifier
                            .weight(deliveredCount.toFloat() / total)
                            .fillMaxHeight()
                            .background(WarmObsidian.PrimaryContainer),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Breakdown items
                // The four apps that sent the most during focus.
                val busiestApps = (history.heldByApp.keys + history.deliveredByApp.keys)
                    .sortedByDescending { (history.heldByApp[it] ?: 0) + (history.deliveredByApp[it] ?: 0) }
                    .take(4)
                val breakdownItems = busiestApps.map { pkg ->
                    val h = history.heldByApp[pkg] ?: 0
                    val d = history.deliveredByApp[pkg] ?: 0
                    Triple(appLabel(pkg), "$h held · $d delivered", ((h + d).toDouble() / total).coerceIn(0.1, 1.0))
                }

                breakdownItems.forEach { (name, desc, frac) ->
                    ProgressBarRow(
                        title = name,
                        subtitle = desc,
                        metric = "",
                        fraction = frac,
                        accentColor = WarmObsidian.Primary,
                    )
                }
            }
        }
    }
}

// ── Question 3: What types of content cause prolonged sessions ────────────────
@Composable
private fun LongRunningCategoriesCard(history: ReportHistory) {
    val categories = history.longRunningCategories()
    val longest = categories.filter { it.opens > 0 }.maxByOrNull { it.minutesLost / it.opens }

    QuestionCard(
        title = "Content categories that run long",
        icon = Icons.Outlined.HourglassTop,
        tag = "time sink proxy",
        insight = longest?.let {
            "Longest per open: ${categoryLabel(it.category)}, about ${formatMinutes(it.minutesLost / it.opens)} each."
        },
        footnote = "Android shows which app, not what's inside it, so category is our proxy.",
    ) {
        if (categories.isEmpty()) {
            EmptyNotice("Needs usage access and at least one notification opened during focus.")
        } else {
            val top = categories.maxOf { it.minutesLost }.coerceAtLeast(1.0)
            categories.take(4).forEach { cat ->
                val perOpen = if (cat.opens > 0) " · ${formatMinutes(cat.minutesLost / cat.opens)} per open" else ""
                val subtitle = "${cat.opens} opens$perOpen"

                ProgressBarRow(
                    title = categoryLabel(cat.category),
                    subtitle = subtitle,
                    metric = formatMinutes(cat.minutesLost),
                    fraction = (cat.minutesLost / top).coerceIn(0.08, 1.0),
                    accentColor = categoryColor(cat.category),
                    showDot = true,
                )
            }
        }
    }
}

// ── Question 4: Which notifications actually matter ───────────────────────────
@Composable
private fun NotificationsThatMatterCard(
    history: ReportHistory,
    appLabel: (String) -> String,
) {
    val items = history.appsThatMatter().map {
        ContactMatterItem(
            name = appLabel(it.packageName),
            channel = "${it.worthIt} of ${it.total} marked worth it",
            percent = (it.share * 100).toInt(),
            fraction = it.share,
            count = it.total,
        )
    }

    QuestionCard(
        title = "Notifications that matter",
        icon = Icons.Outlined.VerifiedUser,
        tag = "Marked as \"Worth it\"",
        insight = "Apps whose notifications you marked as worth it when asked.",
        footnote = "Tap 'Worth it' or 'This was important' in the tray to train your twin.",
    ) {
        if (items.isEmpty()) {
            EmptyNotice("Tap 'Worth it' or 'This was important' in the tray to teach Pratibimb what matters to you.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { contact ->
                    ProgressBarRow(
                        title = contact.name,
                        subtitle = contact.channel,
                        metric = "${contact.percent}%",
                        fraction = contact.fraction.coerceIn(0.1, 1.0),
                        accentColor = WarmObsidian.StatusMint,
                    )
                }
            }
        }
    }
}

// ── Question 5: When the user is mentally fatigued ────────────────────────────
@Composable
private fun FatigueRiskCircadianCard(history: ReportHistory) {
    val levels = history.fatigueByHour()
    val hasReadings = levels.any { it != null }
    val peakHour = levels.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }?.index

    QuestionCard(
        title = "Fatigue risk (proxy)",
        icon = Icons.Outlined.Bedtime,
        insight = peakHour?.let { "Highest around %02d:00, from long stretches, rising app switching and late hours.".format(it) },
        footnote = "A proxy. We don't claim to read your mind. Hours with no focus sit at zero.",
    ) {
        if (!hasReadings) {
            EmptyNotice("No focus sessions recorded yet.")
        } else {
            CircadianWaveGraph(values = levels.map { it ?: 0.0 })
        }
    }
}

// ── Section 7: Suggested Rules (Actionable Intelligence) ──────────────────────
@Composable
private fun SuggestedRulesCard(history: ReportHistory, appLabel: (String) -> String) {
    val approvedMap = remember { mutableStateMapOf<String, Boolean>() }
    val rules = suggestedRules(history, appLabel)

    QuestionCard(
        title = "Suggested rules",
        icon = Icons.Outlined.Tune,
        tag = "Autonomy",
        insight = "Pratibimb generates rules from your feedback patterns. You stay in full control.",
        footnote = "🔒 Rules can never change the never-hold list.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rules.forEach { rule ->
                val approved = approvedMap[rule.id]
                AnimatedVisibility(
                    visible = approved != false,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeChip)
                            .background(WarmObsidian.SurfaceContainerHighest)
                            .padding(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(WarmObsidian.SurfaceContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = rule.icon,
                                        contentDescription = null,
                                        tint = WarmObsidian.Primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.question,
                                        style = bodyMd(color = WarmObsidian.OnSurface),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = rule.detail,
                                        style = labelMd(color = WarmObsidian.OnSurfaceVariant),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (approved == true) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = WarmObsidian.StatusMint,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "Approved",
                                            style = labelMd(color = WarmObsidian.StatusMint),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Decline",
                                        style = labelMd(color = WarmObsidian.Outline),
                                        modifier = Modifier
                                            .clickable { approvedMap[rule.id] = false }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(ShapePill)
                                            .background(WarmObsidian.PrimaryContainer)
                                            .clickable { approvedMap[rule.id] = true }
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = "Approve",
                                            style = labelMd(color = WarmObsidian.OnPrimaryContainer),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Chart Components ──────────────────────────────────────────────────────────

/**
 * 24-hour histogram with highlighted peak windows and hour markings.
 */
@Composable
private fun HourBarsHistogram(
    values: List<Double>,
    highlight: (Int) -> Boolean,
) {
    val maxValue = values.maxOrNull()?.coerceAtLeast(1e-6) ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { hour, value ->
                val isPeak = highlight(hour)
                val barColor = if (isPeak) {
                    WarmObsidian.StatusMint
                } else {
                    WarmObsidian.StatusMint.copy(alpha = 0.22f)
                }

                val heightFraction = (value / maxValue).toFloat().coerceIn(0.06f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(heightFraction)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(barColor),
                )
            }
        }

        // Hour labels below
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00h", "06h", "12h", "18h", "23h").forEach { mark ->
                Text(
                    text = mark,
                    style = labelMd(color = WarmObsidian.Outline),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * Smooth 24-hour Circadian Wave Canvas graph matching the design reference.
 */
@Composable
private fun CircadianWaveGraph(values: List<Double>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(105.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                val yHigh = height * 0.18f
                val yMod = height * 0.52f
                val yLow = height * 0.84f

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                val guideColor = WarmObsidian.Outline.copy(alpha = 0.25f)

                // Dotted reference guidelines for High, Moderate, Low
                drawLine(color = guideColor, start = Offset(0f, yHigh), end = Offset(width, yHigh), pathEffect = dashEffect)
                drawLine(color = guideColor, start = Offset(0f, yMod), end = Offset(width, yMod), pathEffect = dashEffect)
                drawLine(color = guideColor, start = Offset(0f, yLow), end = Offset(width, yLow), pathEffect = dashEffect)

                // Points along 24 hours
                val points = values.mapIndexed { hour, level ->
                    val x = (hour.toFloat() / 23f) * width
                    val normalized = (level / 2.0).coerceIn(0.0, 2.0).toFloat()
                    val y = yLow - normalized * (yLow - yHigh)
                    Offset(x, y)
                }

                if (points.isNotEmpty()) {
                    val path = Path()
                    val fillPath = Path()

                    path.moveTo(points.first().x, points.first().y)
                    fillPath.moveTo(points.first().x, height)
                    fillPath.lineTo(points.first().x, points.first().y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val midX = (p0.x + p1.x) / 2f
                        path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                        fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    }

                    fillPath.lineTo(points.last().x, height)
                    fillPath.close()

                    // Subtle mint glow gradient fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                WarmObsidian.StatusMint.copy(alpha = 0.20f),
                                WarmObsidian.StatusMint.copy(alpha = 0.03f),
                                Color.Transparent,
                            ),
                        ),
                    )

                    // Smooth line
                    drawPath(
                        path = path,
                        color = WarmObsidian.StatusMint,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // Highlight the peak exhaustion point
                    val peakPoint = points.maxByOrNull { yLow - it.y } ?: points.last()
                    drawCircle(
                        color = WarmObsidian.StatusMint,
                        radius = 5.dp.toPx(),
                        center = peakPoint,
                    )
                    drawCircle(
                        color = WarmObsidian.Background,
                        radius = 2.5.dp.toPx(),
                        center = peakPoint,
                    )
                }
            }

            // Right-aligned labels for High, Moderate, Low
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "High", style = labelMd(color = WarmObsidian.Outline), fontSize = 10.sp)
                Text(text = "Moderate", style = labelMd(color = WarmObsidian.Outline), fontSize = 10.sp)
                Text(text = "Low", style = labelMd(color = WarmObsidian.Outline), fontSize = 10.sp)
            }
        }

        // Hour labels below
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00h", "06h", "12h", "18h", "23h").forEach { mark ->
                Text(
                    text = mark,
                    style = labelMd(color = WarmObsidian.Outline),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * Clean reusable horizontal progress row with title, optional subtitle, metric text, and bar.
 */
@Composable
private fun ProgressBarRow(
    title: String,
    subtitle: String?,
    metric: String,
    fraction: Double,
    accentColor: Color,
    showDot: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (showDot) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                }
                Text(
                    text = title,
                    style = bodyMd(color = WarmObsidian.OnSurface),
                    fontWeight = FontWeight.Medium,
                )
            }

            if (metric.isNotBlank()) {
                Text(
                    text = metric,
                    style = bodyMd(color = WarmObsidian.OnSurface),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(ShapePill)
                .background(WarmObsidian.SurfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.toFloat().coerceIn(0.04f, 1f))
                    .fillMaxHeight()
                    .clip(ShapePill)
                    .background(accentColor),
            )
        }

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = labelMd(color = WarmObsidian.OnSurfaceVariant),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun EmptyNotice(text: String) {
    Text(
        text = text,
        style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
        lineHeight = 19.sp,
    )
}

// ── Helpers & Pre-generated Baseline Data ──────────────────────────────────────

private fun formatMinutes(minutes: Double): String {
    val total = minutes.toInt()
    return when {
        minutes < 1 -> "%.1f min".format(minutes)
        total < 60 -> "$total min"
        total % 60 == 0 -> "${total / 60} h"
        else -> "${total / 60} h ${total % 60} min"
    }
}

private fun categoryLabel(category: String): String = when (category) {
    "social", "short_video" -> "Social & reels"
    "messaging" -> "Group chats & messaging"
    "shopping_promo" -> "Promotional & shopping"
    "news" -> "News & alerts"
    "work_email" -> "Work & university email"
    else -> category.replaceFirstChar { it.uppercase() }
}

private fun categoryColor(category: String): Color = when (category) {
    "social", "short_video" -> WarmObsidian.StatusMint
    "messaging" -> WarmObsidian.Secondary
    "shopping_promo" -> WarmObsidian.Tertiary
    else -> WarmObsidian.StatusLavender
}

private data class ContactMatterItem(
    val name: String,
    val channel: String,
    val percent: Int,
    val fraction: Double,
    val count: Int,
)

// Read from the report itself: the app held most and never marked important, the app most
// often marked worth it (over at least three answers), and the steadiest hours.
private fun suggestedRules(history: ReportHistory, appLabel: (String) -> String): List<SuggestedRule> = buildList {
    history.heldByApp.entries
        .filter { (history.worthByApp[it.key]?.first() ?: 0) == 0 }
        .maxByOrNull { it.value }
        ?.let { (pkg, held) ->
            add(
                SuggestedRule(
                    id = "hold_$pkg",
                    icon = Icons.Outlined.ShoppingBag,
                    question = "Always hold ${appLabel(pkg)} during focus?",
                    detail = "$held held, none marked important.",
                ),
            )
        }
    history.appsThatMatter(limit = Int.MAX_VALUE).firstOrNull { it.total >= 3 }?.let {
        add(
            SuggestedRule(
                id = "deliver_${it.packageName}",
                icon = Icons.Outlined.Person,
                question = "Always deliver ${appLabel(it.packageName)}?",
                detail = "You marked ${it.worthIt} of ${it.total} as worth it.",
            ),
        )
    }
    history.bestFocusWindow()?.let { window ->
        add(
            SuggestedRule(
                id = "protect_peak",
                icon = Icons.Outlined.NotificationsActive,
                question = "Hold group chats during %02d:00–%02d:00?".format(window.first % 24, (window.last + 1) % 24),
                detail = "Your steadiest focus hours.",
            ),
        )
    }
}

// The simulated week bundled with the app; null if the asset is missing or malformed.
private fun loadDemoReport(context: Context): DemoReport? = try {
    DemoReport.parse(context.assets.open(DemoReport.ASSET).bufferedReader().use { it.readText() })
} catch (e: Exception) {
    NotificationLog.add("Report", "Could not load ${DemoReport.ASSET}: ${e.message}")
    null
}

private data class SuggestedRule(
    val id: String,
    val icon: ImageVector,
    val question: String,
    val detail: String,
)
