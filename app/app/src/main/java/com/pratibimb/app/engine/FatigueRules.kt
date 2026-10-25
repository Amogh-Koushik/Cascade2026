package com.pratibimb.app.engine

import com.pratibimb.twin.FatigueRisk

// The fatigue-risk proxy: one point each for a long stretch without a break, app switching
// well above this session's own average, and the late hours. A proxy shown to the user only;
// the engine never uses it to decide a hold.
object FatigueRules {
    const val LONG_STRETCH_MIN = 90.0
    const val RECENT_WINDOW_MIN = 30.0
    const val SWITCH_RISE = 1.5

    // Below this many recent switches a "rise" is noise, not a signal.
    const val MIN_RECENT_SWITCHES = 4

    const val LATE_START_HOUR = 23
    const val LATE_END_HOUR = 5

    data class Assessment(val risk: FatigueRisk, val reasons: List<String>)

    fun assess(
        minutesSinceBreak: Double,
        recentSwitches: Int,
        sessionSwitches: Int,
        sessionMinutes: Double,
        hourOfDay: Int,
    ): Assessment {
        val reasons = mutableListOf<String>()
        if (minutesSinceBreak >= LONG_STRETCH_MIN) {
            reasons += "${formatDuration(minutesSinceBreak)} since your last break"
        }
        // The session average scaled to the recent window; only meaningful once the session is
        // longer than the window itself.
        if (sessionMinutes > RECENT_WINDOW_MIN && recentSwitches >= MIN_RECENT_SWITCHES) {
            val averagePerWindow = sessionSwitches * RECENT_WINDOW_MIN / sessionMinutes
            if (recentSwitches >= SWITCH_RISE * averagePerWindow) {
                reasons += "app switching is up on your usual pace"
            }
        }
        if (hourOfDay >= LATE_START_HOUR || hourOfDay < LATE_END_HOUR) {
            reasons += "it's late"
        }
        val risk = when (reasons.size) {
            0 -> FatigueRisk.LOW
            1 -> FatigueRisk.MEDIUM
            else -> FatigueRisk.HIGH
        }
        return Assessment(risk, reasons)
    }

    fun formatDuration(minutes: Double): String {
        val total = minutes.toInt()
        val hours = total / 60
        val mins = total % 60
        return when {
            hours == 0 -> "$mins min"
            mins == 0 -> "$hours h"
            else -> "$hours h $mins min"
        }
    }
}
