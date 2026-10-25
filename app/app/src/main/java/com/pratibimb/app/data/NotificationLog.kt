package com.pratibimb.app.data

import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LogEntry(
    val id: Long,
    val timeMillis: Long,
    val source: String,
    val message: String,
)

// Live, in-memory feed of everything the listener and the DND test do, shown on screen.
// Nothing here is written to disk; the encrypted store replaces it once the real pipeline exists.
object NotificationLog {
    private const val TAG = "PratibimbTwin"
    private const val MAX_ENTRIES = 200

    private val nextId = AtomicLong(0)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    private val _listenerConnected = MutableStateFlow(false)

    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    fun add(source: String, message: String, timeMillis: Long = System.currentTimeMillis()) {
        Log.i(TAG, "[$source] $message")
        val entry = LogEntry(nextId.incrementAndGet(), timeMillis, source, message)
        _entries.update { current -> (listOf(entry) + current).take(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun setListenerConnected(connected: Boolean) {
        Log.i(TAG, "Listener connected = $connected")
        _listenerConnected.value = connected
    }
}
