package com.pratibimb.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import com.pratibimb.app.engine.ForegroundEvent
import com.pratibimb.app.engine.ForegroundKind

// Reads foreground-app and screen-off events from Android's usage stats. Only called while a
// focus session runs or an opened notification is being timed; nothing read here is stored.
object UsageReader {
    // The notification shade and lock screen come to the front without the user changing app.
    private const val SYSTEM_UI = "com.android.systemui"

    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun events(context: Context, fromMillis: Long, toMillis: Long): List<ForegroundEvent> {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val usage = manager.queryEvents(fromMillis, toMillis) ?: return emptyList()
        val events = mutableListOf<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (usage.hasNextEvent()) {
            usage.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundKind.APP_RESUMED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ForegroundKind.SCREEN_OFF
                else -> continue
            }
            events += ForegroundEvent(event.timeStamp, event.packageName.orEmpty(), kind)
        }
        return events
    }

    // Packages that never count as "an app the user moved to".
    fun systemPackages(): Set<String> = setOf(SYSTEM_UI)

    // The launcher is passed through when counting switches, so home-then-app is one switch.
    fun launcherPackages(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    }
}
