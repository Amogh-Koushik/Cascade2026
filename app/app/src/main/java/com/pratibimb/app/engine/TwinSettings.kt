package com.pratibimb.app.engine

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The user's switches over what the twin may do, saved across restarts: pause, which apps it
// manages, whether it may explore, and the content rules.
object TwinSettings {
    private const val PREFS = "twin_controls"
    private const val PAUSED_KEY = "paused"
    private const val OPTED_OUT_KEY = "opted_out_packages"
    private const val RESEARCH_KEY = "research_mode"
    private const val RULE_TEXT_ONLY_KEY = "rule_text_only_repeat"
    private const val GROUP_MARKS_KEY = "group_marks"
    private const val PAUSE_CARD_KEY = "pause_card"
    private const val RULE_NEW_SENDER_KEY = "rule_new_sender_links"
    private const val RULE_FOCUS_GATE_KEY = "rule_focus_gate"
    private const val MENTION_NAMES_KEY = "mention_names"
    private const val KNOWN_SENDERS_KEY = "known_senders"
    private const val MAX_KNOWN_SENDERS = 2000

    @Volatile
    private var prefs: SharedPreferences? = null

    // Paused: nothing is held, logged or decided; every notification arrives as normal.
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    // Apps the user turned off in per-app opt-out; their notifications are never held.
    private val _optedOutPackages = MutableStateFlow<Set<String>>(emptySet())
    val optedOutPackages: StateFlow<Set<String>> = _optedOutPackages.asStateFlow()

    // Research mode lets Auto occasionally deliver something it would have held (with its
    // propensity logged), so held categories keep producing outcomes to learn from.
    private val _researchMode = MutableStateFlow(true)
    val researchMode: StateFlow<Boolean> = _researchMode.asStateFlow()

    // Content rules, each off until switched on (see ContentPolicy).
    private val _ruleFlags = MutableStateFlow(ContentRuleFlags())
    val ruleFlags: StateFlow<ContentRuleFlags> = _ruleFlags.asStateFlow()

    // Names a group message may tag you by ("@Amogh"); "@everyone" always counts.
    private val _mentionNames = MutableStateFlow<Set<String>>(emptySet())
    val mentionNames: StateFlow<Set<String>> = _mentionNames.asStateFlow()

    // Salted hashes of every sender seen in a conversation, so "new sender" means new to this
    // phone. Tracked even with the rule off, so switching it on does not make everyone new.
    @Volatile
    private var knownSenders: Set<String> = emptySet()

    // The pause card on our alert pings. Until the user sets it, it follows the params flag, or
    // is on in the demo build (see setPauseCardDefault).
    private val _pauseCard = MutableStateFlow(false)
    val pauseCard: StateFlow<Boolean> = _pauseCard.asStateFlow()

    // Per group (salted key): worth-it taps minus not-worth-it taps. Above zero, it gets through.
    @Volatile
    private var groupMarks: Map<String, Int> = emptyMap()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val loaded = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _paused.value = loaded.getBoolean(PAUSED_KEY, false)
        _optedOutPackages.value = loaded.getStringSet(OPTED_OUT_KEY, null).orEmpty().toSet()
        _researchMode.value = loaded.getBoolean(RESEARCH_KEY, true)
        _ruleFlags.value = ContentRuleFlags(
            textOnlyRepeatPings = loaded.getBoolean(RULE_TEXT_ONLY_KEY, false),
            newSenderLinks = loaded.getBoolean(RULE_NEW_SENDER_KEY, false),
            focusGate = loaded.getBoolean(RULE_FOCUS_GATE_KEY, true),
        )
        _mentionNames.value = loaded.getStringSet(MENTION_NAMES_KEY, null).orEmpty().toSet()
        knownSenders = loaded.getStringSet(KNOWN_SENDERS_KEY, null).orEmpty().toSet()
        groupMarks = loaded.getStringSet(GROUP_MARKS_KEY, null).orEmpty().mapNotNull { entry ->
            val (key, net) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            net.toIntOrNull()?.let { key to it }
        }.toMap()
        prefs = loaded
    }

    fun setRuleFlags(flags: ContentRuleFlags) {
        _ruleFlags.value = flags
        prefs?.edit()
            ?.putBoolean(RULE_TEXT_ONLY_KEY, flags.textOnlyRepeatPings)
            ?.putBoolean(RULE_NEW_SENDER_KEY, flags.newSenderLinks)
            ?.putBoolean(RULE_FOCUS_GATE_KEY, flags.focusGate)
            ?.apply()
    }

    // From a comma-separated entry such as "Amogh, AK".
    fun setMentionNames(entry: String) {
        val names = entry.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        _mentionNames.value = names
        prefs?.edit()?.putStringSet(MENTION_NAMES_KEY, names)?.apply()
    }

    fun isKnownSender(senderHash: String): Boolean = senderHash in knownSenders

    fun setPauseCardDefault(enabled: Boolean) {
        _pauseCard.value = prefs?.takeIf { it.contains(PAUSE_CARD_KEY) }?.getBoolean(PAUSE_CARD_KEY, enabled) ?: enabled
    }

    fun setPauseCard(enabled: Boolean) {
        _pauseCard.value = enabled
        prefs?.edit()?.putBoolean(PAUSE_CARD_KEY, enabled)?.apply()
    }

    fun isGroupWorth(groupKey: String): Boolean = (groupMarks[groupKey] ?: 0) > 0

    @Synchronized
    fun markGroup(groupKey: String, worth: Boolean) {
        groupMarks = groupMarks + (groupKey to (groupMarks[groupKey] ?: 0) + if (worth) 1 else -1)
        prefs?.edit()?.putStringSet(GROUP_MARKS_KEY, groupMarks.map { (key, net) -> "$key=$net" }.toSet())?.apply()
    }

    // True the first time a sender is seen; the sender is known from then on.
    @Synchronized
    fun firstTimeSeen(senderHash: String): Boolean {
        if (senderHash in knownSenders) return false
        // A full set starts over rather than growing without bound.
        knownSenders = if (knownSenders.size >= MAX_KNOWN_SENDERS) setOf(senderHash) else knownSenders + senderHash
        prefs?.edit()?.putStringSet(KNOWN_SENDERS_KEY, knownSenders)?.apply()
        return true
    }

    fun setPaused(paused: Boolean) {
        _paused.value = paused
        prefs?.edit()?.putBoolean(PAUSED_KEY, paused)?.apply()
    }

    fun setManaged(packageName: String, managed: Boolean) {
        val updated = if (managed) _optedOutPackages.value - packageName else _optedOutPackages.value + packageName
        _optedOutPackages.value = updated
        prefs?.edit()?.putStringSet(OPTED_OUT_KEY, updated)?.apply()
    }

    fun setResearchMode(enabled: Boolean) {
        _researchMode.value = enabled
        prefs?.edit()?.putBoolean(RESEARCH_KEY, enabled)?.apply()
    }

    fun isManaged(packageName: String): Boolean = packageName !in _optedOutPackages.value

    // Delete-all puts every switch back to its default.
    fun clear() {
        _paused.value = false
        _optedOutPackages.value = emptySet()
        _researchMode.value = true
        _ruleFlags.value = ContentRuleFlags()
        _mentionNames.value = emptySet()
        knownSenders = emptySet()
        groupMarks = emptyMap()
        prefs?.edit()?.clear()?.apply()
    }
}
