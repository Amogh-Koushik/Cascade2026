package com.pratibimb.app.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.pratibimb.app.service.PratibimbListener

data class PermissionStatus(
    val restrictedSettingsAllowed: Boolean,
    val notificationAccess: Boolean,
    val usageAccess: Boolean,
    val dndAccess: Boolean,
    val postNotifications: Boolean,
    val batteryExemption: Boolean,
    val interruptionFilter: Int,
) {
    val grantedCount: Int
        get() = listOf(
            restrictedSettingsAllowed,
            notificationAccess,
            usageAccess,
            dndAccess,
            postNotifications,
            batteryExemption,
        ).count { it }

    val totalCount: Int = 6

    val isAllGranted: Boolean
        get() = grantedCount == totalCount

    val canProceedModeA: Boolean
        get() = notificationAccess && dndAccess && postNotifications

    val canProceedModeB: Boolean
        get() = notificationAccess && postNotifications

    companion object {
        val UNKNOWN = PermissionStatus(
            restrictedSettingsAllowed = false,
            notificationAccess = false,
            usageAccess = false,
            dndAccess = false,
            postNotifications = false,
            batteryExemption = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
        )

        // Mock status showing 3 granted and 3 missing as seen in Stitch mockup A2
        val MOCK_CHECKLIST = PermissionStatus(
            restrictedSettingsAllowed = true,
            notificationAccess = true,
            usageAccess = true,
            dndAccess = false,
            postNotifications = false,
            batteryExemption = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
        )

        fun read(context: Context): PermissionStatus {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

            val notifAccess = NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName)

            val dnd = notificationManager?.isNotificationPolicyAccessGranted == true

            val postNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val usage = appOps?.let {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName,
                    )
                }
                mode == AppOpsManager.MODE_ALLOWED
            } ?: false

            val battery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

            // On Android 13+, restricted settings is allowed if notification access was allowed or if sideload restrictions were lifted
            val restrictedAllowed = notifAccess || usage

            return PermissionStatus(
                restrictedSettingsAllowed = restrictedAllowed,
                notificationAccess = notifAccess,
                usageAccess = usage,
                dndAccess = dnd,
                postNotifications = postNotif,
                batteryExemption = battery,
                interruptionFilter = notificationManager?.currentInterruptionFilter
                    ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            )
        }
    }
}

object PermissionIntents {
    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStart(context, intent)
    }

    fun openNotificationAccess(context: Context) {
        val listener = ComponentName(context, PratibimbListener::class.java).flattenToString()
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStart(context, intent, fallback)
    }

    fun openUsageAccess(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            data = Uri.fromParts("package", context.packageName, null)
        }
        val fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStart(context, intent, fallback)
    }

    fun openDndAccess(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStart(context, intent)
    }

    fun openBatteryOptimization(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStart(context, intent, fallback)
    }

    private fun safeStart(context: Context, vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try next fallback
            } catch (_: SecurityException) {
                // Try next fallback
            }
        }
    }
}

fun interruptionFilterName(filter: Int): String = when (filter) {
    NotificationManager.INTERRUPTION_FILTER_ALL -> "normal (all notifications)"
    NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority only"
    NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms only"
    NotificationManager.INTERRUPTION_FILTER_NONE -> "total silence"
    else -> "unknown"
}
