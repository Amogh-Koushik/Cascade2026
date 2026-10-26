package com.pratibimb.app.engine

import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.SimpleJsonParser

// What the weekly report is built from: aggregates only. Minutes and counts per hour, app and
// category, never a notification's text or a sender. Immutable, so a StateFlow can hold it;
// every "with" returns an updated copy. Plain Kotlin, so it runs in JVM tests.
data class ReportHistory(
    val sinceMillis: Long = 0L,
    val steadySecondsByHour: List<Long> = List(HOURS) { 0L },
    val restlessSecondsByHour: List<Long> = List(HOURS) { 0L },
    // Per hour: how many 15-second readings were low, medium and high fatigue risk.
    val fatigueReadingsByHour: List<List<Int>> = List(HOURS) { listOf(0, 0, 0) },
    // Opens of delivered notifications, and the minutes they were measured to cost.
    val opensByApp: Map<String, Int> = emptyMap(),
    val minutesLostByApp: Map<String, Double> = emptyMap(),
    val opensByCategory: Map<String, Int> = emptyMap(),
    val minutesLostByCategory: Map<String, Double> = emptyMap(),
    // How often the user moved to each app during focus.
    val switchesToApp: Map<String, Int> = emptyMap(),
    val heldByApp: Map<String, Int> = emptyMap(),
    val deliveredByApp: Map<String, Int> = emptyMap(),
    // Worth taps per app: [marked worth it or important, all worth taps].
    val worthByApp: Map<String, List<Int>> = emptyMap(),
) {
    fun withFocusTime(hour: Int, restless: Boolean, seconds: Long): ReportHistory =
        if (restless) {
            copy(restlessSecondsByHour = restlessSecondsByHour.bump(hour, seconds))
        } else {
            copy(steadySecondsByHour = steadySecondsByHour.bump(hour, seconds))
        }

    fun withFatigue(hour: Int, risk: FatigueRisk): ReportHistory {
        val readings = fatigueReadingsByHour[hour].toMutableList()
        readings[risk.ordinal] += 1
        return copy(fatigueReadingsByHour = fatigueReadingsByHour.toMutableList().also { it[hour] = readings })
    }

    // minutesLost is null when the open could not be timed; it still counts as an open.
    fun withOpen(packageName: String, category: String, minutesLost: Double?): ReportHistory = copy(
        opensByApp = opensByApp.plusCount(packageName),
        opensByCategory = opensByCategory.plusCount(category),
        minutesLostByApp = minutesLost?.let { minutesLostByApp.plusAmount(packageName, it) } ?: minutesLostByApp,
        minutesLostByCategory = minutesLost?.let { minutesLostByCategory.plusAmount(category, it) } ?: minutesLostByCategory,
    )

    fun withSwitch(packageName: String): ReportHistory = copy(switchesToApp = switchesToApp.plusCount(packageName))

    fun withDecision(packageName: String, held: Boolean): ReportHistory =
        if (held) copy(heldByApp = heldByApp.plusCount(packageName)) else copy(deliveredByApp = deliveredByApp.plusCount(packageName))

    fun withWorth(packageName: String, positive: Boolean): ReportHistory {
        val (yes, all) = worthByApp[packageName] ?: listOf(0, 0)
        return copy(worthByApp = worthByApp + (packageName to listOf(yes + if (positive) 1 else 0, all + 1)))
    }

    val focusMinutesRecorded: Double
        get() = (steadySecondsByHour.sum() + restlessSecondsByHour.sum()) / 60.0

    // The run of consecutive hours with the most steady focus, once there is enough to say.
    fun bestFocusWindow(hours: Int = 3): IntRange? {
        if (steadySecondsByHour.sum() < MIN_STEADY_SECONDS) return null
        val start = (0 until HOURS).maxBy { first -> (0 until hours).sumOf { steadySecondsByHour[(first + it) % HOURS] } }
        return start until start + hours
    }

    // Apps by what they cost focus: measured minutes lost to their notifications first, then
    // how often the user moved to them during focus.
    fun fragmentingApps(limit: Int = 5): List<AppFragment> =
        (opensByApp.keys + minutesLostByApp.keys + switchesToApp.keys)
            .map { AppFragment(it, minutesLostByApp[it] ?: 0.0, opensByApp[it] ?: 0, switchesToApp[it] ?: 0) }
            .filter { it.minutesLost > 0 || it.switches > 0 }
            .sortedWith(compareByDescending<AppFragment> { it.minutesLost }.thenByDescending { it.switches })
            .take(limit)

    fun longRunningCategories(): List<CategoryLength> =
        minutesLostByCategory.map { (category, minutes) -> CategoryLength(category, minutes, opensByCategory[category] ?: 0) }
            .sortedByDescending { it.minutesLost }

    fun appsThatMatter(limit: Int = 5): List<AppWorth> =
        worthByApp.map { (app, counts) -> AppWorth(app, counts[0], counts[1]) }
            .filter { it.total > 0 }
            .sortedWith(compareByDescending<AppWorth> { it.share }.thenByDescending { it.total })
            .take(limit)

    // Average fatigue level per hour (0 low, 1 medium, 2 high); null for hours with no readings.
    fun fatigueByHour(): List<Double?> = fatigueReadingsByHour.map { readings ->
        val total = readings.sum()
        if (total == 0) null else (readings[1] + 2.0 * readings[2]) / total
    }

    fun toJson(): String = JsonText.write(
        mapOf(
            "since" to sinceMillis,
            "steady" to steadySecondsByHour,
            "restless" to restlessSecondsByHour,
            "fatigue" to fatigueReadingsByHour,
            "opens_app" to opensByApp,
            "minutes_app" to minutesLostByApp,
            "opens_category" to opensByCategory,
            "minutes_category" to minutesLostByCategory,
            "switches" to switchesToApp,
            "held" to heldByApp,
            "delivered" to deliveredByApp,
            "worth" to worthByApp,
        ),
    )

    companion object {
        const val HOURS = 24

        // Ten minutes of steady focus before naming best hours.
        const val MIN_STEADY_SECONDS = 600L

        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: String): ReportHistory = fromMap(SimpleJsonParser(json).parse() as Map<String, Any?>)

        @Suppress("UNCHECKED_CAST")
        fun fromMap(root: Map<String, Any?>): ReportHistory {
            fun longs(key: String) = (root[key] as List<Any?>).map { (it as Number).toLong() }
            fun counts(key: String) = (root[key] as Map<String, Any?>).mapValues { (it.value as Number).toInt() }
            fun amounts(key: String) = (root[key] as Map<String, Any?>).mapValues { (it.value as Number).toDouble() }
            return ReportHistory(
                sinceMillis = (root["since"] as Number).toLong(),
                steadySecondsByHour = longs("steady"),
                restlessSecondsByHour = longs("restless"),
                fatigueReadingsByHour = (root["fatigue"] as List<Any?>).map { hour ->
                    (hour as List<Any?>).map { (it as Number).toInt() }
                },
                opensByApp = counts("opens_app"),
                minutesLostByApp = amounts("minutes_app"),
                opensByCategory = counts("opens_category"),
                minutesLostByCategory = amounts("minutes_category"),
                switchesToApp = counts("switches"),
                heldByApp = counts("held"),
                deliveredByApp = counts("delivered"),
                worthByApp = (root["worth"] as Map<String, Any?>).mapValues { entry ->
                    (entry.value as List<Any?>).map { (it as Number).toInt() }
                },
            )
        }
    }
}

data class AppFragment(val packageName: String, val minutesLost: Double, val opens: Int, val switches: Int)

data class CategoryLength(val category: String, val minutesLost: Double, val opens: Int)

data class AppWorth(val packageName: String, val worthIt: Int, val total: Int) {
    val share: Double get() = worthIt.toDouble() / total
}

private fun List<Long>.bump(index: Int, amount: Long): List<Long> =
    toMutableList().also { it[index] += amount }

private fun Map<String, Int>.plusCount(key: String): Map<String, Int> = this + (key to (this[key] ?: 0) + 1)

private fun Map<String, Double>.plusAmount(key: String, amount: Double): Map<String, Double> =
    this + (key to (this[key] ?: 0.0) + amount)
