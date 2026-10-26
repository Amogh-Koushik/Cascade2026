package com.pratibimb.app.data

import android.content.Context
import com.pratibimb.app.engine.JsonText
import java.io.File
import java.io.IOException

// What happened with each pause card, one JSON line per event, kept for later analysis only:
// nothing here is learned from (opening anyway is pull, not worth). No text, no sender.
object PauseCardLog {
    private const val FILE_NAME = "pause_card_events.jsonl"

    @Synchronized
    fun record(context: Context, event: String, fields: Map<String, Any?>) {
        val row = mapOf("event" to event, "at" to System.currentTimeMillis()) + fields
        try {
            File(context.filesDir, FILE_NAME).appendText(JsonText.write(row) + "\n")
        } catch (e: IOException) {
            NotificationLog.add("PauseCardLog", "Could not record $event: ${e.message}")
        }
        NotificationLog.add("pause card", event)
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
