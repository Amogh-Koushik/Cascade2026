package com.pratibimb.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The accent colour picked in Settings, saved across restarts. Choosing one swaps the palette
// every screen reads, so the change shows at once without restarting the app.
object AccentSettings {
    private const val PREFS = "appearance"
    private const val ACCENT_KEY = "accent"

    @Volatile
    private var prefs: SharedPreferences? = null
    private lateinit var appContext: Context

    private val _accent = MutableStateFlow(Accent.ORIGINAL)
    val accent: StateFlow<Accent> = _accent.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        val loaded = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        use(Accent.fromKey(loaded.getString(ACCENT_KEY, null)))
        prefs = loaded
    }

    fun set(accent: Accent) {
        use(accent)
        prefs?.edit()?.putString(ACCENT_KEY, accent.name)?.apply()
    }

    private fun use(accent: Accent) {
        _accent.value = accent
        WarmObsidian.palette = AppPalette.forAccent(accent, appContext)
    }
}
