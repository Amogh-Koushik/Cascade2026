package com.pratibimb.app.data

import android.content.Context
import com.pratibimb.app.engine.ReportHistory
import java.io.File
import java.io.IOException

// The report history on disk. It holds aggregates only (minutes and counts per hour, app and
// category), so unlike the tray it carries nothing that needs encrypting.
object ReportStore {
    private const val FILE_NAME = "report_history.json"

    @Synchronized
    fun save(context: Context, history: ReportHistory) {
        try {
            File(context.filesDir, FILE_NAME).writeText(history.toJson())
        } catch (e: IOException) {
            NotificationLog.add("ReportStore", "Could not save the report history: ${e.message}")
        }
    }

    // Null when there is no history yet or the file cannot be read.
    fun load(context: Context): ReportHistory? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return try {
            ReportHistory.fromJson(file.readText())
        } catch (e: IOException) {
            NotificationLog.add("ReportStore", "Report history unreadable, starting over: ${e.message}")
            null
        } catch (e: RuntimeException) {
            NotificationLog.add("ReportStore", "Report history unreadable, starting over: ${e.message}")
            null
        }
    }
}
