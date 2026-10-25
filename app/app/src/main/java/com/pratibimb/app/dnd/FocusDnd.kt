package com.pratibimb.app.dnd

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import androidx.core.content.edit

// Mode A silence. During focus the phone switches to Priority mode with a policy that lets
// alarms, starred callers and repeat callers through and silences everything else: no sound,
// no vibration, no pop-up, no screen wake, no light. Silenced notifications still sit in the
// shade and status bar. Pratibimb's own alert channel bypasses DND, so the only thing that
// rings is a ping the twin chose to send. The user's own filter and policy are saved first and
// put back when focus ends.
class FocusDnd(context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    // Saved to disk rather than memory so a restore still works if Android kills the app
    // in the middle of a 50-minute session.
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val hasAccess: Boolean
        get() = manager.isNotificationPolicyAccessGranted

    // True while our focus policy is in force and the user's own settings are waiting to be restored.
    val isApplied: Boolean
        get() = prefs.getBoolean(KEY_SAVED, false)

    fun enterPriority(): Boolean {
        if (!hasAccess) return false
        // Only save once, so entering twice cannot overwrite the user's real settings with ours.
        if (!isApplied) saveCurrent()
        manager.notificationPolicy = focusPolicy()
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

        // Some phones refuse the change without an error, for example when the user's own DND
        // schedule is in charge. Report what is really in force, and undo our half-applied
        // policy so the caller can fall back to hold-only Mode B.
        val applied =
            manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        if (!applied) restore()
        return applied
    }

    fun restore(): Boolean {
        if (!hasAccess || !isApplied) return false
        manager.notificationPolicy = Policy(
            prefs.getInt(KEY_CATEGORIES, 0),
            prefs.getInt(KEY_CALL_SENDERS, Policy.PRIORITY_SENDERS_ANY),
            prefs.getInt(KEY_MESSAGE_SENDERS, Policy.PRIORITY_SENDERS_ANY),
            prefs.getInt(KEY_SUPPRESSED_EFFECTS, 0),
            prefs.getInt(KEY_CONVERSATION_SENDERS, Policy.CONVERSATION_SENDERS_NONE),
        )
        manager.setInterruptionFilter(
            prefs.getInt(KEY_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL),
        )
        prefs.edit { clear() }
        return true
    }

    fun describe(): String {
        val filter = when (manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "off (all notifications)"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority only"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms only"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "total silence"
            else -> "unknown"
        }
        val access = if (hasAccess) "granted" else "missing"
        val ours = if (isApplied) "applied" else "not applied"
        return "DND $filter · focus policy $ours · DND access $access"
    }

    private fun saveCurrent() {
        val policy = manager.notificationPolicy
        prefs.edit {
            putBoolean(KEY_SAVED, true)
            putInt(KEY_FILTER, manager.currentInterruptionFilter)
            putInt(KEY_CATEGORIES, policy.priorityCategories)
            putInt(KEY_CALL_SENDERS, policy.priorityCallSenders)
            putInt(KEY_MESSAGE_SENDERS, policy.priorityMessageSenders)
            putInt(KEY_SUPPRESSED_EFFECTS, policy.suppressedVisualEffects)
            putInt(KEY_CONVERSATION_SENDERS, policy.priorityConversationSenders)
        }
    }

    // Messages and conversations are not priority categories, so they are silenced. The visual
    // effects below are suppressed only for silenced notifications: they still appear in the
    // shade and status bar, but cannot pop up, wake the screen or flash the light.
    private fun focusPolicy() = Policy(
        Policy.PRIORITY_CATEGORY_ALARMS or
            Policy.PRIORITY_CATEGORY_CALLS or
            Policy.PRIORITY_CATEGORY_REPEAT_CALLERS,
        Policy.PRIORITY_SENDERS_STARRED,
        Policy.PRIORITY_SENDERS_STARRED,
        Policy.SUPPRESSED_EFFECT_PEEK or
            Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or
            Policy.SUPPRESSED_EFFECT_LIGHTS or
            Policy.SUPPRESSED_EFFECT_AMBIENT,
        Policy.CONVERSATION_SENDERS_NONE,
    )

    private companion object {
        const val PREFS_NAME = "focus_dnd"
        const val KEY_SAVED = "saved"
        const val KEY_FILTER = "filter"
        const val KEY_CATEGORIES = "categories"
        const val KEY_CALL_SENDERS = "call_senders"
        const val KEY_MESSAGE_SENDERS = "message_senders"
        const val KEY_SUPPRESSED_EFFECTS = "suppressed_effects"
        const val KEY_CONVERSATION_SENDERS = "conversation_senders"
    }
}
