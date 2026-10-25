package com.pratibimb.app.engine

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pratibimb.app.data.FeatureBuilder
import com.pratibimb.app.data.FeatureRules
import com.pratibimb.app.data.NotificationLog
import com.pratibimb.app.data.UsageReader
import com.pratibimb.app.dnd.AlertPing
import com.pratibimb.app.dnd.DigestNotifier
import com.pratibimb.app.service.PratibimbListener
import com.pratibimb.app.ui.HeldTrayItem
import com.pratibimb.app.ui.LiveNotificationItem
import com.pratibimb.twin.Action
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.Category
import com.pratibimb.twin.Decision
import com.pratibimb.twin.FatigueRisk
import com.pratibimb.twin.FeedbackEvent
import com.pratibimb.twin.PhoneState
import com.pratibimb.twin.RealTwinEngine
import com.pratibimb.twin.SimpleJsonParser
import com.pratibimb.twin.TwinContext
import com.pratibimb.twin.WorthLabelKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

/**
 * TwinRouter acts as the central coordinator between the Android NotificationListenerService,
 * the TwinEngine decision engine, and the Pratibimb UI state.
 */
object TwinRouter {
    private const val COUNTS_PREFS = "twin_counts"
    private const val COUNTS_KEY = "counts_json"
    private const val IDENTITY_PREFS = "twin_identity"
    private const val SALT_KEY = "sender_salt"
    private const val SETTINGS_PREFS = "twin_settings"
    private const val PERSONA_KEY = "persona_id"
    private const val AUTONOMY_KEY = "autonomy"
    private const val ALWAYS_SENDERS_KEY = "always_deliver_senders"
    private const val ALWAYS_PACKAGES_KEY = "always_deliver_packages"

    // Bounds the re-post memory; clearing it only means one extra decision for an old key.
    private const val MAX_TRACKED_KEYS = 500

    // Used when an open cannot be timed from usage events: no usage access, the open happened
    // outside focus (usage is only read during focus), or its app never showed up in the events.
    private const val OPENED_MINUTES_ESTIMATE = 5.0

    val engine = RealTwinEngine()

    // Set once params are loaded; until then incoming notifications are left untouched.
    @Volatile
    private var appContext: Context? = null

    val engineStatus: String
        get() = if (appContext != null) "ready (${currentPersona.asset})" else "not loaded"

    @Volatile
    private var currentPersona = Personas.default

    @Volatile
    private var sessionStartMillis = 0L

    @Volatile
    private var sessionEndMillis = 0L

    // policy.cap_min from the params: nothing waits in the tray longer than this.
    @Volatile
    private var holdCapMin = 45.0

    // policy.minutes_lost_cap_min: the most one opened notification is charged.
    @Volatile
    private var minutesLostCapMin = 60.0

    @Volatile
    var activeListener: PratibimbListener? = null

    // ── Observable UI states ───────────────────────────────────────────────
    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    private val _liveNotifications = MutableStateFlow<List<LiveNotificationItem>>(emptyList())
    val liveNotifications: StateFlow<List<LiveNotificationItem>> = _liveNotifications.asStateFlow()

    private val _heldTray = MutableStateFlow<List<HeldTrayItem>>(emptyList())
    val heldTray: StateFlow<List<HeldTrayItem>> = _heldTray.asStateFlow()

    private val _heldTodayCount = MutableStateFlow(0)
    val heldTodayCount: StateFlow<Int> = _heldTodayCount.asStateFlow()

    private val _netMinutesProtected = MutableStateFlow(0.0)
    val netMinutesProtected: StateFlow<Double> = _netMinutesProtected.asStateFlow()

    // ── Engine Context States ──────────────────────────────────────────────
    private val _phoneState = MutableStateFlow(PhoneState.FREE)
    val phoneState: StateFlow<PhoneState> = _phoneState.asStateFlow()

    private val _autonomy = MutableStateFlow(Autonomy.AUTO)
    val autonomy: StateFlow<Autonomy> = _autonomy.asStateFlow()

    private val _fatigueRisk = MutableStateFlow(FatigueRisk.LOW)
    val fatigueRisk: StateFlow<FatigueRisk> = _fatigueRisk.asStateFlow()

    // The persona id (see Personas).
    private val _selectedPersona = MutableStateFlow(Personas.default.id)
    val selectedPersona: StateFlow<String> = _selectedPersona.asStateFlow()

    // The demo-learning profile makes every worth estimate so uncertain that nearly every
    // message counts as "might matter", so a few taps visibly move the ranges. It is for the
    // scripted demo, never on by default, and not saved across restarts.
    private val _demoLearning = MutableStateFlow(false)
    val demoLearning: StateFlow<Boolean> = _demoLearning.asStateFlow()

    // Mode A is only claimed once DND access is confirmed in init().
    private val _isModeA = MutableStateFlow(false)
    val isModeA: StateFlow<Boolean> = _isModeA.asStateFlow()

    // Senders (salted hashes) and apps the user marked "always deliver" from the tray.
    @Volatile
    private var alwaysDeliverSenders: Set<String> = emptySet()

    @Volatile
    private var alwaysDeliverPackages: Set<String> = emptySet()

    // Feature rules (app categories, keyword lists, repeat window) come from the same params
    // file the engine decides with.
    @Volatile
    private var featureRules: FeatureRules? = null

    // Per-install secret for sender hashes, so a stored hash cannot be matched to a name.
    @Volatile
    private var senderSalt = ""

    // What was last decided for each notification key. Messaging apps re-post the same key to
    // update a count or a summary; a re-post with the same content must not be decided,
    // logged or pinged again, but a held one is cancelled again so it cannot slip back in.
    private data class SeenPost(val content: Int, val held: Boolean)

    private val seenByKey = ConcurrentHashMap<String, SeenPost>()

    private val outcomes = OutcomeTracker(openWindowMillis = 10 * 60_000L)

    // Opens made during focus, waiting for the user to leave the opened app.
    private val openTimers = CopyOnWriteArrayList<OpenedNotification>()

    // The last state the usage events showed. Detection only changes the phone state when this
    // changes, so a state picked by hand on the Debug screen stays until behaviour changes.
    @Volatile
    private var lastDetectedState: PhoneState? = null

    @Volatile
    private var usageAccessWarned = false

    // Loads the saved persona's params, the counts learned so far and the user's settings.
    // Both the activity and the listener call this, because after a reboot the listener can
    // start first; only the first successful call does the work.
    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        val settings = settingsOf(app)
        val saved = settings.getString(PERSONA_KEY, null)?.let { Personas.find(it) }
        val loaded = (saved != null && loadPersona(app, saved)) ||
            (saved != Personas.default && loadPersona(app, Personas.default))
        if (!loaded) return

        val identity = app.getSharedPreferences(IDENTITY_PREFS, Context.MODE_PRIVATE)
        senderSalt = identity.getString(SALT_KEY, null)
            ?: UUID.randomUUID().toString().also { identity.edit().putString(SALT_KEY, it).apply() }
        app.getSharedPreferences(COUNTS_PREFS, Context.MODE_PRIVATE)
            .getString(COUNTS_KEY, null)
            ?.let { engine.importCounts(it) }
        settings.getString(AUTONOMY_KEY, null)
            ?.let { name -> Autonomy.entries.firstOrNull { it.name == name } }
            ?.let { _autonomy.value = it }
        alwaysDeliverSenders = settings.getStringSet(ALWAYS_SENDERS_KEY, null).orEmpty().toSet()
        alwaysDeliverPackages = settings.getStringSet(ALWAYS_PACKAGES_KEY, null).orEmpty().toSet()
        _isModeA.value = app.getSystemService(NotificationManager::class.java)
            .isNotificationPolicyAccessGranted
        appContext = app
        NotificationLog.add("TwinRouter", "Engine ready with ${currentPersona.asset}")
    }

    // Parses everything first, so a broken file leaves the previous persona fully in place.
    @Suppress("UNCHECKED_CAST")
    private fun loadPersona(app: Context, persona: PersonaOption): Boolean {
        try {
            val paramsJson = app.assets.open(persona.asset).bufferedReader().use { it.readText() }
            val params = SimpleJsonParser(paramsJson).parse() as Map<String, Any?>
            val rules = FeatureRules.fromParams(params)
            val policy = params["policy"] as Map<String, Any?>
            val capMin = (policy["cap_min"] as Number).toDouble()
            val openWindowMin = (policy["open_window_min"] as Number).toDouble()
            val lostCapMin = (policy["minutes_lost_cap_min"] as Number).toDouble()
            engine.loadParams(paramsJson)
            featureRules = rules
            holdCapMin = capMin
            minutesLostCapMin = lostCapMin
            outcomes.openWindowMillis = (openWindowMin * 60_000).toLong()
        } catch (e: IOException) {
            NotificationLog.add("TwinRouter", "Could not load ${persona.asset}: ${e.message}")
            return false
        } catch (e: RuntimeException) {
            NotificationLog.add("TwinRouter", "${persona.asset} is not a valid params file: ${e.message}")
            return false
        }
        currentPersona = persona
        _selectedPersona.value = persona.id
        return true
    }

    private fun settingsOf(context: Context): SharedPreferences =
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    private fun editSettings(change: SharedPreferences.Editor.() -> Unit) {
        appContext?.let { settingsOf(it).edit().apply(change).apply() }
    }

    fun startSession(durationSeconds: Int) {
        val now = System.currentTimeMillis()
        sessionStartMillis = now
        sessionEndMillis = now + durationSeconds * 1000L
    }

    fun endSession() {
        sessionStartMillis = 0L
        sessionEndMillis = 0L
    }

    // D (minutes until a held item would be released) and time in focus come from the running
    // session, because the engine charges the cost of holding by how long the item will wait.
    private fun currentContext(): TwinContext {
        val now = System.currentTimeMillis()
        val inSession = sessionEndMillis > now
        val remainingMin = if (inSession) (sessionEndMillis - now) / 60_000.0 else 0.0
        val elapsedMin = if (inSession) (now - sessionStartMillis) / 60_000.0 else 0.0
        return TwinContext(
            state = _phoneState.value,
            autonomy = _autonomy.value,
            minutesUntilRelease = min(remainingMin, holdCapMin),
            focusMinutesElapsed = elapsedMin,
            fatigueRisk = _fatigueRisk.value,
            explorationDraw = 0.5,
            alwaysDeliverSenders = alwaysDeliverSenders,
            alwaysDeliverPackages = alwaysDeliverPackages,
            demoLearning = _demoLearning.value,
        )
    }

    // Every feedback goes through here, so what the twin learns survives a restart.
    private fun learn(event: FeedbackEvent) {
        learnAll(listOf(event))
    }

    private fun learnAll(events: List<FeedbackEvent>) {
        if (events.isEmpty()) return
        events.forEach { engine.applyFeedback(it) }
        saveCounts()
    }

    private fun saveCounts() {
        appContext?.getSharedPreferences(COUNTS_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(COUNTS_KEY, engine.exportCounts())
            ?.apply()
    }

    fun onListenerConnected(listener: PratibimbListener) {
        init(listener)
        activeListener = listener
        _isListenerConnected.value = true
        NotificationLog.setListenerConnected(true)
        NotificationLog.add("TwinRouter", "PratibimbListener attached")
    }

    fun onListenerDisconnected() {
        activeListener = null
        _isListenerConnected.value = false
        NotificationLog.setListenerConnected(false)
        NotificationLog.add("TwinRouter", "PratibimbListener detached")
    }

    /**
     * Core thin-slice handler: called whenever Android posts a notification.
     */
    fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
    ) {
        val listener = activeListener ?: return
        if (sbn.packageName == listener.packageName) return
        if (sbn.isOngoing) return
        // Without params the engine cannot decide; leaving the notification alone is the safe choice.
        val rules = featureRules ?: return
        // A group summary ("3 new messages") only bundles notifications decided on their own.
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        sweepOutcomes()

        val content = contentOf(sbn)
        val previous = seenByKey[sbn.key]
        if (previous != null && previous.content == content) {
            if (previous.held) cancelQuietly(listener, sbn.key)
            return
        }

        // 1. Build features from live StatusBarNotification
        val features = FeatureBuilder.build(sbn, rankingMap, rules, senderSalt)

        // 2. Engine evaluates features against the current focus session
        val decision = engine.decide(features, currentContext())
        if (seenByKey.size > MAX_TRACKED_KEYS) seenByKey.clear()
        seenByKey[sbn.key] = SeenPost(content, decision.action == Action.HOLD)

        // 4. Extract display metadata
        val appLabel = getAppLabel(listener, sbn.packageName)
        val extras = sbn.notification.extras
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val title = if (rawTitle.isNotBlank()) rawTitle else appLabel
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val contentIntent = sbn.notification.contentIntent

        NotificationLog.add(
            source = appLabel,
            message = describeDecision(
                title,
                features.category,
                features.isConversation,
                decision,
                features.groupChat,
            ),
            timeMillis = sbn.postTime,
        )

        // 5. Create Live Feed Item
        val liveItem = LiveNotificationItem(
            id = "live_${sbn.key.hashCode()}_${System.currentTimeMillis()}",
            app = appLabel,
            title = title,
            text = text,
            timeLabel = "Just now",
            decision = decision,
            senderDisplayName = features.senderDisplayName,
            packageName = sbn.packageName,
            senderHash = features.senderHash,
        )
        _liveNotifications.value = (listOf(liveItem) + _liveNotifications.value).take(60)

        // 6. Action Execution: HOLD vs DELIVER
        if (decision.action == Action.HOLD) {
            outcomes.forget(sbn.key)
            // Cancel notification from Android system shade to keep user in focus
            cancelQuietly(listener, sbn.key)

            val trayItem = HeldTrayItem(
                id = "tray_${sbn.key.hashCode()}_${System.currentTimeMillis()}",
                app = appLabel,
                title = title,
                text = text,
                sender = features.senderDisplayName,
                packageName = sbn.packageName,
                heldReason = decision.reason,
                timeAgo = "Just now",
                minutesUntilRelease = decision.card.minutesUntilRelease.toInt().coerceAtLeast(1),
                decision = decision,
                contentIntent = contentIntent,
                senderHash = features.senderHash,
            )
            _heldTray.value = listOf(trayItem) + _heldTray.value
            _heldTodayCount.value += 1
            _netMinutesProtected.value += decision.card.netMinutesProtected
        } else {
            outcomes.delivered(
                sbn.key,
                sbn.packageName,
                features.category,
                _phoneState.value,
                System.currentTimeMillis(),
            )
            // DELIVER: If in Mode A (DND on) and user is in focus, send bypass alert
            if (_isModeA.value && _phoneState.value != PhoneState.FREE) {
                val ping = AlertPing.post(
                    context = listener,
                    title = "[$appLabel] $title",
                    text = text,
                    contentIntent = contentIntent,
                    tag = sbn.key,
                )
                NotificationLog.add("alert", "Ping for $appLabel: ${ping.label}")
            }
        }
    }

    // Title and text identify a notification's content; a re-post with both unchanged is
    // the same notification, not a new message.
    private fun contentOf(sbn: StatusBarNotification): Int {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return "$title\n$text".hashCode()
    }

    private fun cancelQuietly(listener: PratibimbListener, key: String) {
        try {
            listener.cancelNotification(key)
        } catch (e: SecurityException) {
            NotificationLog.add("TwinRouter", "Could not hold $key: ${e.message}")
        }
    }

    // One readable line per decision for the Debug log: what happened, why, and the two
    // numbers behind it.
    private fun describeDecision(
        title: String,
        category: Category,
        isConversation: Boolean,
        decision: Decision,
        groupChat: Boolean = false,
    ): String {
        val card = decision.card
        val kind = when {
            groupChat -> "${category.key}, group chat"
            isConversation -> "${category.key}, conversation"
            else -> category.key
        }
        return "${decision.action.name} \"$title\" [$kind] ${decision.rule.name}: ${decision.reason}" +
            " · hold wins ${card.holdBetterPerHundred}/100 · worth up to %.2f".format(card.qUpper80)
    }

    fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        reason: Int,
    ) {
        // A tapped alert ping stands for its original, whose key it carries as its tag. The
        // app's other notifications (digests, released items) say nothing about pull.
        val key = when {
            sbn.packageName != appContext?.packageName -> sbn.key
            reason == NotificationListenerService.REASON_CLICK -> sbn.tag
            else -> null
        }
        if (key != null && reason == NotificationListenerService.REASON_CLICK) {
            outcomes.opened(key, System.currentTimeMillis())?.let { startOpenTimer(it) }
        } else if (key != null) {
            val outcome = when (reason) {
                NotificationListenerService.REASON_CANCEL,
                NotificationListenerService.REASON_CANCEL_ALL -> outcomes.dismissed(key)
                // Cancelled by the app itself (often because the user opened it some other way)
                // or by the system: not evidence either way, so it is not learned from.
                else -> {
                    outcomes.forget(key)
                    null
                }
            }
            outcome?.let {
                learn(it)
                NotificationLog.add("TwinRouter", "Feedback: ${it.category.key} dismissed unopened in ${it.state}")
            }
        }
        sweepOutcomes()
    }

    // An open during focus is timed from usage events until the user leaves the app; any other
    // open is learned at once with the estimate.
    private fun startOpenTimer(open: OpenedNotification) {
        val app = appContext
        val timed = open.inWindow && open.state != PhoneState.FREE && app != null && UsageReader.hasAccess(app)
        if (timed) {
            openTimers += open
            NotificationLog.add("TwinRouter", "Feedback: ${open.category.key} opened, timing until you leave it")
            return
        }
        val outcome = open.outcome(OPENED_MINUTES_ESTIMATE)
        learn(outcome)
        val what = if (outcome.opened) "opened (estimated ${OPENED_MINUTES_ESTIMATE.toInt()} min)" else "opened too late, counted as not opened"
        NotificationLog.add("TwinRouter", "Feedback: ${open.category.key} $what in ${open.state}")
    }

    private fun settleOpenTimers(context: Context, nowMillis: Long, sessionOver: Boolean) {
        if (openTimers.isEmpty()) return
        val from = openTimers.minOf { it.openedAtMillis } - ForegroundRules.TAP_GRACE_MILLIS
        val events = UsageReader.events(context, from, nowMillis)
        val ignored = UsageReader.systemPackages()
        val settled = mutableListOf<FeedbackEvent>()
        for (open in openTimers) {
            val visit = ForegroundRules.visitAfter(events, open.packageName, open.openedAtMillis, ignored)
            val minutes = ForegroundRules.minutesLost(
                visit = visit,
                openedAtMillis = open.openedAtMillis,
                nowMillis = nowMillis,
                capMinutes = minutesLostCapMin,
                estimateMinutes = OPENED_MINUTES_ESTIMATE,
                sessionOver = sessionOver,
            ) ?: continue
            openTimers.remove(open)
            settled += open.outcome(minutes)
            NotificationLog.add(
                "TwinRouter",
                "Feedback: ${open.category.key} opened, %.1f min lost in ${open.state}".format(minutes),
            )
        }
        learnAll(settled)
    }

    // FOCUS_STEADY or FOCUS_RESTLESS from app switches since the session (re)started, over the
    // last ten minutes. Nothing is read in FREE.
    private fun detectPhoneState(context: Context, nowMillis: Long) {
        if (_phoneState.value == PhoneState.FREE || sessionStartMillis == 0L) {
            lastDetectedState = null
            return
        }
        if (!UsageReader.hasAccess(context)) {
            if (!usageAccessWarned) {
                usageAccessWarned = true
                NotificationLog.add("TwinRouter", "No usage access: phone state stays as set, opens use the estimate")
            }
            return
        }
        usageAccessWarned = false
        val from = maxOf(nowMillis - ForegroundRules.SWITCH_WINDOW_MILLIS, sessionStartMillis)
        val events = UsageReader.events(context, from, nowMillis)
        val passThrough = UsageReader.systemPackages() + UsageReader.launcherPackages(context)
        val switches = ForegroundRules.countSwitches(events, passThrough)
        val detected = if (ForegroundRules.isRestless(switches)) PhoneState.FOCUS_RESTLESS else PhoneState.FOCUS_STEADY
        if (detected == lastDetectedState) return
        lastDetectedState = detected
        if (_phoneState.value != detected) {
            _phoneState.value = detected
            NotificationLog.add("TwinRouter", "Phone state $detected: $switches app switches in the last 10 min")
        }
    }

    // Delivered notifications nobody opened within the open window are learned as not opened.
    private fun sweepOutcomes() {
        val expired = outcomes.expired(System.currentTimeMillis())
        if (expired.isEmpty()) return
        learnAll(expired)
        NotificationLog.add("TwinRouter", "Feedback: ${expired.size} delivered, not opened in time")
    }

    // Called every few seconds while focus runs, and once more with sessionOver when it ends:
    // updates the phone state, settles pull outcomes and releases anything that has waited for
    // the hold cap.
    fun tick(context: Context, sessionOver: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!sessionOver) detectPhoneState(context, now)
        sweepOutcomes()
        settleOpenTimers(context, now, sessionOver)
        releaseExpiredHolds(context, now)
    }

    private fun releaseExpiredHolds(context: Context, nowMillis: Long) {
        val capMillis = (holdCapMin * 60_000).toLong()
        val expired = _heldTray.value.filter { nowMillis - it.heldAtMillis >= capMillis }
        if (expired.isEmpty()) return
        val ids = expired.map { it.id }.toSet()
        _heldTray.value = _heldTray.value.filterNot { it.id in ids }
        if (expired.size == 1) {
            val item = expired.single()
            DigestNotifier.postReleasedNotification(context, item.app, item.title, item.text, item.contentIntent)
        } else {
            DigestNotifier.postBatchDigest(
                context = context,
                title = "Held for ${holdCapMin.toInt()} minutes",
                summaryLines = expired.map { "${it.app}: ${it.title}" },
            )
        }
        NotificationLog.add("TwinRouter", "Hold cap: released ${expired.size} after ${holdCapMin.toInt()} min")
    }

    // ── User Interactive Controls ──────────────────────────────────────────
    fun setModeA(modeA: Boolean) {
        _isModeA.value = modeA
    }

    fun setPhoneState(state: PhoneState) {
        _phoneState.value = state
    }

    fun setAutonomy(autonomy: Autonomy) {
        _autonomy.value = autonomy
        editSettings { putString(AUTONOMY_KEY, autonomy.name) }
    }

    fun setFatigueRisk(risk: FatigueRisk) {
        _fatigueRisk.value = risk
    }

    fun setDemoLearning(enabled: Boolean) {
        _demoLearning.value = enabled
        NotificationLog.add("TwinRouter", "Demo learning " + if (enabled) "on" else "off")
    }

    // Loads another persona's priors; the learned counts stay. Returns false if the persona is
    // unknown or its file could not be loaded, in which case nothing changes.
    @Synchronized
    fun setPersona(idOrLabel: String): Boolean {
        val app = appContext ?: return false
        val persona = Personas.find(idOrLabel)
        if (persona == null) {
            NotificationLog.add("TwinRouter", "Unknown persona \"$idOrLabel\"")
            return false
        }
        if (persona == currentPersona) return true
        if (!loadPersona(app, persona)) return false
        editSettings { putString(PERSONA_KEY, persona.id) }
        NotificationLog.add("TwinRouter", "Persona switched to ${persona.label} (${persona.asset})")
        return true
    }

    // Pushes the bundled simulated outcomes through the normal learning path. Returns how
    // many were applied.
    fun runLearningReplay(): Int {
        val app = appContext ?: return 0
        val events = try {
            LearningReplay.parse(app.assets.open(LearningReplay.ASSET).bufferedReader().use { it.readText() })
        } catch (e: IOException) {
            NotificationLog.add("TwinRouter", "Could not load ${LearningReplay.ASSET}: ${e.message}")
            return 0
        } catch (e: RuntimeException) {
            NotificationLog.add("TwinRouter", "${LearningReplay.ASSET} is not a valid replay: ${e.message}")
            return 0
        }
        learnAll(events)
        NotificationLog.add("TwinRouter", "Learning replay: ${events.size} simulated outcomes applied")
        return events.size
    }

    fun releaseHeldItem(context: Context, trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        DigestNotifier.postReleasedNotification(
            context = context,
            app = item.app,
            title = item.title,
            text = item.text,
            contentIntent = item.contentIntent,
        )
        learn(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = item.senderHash,
                label = WorthLabelKind.RELEASE_NOW,
            )
        )
        _heldTray.value = _heldTray.value.filterNot { it.id == trayId }
    }

    fun releaseAllDigest(context: Context, title: String) {
        val currentItems = _heldTray.value
        if (currentItems.isNotEmpty()) {
            DigestNotifier.postBatchDigest(
                context = context,
                title = title,
                summaryLines = currentItems.map { "${it.app}: ${it.title}" },
            )
            _heldTray.value = emptyList()
        }
    }

    // A rule, not just a label: from now on this sender (or, for items with no sender, this
    // app) is always delivered by the never-hold overrides. The item itself is released now.
    fun alwaysDeliverSender(context: Context, trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        val sender = item.senderHash
        if (sender != null) {
            alwaysDeliverSenders = alwaysDeliverSenders + sender
            editSettings { putStringSet(ALWAYS_SENDERS_KEY, alwaysDeliverSenders) }
        } else {
            alwaysDeliverPackages = alwaysDeliverPackages + item.packageName
            editSettings { putStringSet(ALWAYS_PACKAGES_KEY, alwaysDeliverPackages) }
        }
        NotificationLog.add(
            "TwinRouter",
            "Always deliver: " + if (sender != null) "${item.sender ?: "this sender"} in ${item.app}" else item.app,
        )
        learn(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = sender,
                label = WorthLabelKind.ALWAYS_DELIVER,
            )
        )
        DigestNotifier.postReleasedNotification(context, item.app, item.title, item.text, item.contentIntent)
        _heldTray.value = _heldTray.value.filterNot { it.id == trayId }
    }

    fun markHeldWrong(trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        learn(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = item.senderHash,
                label = WorthLabelKind.SHOULD_HAVE_COME_THROUGH,
            )
        )
        _heldTray.value = _heldTray.value.filterNot { it.id == trayId }
    }

    fun markHeldCorrect(trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        learn(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = item.senderHash,
                label = WorthLabelKind.CORRECT_HOLD,
            )
        )
    }

    fun submitWorthFeedback(itemId: String, isWorth: Boolean) {
        val label = if (isWorth) WorthLabelKind.WORTH_IT else WorthLabelKind.NOT_WORTH_IT
        _liveNotifications.value = _liveNotifications.value.map { item ->
            if (item.id == itemId) {
                learn(
                    FeedbackEvent.WorthLabel(
                        packageName = item.packageName,
                        senderHash = item.senderHash,
                        label = label,
                    )
                )
                item.copy(feedbackLabel = label)
            } else {
                item
            }
        }
    }

    fun triggerScenario(scenario: MockScenario) {
        if (appContext == null) {
            NotificationLog.add("TwinRouter", "Scenario ignored: engine params not loaded yet")
            return
        }
        val currentContext = scenario.context.copy(
            state = _phoneState.value,
            autonomy = _autonomy.value,
            fatigueRisk = _fatigueRisk.value,
            alwaysDeliverSenders = alwaysDeliverSenders,
            alwaysDeliverPackages = alwaysDeliverPackages,
            demoLearning = _demoLearning.value,
        )

        val decision = engine.decide(scenario.features, currentContext)
        NotificationLog.add(
            source = "sample: ${scenario.display.app}",
            message = describeDecision(
                scenario.display.title,
                scenario.features.category,
                scenario.features.isConversation,
                decision,
            ),
        )

        val liveItem = LiveNotificationItem(
            id = "sc_${System.currentTimeMillis()}_${scenario.id}",
            app = scenario.display.app,
            title = scenario.display.title,
            text = scenario.display.text,
            timeLabel = "Just now",
            decision = decision,
            senderDisplayName = scenario.features.senderDisplayName,
            packageName = scenario.features.packageName,
            senderHash = scenario.features.senderHash,
        )

        _liveNotifications.value = listOf(liveItem) + _liveNotifications.value

        if (decision.action == Action.HOLD) {
            _heldTodayCount.value += 1
            _netMinutesProtected.value += decision.card.netMinutesProtected

            val trayItem = HeldTrayItem(
                id = "tray_${System.currentTimeMillis()}_${scenario.id}",
                app = scenario.display.app,
                title = scenario.display.title,
                text = scenario.display.text,
                sender = scenario.features.senderDisplayName,
                packageName = scenario.features.packageName,
                heldReason = decision.reason,
                timeAgo = "Just now",
                minutesUntilRelease = decision.card.minutesUntilRelease.toInt().coerceAtLeast(1),
                decision = decision,
                senderHash = scenario.features.senderHash,
            )
            _heldTray.value = listOf(trayItem) + _heldTray.value
        }
    }

    // Starts the twin over: learned counts, always-deliver rules and today's numbers.
    fun resetAll() {
        engine.resetCounts()
        saveCounts()
        FeatureBuilder.reset()
        seenByKey.clear()
        outcomes.clear()
        openTimers.clear()
        alwaysDeliverSenders = emptySet()
        alwaysDeliverPackages = emptySet()
        editSettings {
            remove(ALWAYS_SENDERS_KEY)
            remove(ALWAYS_PACKAGES_KEY)
        }
        _heldTodayCount.value = 0
        _netMinutesProtected.value = 0.0
        _heldTray.value = emptyList()
    }

    private fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}
