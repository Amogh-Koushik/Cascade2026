package com.pratibimb.app.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pratibimb.app.engine.TwinRouter
import com.pratibimb.app.ui.MainActivity

// Quick-settings tile: one tap opens the app and starts focus with the chosen duration (50 min
// by default); while a session runs it shows the minutes left and opens the app to stop it.
// The session itself stays in the app, so there is only ever one timer.
class FocusTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        val remainingMs = TwinRouter.sessionEndsAtMillis - System.currentTimeMillis()
        tile.label = "Focus"
        if (remainingMs > 0) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "${remainingMs / 60_000 + 1} min left"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = "Tap to start"
        }
        tile.updateTile()
    }

    override fun onClick() {
        val running = TwinRouter.sessionEndsAtMillis > System.currentTimeMillis()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_START_FOCUS, !running)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        const val EXTRA_START_FOCUS = "com.pratibimb.app.START_FOCUS"

        // Asks the system to redraw the tile after a session starts or ends.
        fun refresh(context: Context) {
            requestListeningState(context, ComponentName(context, FocusTileService::class.java))
        }
    }
}
