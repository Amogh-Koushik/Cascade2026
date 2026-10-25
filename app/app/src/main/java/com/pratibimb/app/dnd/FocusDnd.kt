package com.pratibimb.app.dnd

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import androidx.core.content.edit

// Mode A silence. During focus the phone switches to Priority mode with a policy that lets
// alarms, starred callers and repeat callers through and no app notifications. The user's own
// filter and policy are saved first and put back when focus ends.
class FocusDnd(context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    // Saved to disk rather than memory so a restore still works if Android kills the app
    // in the middle of a 50-minute session.
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val hasAccess: Boolean
        get() = manager.isNotificationPolicyAccessGranted

    fun enterPriority(): Boolean {
        if (!hasAccess) return false
        // Only save once, so pressing "on" twice cannot overwrite the user's real settings
        // with our own focus policy.
        if (!prefs.getBoolean(KEY_SAVED, false)) saveCurrent()
        manager.notificationPolicy = focusPolicy()
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return true
    }

    fun restore(): Boolean {
        if (!hasAccess || !prefs.getBoolean(KEY_SAVED, false)) return false
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

    // No visual effects are suppressed, so silenced notifications still appear in the shade.
    private fun focusPolicy() = Policy(
        Policy.PRIORITY_CATEGORY_ALARMS or
            Policy.PRIORITY_CATEGORY_CALLS or
            Policy.PRIORITY_CATEGORY_REPEAT_CALLERS,
        Policy.PRIORITY_SENDERS_STARRED,
        Policy.PRIORITY_SENDERS_STARRED,
        0,
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
