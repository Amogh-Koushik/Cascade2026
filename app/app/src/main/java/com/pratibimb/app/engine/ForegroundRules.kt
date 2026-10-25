package com.pratibimb.app.engine

// What the phone's usage events say about the user, as plain functions over a timeline so they
// run in JVM tests. The Android side (data/UsageReader) only turns UsageEvents into this list.

enum class ForegroundKind { APP_RESUMED, SCREEN_OFF }

data class ForegroundEvent(val atMillis: Long, val packageName: String, val kind: ForegroundKind)

object ForegroundRules {
    // FOCUS_STEADY is at most this many app switches in the window; more is FOCUS_RESTLESS.
    const val STEADY_MAX_SWITCHES = 3
    const val SWITCH_WINDOW_MILLIS = 10 * 60_000L

    // The opened app can reach the front a moment before the tap is reported as a removal.
    const val TAP_GRACE_MILLIS = 5_000L

    // Moves between different apps. Ignored packages (the launcher, the system UI) are passed
    // through, so going home and opening another app is one switch, not two.
    fun countSwitches(events: List<ForegroundEvent>, ignored: Set<String>): Int {
        var switches = 0
        var current: String? = null
        for (event in events.sortedBy { it.atMillis }) {
            if (event.kind != ForegroundKind.APP_RESUMED || event.packageName in ignored) continue
            if (current != null && event.packageName != current) switches += 1
            current = event.packageName
        }
        return switches
    }

    fun isRestless(switches: Int): Boolean = switches > STEADY_MAX_SWITCHES

    // When an opened notification's app came to the front, and when the user then left it: the
    // first moment another app came to the front or the screen went off. Ignored packages (the
    // notification shade) do not count as leaving.
    fun visitAfter(
        events: List<ForegroundEvent>,
        packageName: String,
        openedAtMillis: Long,
        ignored: Set<String>,
    ): AppVisit {
        var enteredAt: Long? = null
        for (event in events.sortedBy { it.atMillis }) {
            if (event.atMillis < openedAtMillis - TAP_GRACE_MILLIS) continue
            when {
                enteredAt == null -> if (event.kind == ForegroundKind.APP_RESUMED && event.packageName == packageName) {
                    enteredAt = event.atMillis
                }
                event.kind == ForegroundKind.SCREEN_OFF -> return AppVisit(enteredAt, event.atMillis)
                event.packageName != packageName && event.packageName !in ignored ->
                    return AppVisit(enteredAt, event.atMillis)
            }
        }
        return AppVisit(enteredAt, null)
    }

    // If the opened app never shows up in the usage events by then, the tap opened something
    // the events cannot follow, and the fixed estimate is used instead.
    const val NOT_SEEN_MILLIS = 2 * 60_000L

    // Minutes an opened notification cost, from the tap until the user left its app, capped;
    // null means keep waiting. When the session is over, whatever has been seen is final.
    fun minutesLost(
        visit: AppVisit,
        openedAtMillis: Long,
        nowMillis: Long,
        capMinutes: Double,
        estimateMinutes: Double,
        sessionOver: Boolean,
    ): Double? {
        val elapsedMin = (nowMillis - openedAtMillis) / 60_000.0
        return when {
            visit.leftAtMillis != null ->
                ((visit.leftAtMillis - openedAtMillis) / 60_000.0).coerceIn(0.0, capMinutes)
            elapsedMin >= capMinutes -> capMinutes
            visit.enteredAtMillis == null &&
                (sessionOver || nowMillis - openedAtMillis >= NOT_SEEN_MILLIS) -> estimateMinutes
            sessionOver -> elapsedMin
            else -> null
        }
    }
}

// Both null until seen: the app has not come to the front yet, or the user is still in it.
data class AppVisit(val enteredAtMillis: Long?, val leftAtMillis: Long?)
