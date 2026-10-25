package com.pratibimb.app.ui.report

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.engine.ReportHistory
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.headlineLg
import com.pratibimb.app.ui.theme.labelMd
import com.pratibimb.app.ui.theme.titleMd
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The report, built only from what this phone recorded during focus sessions; every section
// says so when there is not enough yet rather than showing sample numbers.
@Composable
fun ReportScreen(
    history: ReportHistory,
    appLabel: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val since = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(history.sinceMillis))
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WarmObsidian.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Report", style = headlineLg(color = WarmObsidian.OnSurface), fontSize = 26.sp)
        Text(
            text = "From this phone since $since · ${formatMinutes(history.focusMinutesRecorded)} of focus recorded",
            style = labelMd(color = WarmObsidian.StatusMint),
        )
        BestHours(history)
        FragmentingApps(history, appLabel)
        Interruptions(history, appLabel)
        LongCategories(history)
        AppsThatMatter(history, appLabel)
        FatigueByHour(history)
        Text(
            text = "Estimates from your on-device model. Minutes lost is a lower bound.",
            style = labelMd(color = WarmObsidian.Outline),
        )
    }
}

@Composable
private fun Section(title: String, tag: String? = null, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().cardSurface().padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = titleMd(color = WarmObsidian.OnSurface), modifier = Modifier.weight(1f))
                tag?.let {
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(WarmObsidian.SurfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(text = it, style = labelMd(color = WarmObsidian.OnSurfaceVariant))
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text = text, style = bodyMd(color = WarmObsidian.OnSurfaceVariant))
}

@Composable
private fun BestHours(history: ReportHistory) {
    Section("Best focus hours") {
        val window = history.bestFocusWindow()
        if (window == null) {
            Empty("Needs at least 10 minutes of steady focus. Start a session to fill this.")
        } else {
            Text(
                text = "Steadiest stretch: %02d:00–%02d:00".format(window.first % 24, (window.last + 1) % 24),
                style = labelMd(color = WarmObsidian.Primary),
            )
            HourBars(
                values = history.steadySecondsByHour.map { it.toDouble() },
                highlight = { hour -> (window.first..window.last).any { it % 24 == hour } },
            )
        }
        Text(text = "Based on phone behaviour and declared focus.", style = labelMd(color = WarmObsidian.Outline))
    }
}

@Composable
private fun FragmentingApps(history: ReportHistory, appLabel: (String) -> String) {
    Section("Apps that fragment focus", tag = "lower bound") {
        val apps = history.fragmentingApps()
        if (apps.isEmpty()) {
            Empty("No opens or app switches recorded during focus yet.")
        } else {
            val top = apps.maxOf { it.minutesLost.coerceAtLeast(it.switches.toDouble()) }.coerceAtLeast(1.0)
            apps.forEach { app ->
                val detail = buildList {
                    if (app.minutesLost > 0) add(formatMinutes(app.minutesLost) + " lost")
                    if (app.opens > 0) add("${app.opens} opened")
                    if (app.switches > 0) add("${app.switches} switches")
                }.joinToString(" · ")
                BarRow(appLabel(app.packageName), detail, app.minutesLost.coerceAtLeast(app.switches.toDouble()) / top)
            }
        }
    }
}

@Composable
private fun Interruptions(history: ReportHistory, appLabel: (String) -> String) {
    Section("What interrupts you") {
        val apps = (history.heldByApp.keys + history.deliveredByApp.keys)
            .map { Triple(it, history.deliveredByApp[it] ?: 0, history.heldByApp[it] ?: 0) }
            .sortedByDescending { it.second + it.third }
            .take(5)
        if (apps.isEmpty()) {
            Empty("No notifications decided during focus yet.")
        } else {
            val top = apps.maxOf { it.second + it.third }.coerceAtLeast(1)
            apps.forEach { (app, delivered, held) ->
                BarRow(appLabel(app), "$delivered delivered · $held held", (delivered + held).toDouble() / top)
            }
        }
    }
}

@Composable
private fun LongCategories(history: ReportHistory) {
    Section("Content categories that run long") {
        val categories = history.longRunningCategories()
        if (categories.isEmpty()) {
            Empty("No measured opens yet. Needs usage access and a notification opened during focus.")
        } else {
            val top = categories.maxOf { it.minutesLost }.coerceAtLeast(1.0)
            categories.forEach { category ->
                val perOpen = if (category.opens > 0) " · ${formatMinutes(category.minutesLost / category.opens)} per open" else ""
                BarRow(categoryName(category.category), formatMinutes(category.minutesLost) + perOpen, category.minutesLost / top)
            }
        }
        Text(
            text = "Android shows which app, not what's inside it, so category is our proxy.",
            style = labelMd(color = WarmObsidian.Outline),
        )
    }
}

@Composable
private fun AppsThatMatter(history: ReportHistory, appLabel: (String) -> String) {
    Section("Notifications that matter", tag = "your taps") {
        val apps = history.appsThatMatter()
        if (apps.isEmpty()) {
            Empty("Tap Worth it or Not worth it on cards and alerts, or This was important in the tray, to fill this.")
        } else {
            apps.forEach { app ->
                BarRow(
                    appLabel(app.packageName),
                    "${(app.share * 100).toInt()}% worth it · ${app.total} taps",
                    app.share,
                )
            }
        }
    }
}

@Composable
private fun FatigueByHour(history: ReportHistory) {
    Section("Fatigue risk (proxy)") {
        val levels = history.fatigueByHour()
        if (levels.all { it == null }) {
            Empty("No readings yet. The proxy is read every 15 seconds during focus.")
        } else {
            HourBars(
                values = levels.map { it ?: 0.0 },
                maxValue = 2.0,
                color = { hour -> levelColor(levels[hour]) },
            )
            Text(text = "Low · medium · high, averaged per hour.", style = labelMd(color = WarmObsidian.OnSurfaceVariant))
        }
        Text(text = "A proxy. We don't claim to read your mind.", style = labelMd(color = WarmObsidian.Outline))
    }
}

@Composable
private fun BarRow(label: String, detail: String, fraction: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(text = label, style = bodyMd(color = WarmObsidian.OnSurface), modifier = Modifier.weight(1f))
            Text(text = detail, style = labelMd(color = WarmObsidian.StatusMint), fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(ShapePill)
                .background(WarmObsidian.SurfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.toFloat().coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(ShapePill)
                    .background(WarmObsidian.StatusMint),
            )
        }
    }
}

// 24 vertical bars, one per hour, with hour marks underneath.
@Composable
private fun HourBars(
    values: List<Double>,
    maxValue: Double = values.maxOrNull() ?: 0.0,
    highlight: (Int) -> Boolean = { false },
    color: ((Int) -> Color)? = null,
) {
    val top = maxValue.coerceAtLeast(1e-9)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { hour, value ->
                val barColor = color?.invoke(hour)
                    ?: if (highlight(hour)) WarmObsidian.StatusMint else WarmObsidian.StatusMint.copy(alpha = 0.35f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight((value / top).toFloat().coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(barColor),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("00h", "06h", "12h", "18h", "23h").forEachIndexed { i, mark ->
                Text(text = mark, style = labelMd(color = WarmObsidian.Outline), modifier = Modifier.weight(1f))
                if (i < 4) Spacer(modifier = Modifier.weight(0.25f))
            }
        }
    }
}

private fun levelColor(level: Double?): Color = when {
    level == null -> WarmObsidian.SurfaceContainerHigh
    level >= 1.5 -> WarmObsidian.StatusCoral
    level >= 0.5 -> WarmObsidian.Secondary
    else -> WarmObsidian.StatusMint
}

private fun categoryName(key: String): String = when (key) {
    "short_video" -> "Short video"
    "social" -> "Social"
    "messaging" -> "Messaging"
    "shopping_promo" -> "Shopping and promos"
    "news" -> "News"
    "work_email" -> "Work email"
    else -> "Other"
}

private fun formatMinutes(minutes: Double): String {
    val total = minutes.toInt()
    return when {
        minutes < 1 -> "%.1f min".format(minutes)
        total < 60 -> "$total min"
        total % 60 == 0 -> "${total / 60} h"
        else -> "${total / 60} h ${total % 60} min"
    }
}
