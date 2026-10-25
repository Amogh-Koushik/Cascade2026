package com.pratibimb.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Toggles for the DND test screen. Held in memory only, so they reset when the app restarts.
object SpikeSettings {
    private val _pingOnPost = MutableStateFlow(false)

    // When on, every notification from another app triggers an alert ping, which lets us
    // check that a real WhatsApp stays silent in the shade while our ping still rings.
    val pingOnPost: StateFlow<Boolean> = _pingOnPost.asStateFlow()

    fun setPingOnPost(enabled: Boolean) {
        _pingOnPost.value = enabled
    }
}
