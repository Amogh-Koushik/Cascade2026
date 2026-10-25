package com.pratibimb.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat

data class PermissionStatus(
    val notificationAccess: Boolean,
    val dndAccess: Boolean,
    val postNotifications: Boolean,
    val interruptionFilter: Int,
) {
    companion object {
        val UNKNOWN = PermissionStatus(
            notificationAccess = false,
            dndAccess = false,
            postNotifications = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
        )

        fun read(context: Context): PermissionStatus {
            val manager = context.getSystemService(NotificationManager::class.java)
            return PermissionStatus(
                notificationAccess = NotificationManagerCompat
                    .getEnabledListenerPackages(context)
                    .contains(context.packageName),
                dndAccess = manager.isNotificationPolicyAccessGranted,
                postNotifications = context.checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
                interruptionFilter = manager.currentInterruptionFilter,
            )
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
