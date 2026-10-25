package com.pratibimb.app.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pratibimb.app.dnd.WorthPrompts
import com.pratibimb.app.engine.TwinRouter

// Receives a "Worth it / Not worth it" tap from a notification, teaches the twin, and removes
// the notification that asked.
class WorthFeedbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WorthPrompts.ACTION) return
        val packageName = intent.getStringExtra(WorthPrompts.EXTRA_PACKAGE) ?: return
        // The app may have been killed since the alert was posted.
        TwinRouter.init(context)
        TwinRouter.recordAlertFeedback(
            liveItemId = intent.getStringExtra(WorthPrompts.EXTRA_LIVE_ID),
            packageName = packageName,
            senderHash = intent.getStringExtra(WorthPrompts.EXTRA_SENDER),
            worth = intent.getBooleanExtra(WorthPrompts.EXTRA_WORTH, false),
        )
        context.getSystemService(NotificationManager::class.java).cancel(
            intent.getStringExtra(WorthPrompts.EXTRA_TAG),
            intent.getIntExtra(WorthPrompts.EXTRA_ID, 0),
        )
    }
}
