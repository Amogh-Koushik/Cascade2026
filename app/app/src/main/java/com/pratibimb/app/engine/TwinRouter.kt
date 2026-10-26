package com.pratibimb.app.engine

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pratibimb.app.data.FeatureBuilder
import com.pratibimb.app.data.FeatureRules
import com.pratibimb.app.data.NotificationInspector
import com.pratibimb.app.data.PauseCardLog
import com.pratibimb.app.data.ReportStore
import com.pratibimb.app.data.NotificationLog
import com.pratibimb.app.data.TrayStore
import com.pratibimb.app.data.UsageReader
import com.pratibimb.app.dnd.AlertPing
import com.pratibimb.app.dnd.DemoNotifier
import com.pratibimb.app.dnd.DigestNotifier
import com.pratibimb.app.dnd.WorthPrompt
import com.pratibimb.app.dnd.WorthPrompts
import com.pratibimb.app.service.PratibimbListener
import com.pratibimb.app.ui.HeldTrayItem
import com.pratibimb.app.ui.LiveNotificationItem
import com.pratibimb.app.ui.pausecard.PauseCardActivity
import com.pratibimb.twin.Action
import com.pratibimb.twin.Autonomy
import com.pratibimb.twin.Category
import com.pratibimb.twin.Decision
import com.pratibimb.twin.DecisionRule
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
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

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

    private const val WORTH_PROMPT_EVERY = 3

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

    // When the running focus session ends; 0 when none is running (or during a break).
    val sessionEndsAtMillis: Long get() = sessionEndMillis

    // policy.cap_min from the params: nothing waits in the tray longer than this.
    @Volatile
    private var holdCapMin = 45.0

    // policy.minutes_lost_cap_min: the most one opened notification is charged.
    @Volatile
    private var minutesLostCapMin = 60.0

    // policy.dm_burst_to_deliver: Instagram/Snapchat 1:1 texts in 2 minutes that get through.
    @Volatile
    private var dmBurstToDeliver = ContentPolicy.BURST_TO_DELIVER

    // policy.resume_min: minutes to get back into the task after an open, for the cards.
    @Volatile
    var resumeMinutes = 0.0
        private set

    // policy.cap_by_kind: a shorter wait for people than for feeds.
    @Volatile
    private var holdCaps = HoldCaps(45.0, emptyMap())

    // The params' active topics (Placements is preloaded).
    @Volatile
    private var contexts: List<TopicContext> = emptyList()

    // never_hold.otp_source_packages without the chat apps: where bank and UPI alerts come from.
    @Volatile
    private var paymentPackages: Set<String> = emptySet()

    // Instagram and Snapchat texts per sender in the last 2 minutes (5 get through).
    private val textBursts = TextBurstCounter()

    // Pause cards in the current focus session (see PauseCardRules).
    private val _pauseCardsThisSession = MutableStateFlow(0)
    val pauseCardsThisSession: StateFlow<Int> = _pauseCardsThisSession.asStateFlow()

    @Volatile
    private var lastPauseCardAtMillis = 0L

    @Volatile
    private var pauseCardsAskedLess = false

    // params features.pause_card_min_loss_min, or the demo build's lower bar.
    @Volatile
    private var pauseCardMinLossMin = PauseCardRules.MIN_EXPECTED_LOSS_MIN

    // A ping for a card-eligible item is tagged with this prefix instead of the bare key, so a
    // tap on it is not taken as opening the original: only "Open anyway" is.
    private const val PAUSE_CARD_TAG = "pausecard:"

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

    // Why the proxy reads as it does ("1 h 50 min since your last break"), for Home's nudge.
    private val _fatigueReasons = MutableStateFlow<List<String>>(emptyList())
    val fatigueReasons: StateFlow<List<String>> = _fatigueReasons.asStateFlow()

    // What the Report tab is built from: aggregates over every focus session on this phone.
    private val _report = MutableStateFlow(ReportHistory())
    val report: StateFlow<ReportHistory> = _report.asStateFlow()

    // The last batch released as one digest; Home and the Tray show it, and the Tray can undo it
    // for a short while.
    private val _lastDigest = MutableStateFlow<ReleasedDigest?>(null)
    val lastDigest: StateFlow<ReleasedDigest?> = _lastDigest.asStateFlow()

    // The persona id (see Personas).
    private val _selectedPersona = MutableStateFlow(Personas.default.id)
    val selectedPersona: StateFlow<String> = _selectedPersona.asStateFlow()

    // The demo-learning profile uses small prior weights, so a few Worth it / Not worth it taps
    // visibly move the ranges and the decisions. On by default for the demo; not saved, so it
    // is on again after a restart.
    private val _demoLearning = MutableStateFlow(true)
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
    private var lastDetectedFatigue: FatigueRisk? = null

    @Volatile
    private var usageAccessWarned = false

    private val deliveredInFocus = AtomicInteger(0)

    // When focus time was last added to the report, and the last app seen in front, so each
    // reading adds only what happened since the one before.
    @Volatile
    private var lastBehaviourAtMillis = 0L

    @Volatile
    private var lastSwitchEventAtMillis = 0L

    @Volatile
    private var lastForegroundPackage: String? = null

    // A gap longer than this between readings (the app was asleep) is not counted as focus.
    private const val MAX_READING_GAP_MILLIS = 60_000L

    // How long a released digest can be put back into the tray.
    private const val UNDO_WINDOW_MILLIS = 60_000L

    // Loads the saved persona's params, the counts learned so far and the user's settings.
    // Both the activity and the listener call this, because after a reboot the listener can
    // start first; only the first successful call does the work.
    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        TwinSettings.init(app)
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
        _heldTray.value = TrayStore.load(app)
        _report.value = ReportStore.load(app) ?: ReportHistory(sinceMillis = System.currentTimeMillis())
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
            val resume = (policy["resume_min"] as Number).toDouble()
            val dmBurst = (policy["dm_burst_to_deliver"] as Number?)?.toInt() ?: ContentPolicy.BURST_TO_DELIVER
            val caps = HoldCaps.fromParams(params)
            val topics = Contexts.fromParams(params)
            val payments = ((params["never_hold"] as Map<String, Any?>)["otp_source_packages"] as List<Any?>)
                .map { it as String }
                .filter { MessageSignals.familyOf(it) == AppFamily.OTHER }
                .toSet()
            engine.loadParams(paramsJson)
            featureRules = rules
            holdCapMin = capMin
            minutesLostCapMin = lostCapMin
            resumeMinutes = resume
            dmBurstToDeliver = dmBurst
            holdCaps = caps
            contexts = topics
            paymentPackages = payments
            val featureFlags = params["features"] as Map<String, Any?>?
            val pauseCardParam = (featureFlags?.get("pause_card") as Boolean?) ?: false
            TwinSettings.setPauseCardDefault(pauseCardParam || BuildMode.DEMO)
            pauseCardMinLossMin = if (BuildMode.DEMO) {
                BuildMode.DEMO_PAUSE_CARD_MIN_LOSS_MIN
            } else {
                (featureFlags?.get("pause_card_min_loss_min") as Number?)?.toDouble() ?: PauseCardRules.MIN_EXPECTED_LOSS_MIN
            }
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

    // Every tray change goes through here, so the encrypted copy on disk always matches.
    private fun updateTray(change: (List<HeldTrayItem>) -> List<HeldTrayItem>) {
        _heldTray.update(change)
        appContext?.let { TrayStore.save(it, _heldTray.value) }
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
    // capMin is the cap for this notification's kind, so D charges a 1:1 message for at most
    // the 15 minutes it could wait.
    private fun currentContext(capMin: Double = holdCapMin): TwinContext {
        val now = System.currentTimeMillis()
        val inSession = sessionEndMillis > now
        val remainingMin = if (inSession) (sessionEndMillis - now) / 60_000.0 else 0.0
        val elapsedMin = if (inSession) (now - sessionStartMillis) / 60_000.0 else 0.0
        return TwinContext(
            state = _phoneState.value,
            autonomy = _autonomy.value,
            minutesUntilRelease = min(remainingMin, capMin),
            focusMinutesElapsed = elapsedMin,
            fatigueRisk = _fatigueRisk.value,
            // A fresh draw per decision lets Auto explore with probability epsilon; with research
            // mode off the draw is 1.0, which never explores.
            explorationDraw = if (TwinSettings.researchMode.value) Random.nextDouble() else 1.0,
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
        events.filterIsInstance<FeedbackEvent.WorthLabel>().forEach { label ->
            record { it.withWorth(label.packageName, label.label.positive) }
        }
    }

    // Every report change goes through here, so the copy on disk always matches.
    private fun record(change: (ReportHistory) -> ReportHistory) {
        _report.update(change)
        appContext?.let { ReportStore.save(it, _report.value) }
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
        // The app's own notifications are skipped, except demo posts standing in for another app.
        val demo = if (sbn.packageName == listener.packageName) demoScenarioOf(sbn) ?: return else null

        val appLabel = demo?.display?.app ?: getAppLabel(listener, sbn.packageName)
        val extras = sbn.notification.extras
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val title = if (rawTitle.isNotBlank()) rawTitle else appLabel
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val contentIntent = sbn.notification.contentIntent
        val content = ContentRules.classify(rawTitle, text, extras.containsKey(Notification.EXTRA_PICTURE), sbn.postTime)
        fun inspect(outcome: String) = NotificationInspector.record(sbn, rankingMap, appLabel, rawTitle, content, outcome)
        // A notification the twin leaves alone says why in the Debug log too.
        fun skip(outcome: String) {
            NotificationLog.add(appLabel, "SKIP \"$rawTitle\": $outcome", sbn.postTime)
            inspect(outcome)
        }

        if (sbn.isOngoing) return inspect("Ongoing: left alone")
        // Without params the engine cannot decide; leaving the notification alone is the safe choice.
        val rules = featureRules ?: return skip("engine not loaded, left alone")
        // A group summary ("3 new messages") usually bundles notifications decided on their own.
        // Some apps (WhatsApp on some phones) post only the summary, "Rohan: 3 new messages",
        // with no notification per message; then the summary is the notification to decide.
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 && hasGroupChildren(listener, sbn)) {
            return skip("group summary, its messages are decided on their own")
        }
        // Paused, or an app the user opted out: the notification arrives untouched and unlogged.
        val sourcePackage = demo?.features?.packageName ?: sbn.packageName
        if (TwinSettings.paused.value) return skip("paused, left alone")
        if (!TwinSettings.isManaged(sourcePackage)) return skip("app opted out, left alone")
        sweepOutcomes()

        val contentHash = contentOf(sbn)
        val previous = seenByKey[sbn.key]
        if (previous != null && previous.content == contentHash) {
            if (previous.held) cancelQuietly(listener, sbn.key)
            return skip("re-post with the same content, not decided again")
        }

        // 1. Build features from live StatusBarNotification
        val flags = TwinSettings.ruleFlags.value
        val countsAsPing = !(flags.textOnlyRepeatPings && content.kind != ContentKind.TEXT)
        val features = demo?.features?.copy(key = sbn.key, postedAtMillis = sbn.postTime)
            ?: FeatureBuilder.build(sbn, rankingMap, rules, senderSalt, countsAsPing)
        // A demo post only checks, so the same sample stays "new" every time it is fired.
        val newSender = features.senderHash?.let {
            if (demo != null) !TwinSettings.isKnownSender(it) else TwinSettings.firstTimeSeen(it)
        } ?: false
        val capMin = holdCaps.capFor(features)
        val topic = Contexts.match(rawTitle, text, features.category, contexts)
        val family = MessageSignals.familyOf(features.packageName)
        val oneToOne = features.isConversation && !features.groupChat
        // A group is known by its app and its name, salted like a sender.
        val groupName = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: rawTitle
        val groupKey = if (features.groupChat) FeatureBuilder.keyFor("${features.packageName}:$groupName", senderSalt, "g_") else null
        val instaOrSnapText = oneToOne && content.kind == ContentKind.TEXT &&
            (family == AppFamily.INSTAGRAM || family == AppFamily.SNAPCHAT)
        val hasProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val signals = FocusSignals(
            family = family,
            orderStatus = MessageSignals.isOrderStatus(sbn.notification.category, hasProgress, sbn.isOngoing, rawTitle, text),
            bankAlert = !features.groupChat && MessageSignals.isBankAlert(features.packageName, paymentPackages, rawTitle, text),
            bot = MessageSignals.isBot(family, rawTitle),
            streak = MessageSignals.isStreak(text),
            mentionsYou = ContentRules.mentionsYou(
                FeatureBuilder.messageText(sbn.notification),
                ContentRules.mentionNames(TwinSettings.mentionNames.value, FeatureBuilder.selfNames(sbn.notification)),
            ),
            groupWorth = groupKey?.let { TwinSettings.isGroupWorth(it) } ?: false,
            textBurst = if (instaOrSnapText) textBursts.record(features.senderHash ?: rawTitle, sbn.postTime) else 0,
            burstToDeliver = dmBurstToDeliver,
            newSender = newSender,
            contextLabel = topic?.label,
            nowMillis = System.currentTimeMillis(),
        )

        // 2. Engine evaluates features against the current focus session, then the focus
        // rules get their say.
        val context = currentContext(capMin)
        val ruled = ContentPolicy.apply(engine.decide(features, context), features, content, flags, signals)
        val decision = ruled.decision
        // In Suggest mode the decision is only a recommendation: the notification is delivered
        // and the user can still hold it from its detail screen.
        val holds = decision.action == Action.HOLD && !decision.suggestOnly
        if (seenByKey.size > MAX_TRACKED_KEYS) seenByKey.clear()
        seenByKey[sbn.key] = SeenPost(contentHash, holds)
        // The report covers focus only; demo posts stand in for an app and are left out too.
        if (demo == null && _phoneState.value != PhoneState.FREE) record { it.withDecision(features.packageName, holds) }

        val line = describeDecision(title, features.category, features.isConversation, decision, features.groupChat) +
            (ruled.rule?.let { " · content rule: $it" } ?: "")
        NotificationLog.add(
            source = if (demo != null) "demo: $appLabel" else appLabel,
            message = line,
            timeMillis = sbn.postTime,
        )
        inspect(line)

        // 5. Create Live Feed Item
        val liveItem = LiveNotificationItem(
            id = "live_${sbn.key.hashCode()}_${System.currentTimeMillis()}",
            app = appLabel,
            title = title,
            text = text,
            timeLabel = "Just now",
            decision = decision,
            senderDisplayName = features.senderDisplayName,
            packageName = features.packageName,
            senderHash = features.senderHash,
            key = sbn.key,
            features = features,
            context = context,
            contentIntent = contentIntent,
            groupKey = groupKey,
            pauseCardEligible = TwinSettings.pauseCard.value &&
                PauseCardRules.eligible(
                    decision,
                    ruled.rule,
                    whatsAppOneToOne = family == AppFamily.WHATSAPP && oneToOne,
                    minLossMin = pauseCardMinLossMin,
                ),
        )
        _liveNotifications.value = (listOf(liveItem) + _liveNotifications.value).take(60)

        // 6. Action Execution: HOLD vs DELIVER
        if (holds) {
            outcomes.forget(sbn.key)
            // Cancel notification from Android system shade to keep user in focus
            cancelQuietly(listener, sbn.key)

            val trayItem = HeldTrayItem(
                id = "tray_${sbn.key.hashCode()}_${System.currentTimeMillis()}",
                app = appLabel,
                title = title,
                text = text,
                sender = features.senderDisplayName,
                packageName = features.packageName,
                heldReason = decision.reason,
                timeAgo = "Just now",
                minutesUntilRelease = decision.card.minutesUntilRelease.toInt().coerceAtLeast(1),
                decision = decision,
                contentIntent = contentIntent,
                senderHash = features.senderHash,
                flag = ruled.flag,
                capMinutes = capMin,
                deadlineAtMillis = content.deadlineAtMillis,
                groupKey = groupKey,
            )
            // A group keeps one tray row, "CSE-B ×3", holding its latest message; the row keeps
            // the first one's hold time, so the cap counts from the first.
            updateTray { tray ->
                val existing = groupKey?.let { key -> tray.firstOrNull { it.groupKey == key } }
                if (existing == null) {
                    listOf(trayItem) + tray
                } else {
                    val count = existing.count + 1
                    val merged = trayItem.copy(
                        id = existing.id,
                        title = "$groupName ×$count",
                        heldAtMillis = existing.heldAtMillis,
                        count = count,
                    )
                    listOf(merged) + tray.filterNot { it.id == existing.id }
                }
            }
            _heldTodayCount.value += 1
            _netMinutesProtected.value += decision.card.netMinutesProtected
        } else {
            if (ruled.releaseSenderBurst) releaseSenderMessages(listener, features.packageName, features.senderHash)
            // A demo post has no real app behind it to open, so it teaches nothing about pull.
            if (demo == null) {
                outcomes.delivered(
                    sbn.key,
                    sbn.packageName,
                    features.category,
                    _phoneState.value,
                    System.currentTimeMillis(),
                )
            }
            if (_phoneState.value != PhoneState.FREE) {
                val pingTitle = if (ruled.releaseSenderBurst) "${features.senderDisplayName ?: title} ×${signals.textBurst}" else title
                deliverInFocus(listener, sbn.key, liveItem.copy(title = pingTitle), contentIntent)
            }
        }
    }

    // The 5th text in 2 minutes: the same person's held messages from this app go out with it.
    private fun releaseSenderMessages(context: Context, packageName: String, senderHash: String?) {
        if (senderHash == null) return
        val earlier = _heldTray.value.filter { it.packageName == packageName && it.senderHash == senderHash }
        if (earlier.isEmpty()) return
        val ids = earlier.map { it.id }.toSet()
        updateTray { tray -> tray.filterNot { it.id in ids } }
        DigestNotifier.postBatchDigest(
            context = context,
            title = "${earlier.first().app}: ${earlier.first().sender ?: earlier.first().title}",
            summaryLines = earlier.reversed().map { it.text },
        )
        NotificationLog.add("TwinRouter", "Released ${earlier.size} earlier messages from the same sender")
    }

    // Whether the app also posted individual notifications in this summary's group.
    private fun hasGroupChildren(listener: PratibimbListener, summary: StatusBarNotification): Boolean =
        try {
            listener.activeNotifications.orEmpty().any {
                it.key != summary.key &&
                    it.packageName == summary.packageName &&
                    it.groupKey == summary.groupKey &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
        } catch (e: SecurityException) {
            NotificationLog.add("TwinRouter", "Could not read active notifications: ${e.message}")
            false
        }

    // In Mode A a delivered notification is silenced by Priority mode, so the twin pings for
    // it. Unsure deliveries and one in three of the rest also ask "Worth it?" (on the ping in
    // Mode A, on a silent notification in Mode B, where the real app made its own sound).
    private fun deliverInFocus(
        listener: PratibimbListener,
        key: String,
        item: LiveNotificationItem,
        contentIntent: PendingIntent?,
    ) {
        // An unsure delivery ("might matter", or ranges too wide to trust) always asks, so the
        // twin learns where it is least sure; the rest ask one time in three.
        val unsure = item.decision.rule == DecisionRule.MIGHT_MATTER || item.decision.card.lowConfidence
        val prompt = if (deliveredInFocus.incrementAndGet() % WORTH_PROMPT_EVERY == 0 || unsure) {
            WorthPrompt(item.id, item.packageName, item.senderHash)
        } else {
            null
        }
        if (_isModeA.value) {
            val ping = AlertPing.post(
                context = listener,
                title = "[${item.app}] ${item.title}",
                text = item.text,
                contentIntent = if (item.pauseCardEligible) PauseCardActivity.pendingIntent(listener, item.id) else contentIntent,
                tag = if (item.pauseCardEligible) PAUSE_CARD_TAG + key else key,
                prompt = prompt,
            )
            NotificationLog.add("alert", "Ping for ${item.app}: ${ping.label}" + if (prompt != null) ", asks worth it" else "")
        } else if (prompt != null) {
            WorthPrompts.postSilent(listener, item.app, item.title, prompt)
            NotificationLog.add("alert", "Asked worth it for ${item.app}")
        }
    }

    private fun demoScenarioOf(sbn: StatusBarNotification): MockScenario? {
        if (sbn.notification.channelId != DemoNotifier.CHANNEL_ID) return null
        val id = sbn.notification.extras.getString(DemoNotifier.EXTRA_SCENARIO) ?: return null
        return StubEngine.SCENARIOS.firstOrNull { it.id == id }
    }

    // A "Worth it / Not worth it" tap on a delivered alert.
    fun recordAlertFeedback(liveItemId: String?, packageName: String, senderHash: String?, worth: Boolean) {
        val label = if (worth) WorthLabelKind.WORTH_IT else WorthLabelKind.NOT_WORTH_IT
        learn(FeedbackEvent.WorthLabel(packageName = packageName, senderHash = senderHash, label = label))
        _liveNotifications.value.firstOrNull { it.id == liveItemId }?.groupKey?.let { TwinSettings.markGroup(it, worth) }
        _liveNotifications.update { items ->
            items.map { if (it.id == liveItemId) it.copy(feedbackLabel = label) else it }
        }
        NotificationLog.add("TwinRouter", "Feedback from alert: $packageName " + if (worth) "worth it" else "not worth it")
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
        val mode = when {
            decision.suggestOnly -> " (suggested only)"
            decision.explored -> " · explored, propensity %.2f".format(decision.propensity)
            else -> ""
        }
        return "${decision.action.name} \"$title\" [$kind] ${decision.rule.name}: ${decision.reason}" +
            " · hold wins ${card.holdBetterPerHundred}/100 · worth up to %.2f".format(card.qUpper80) + mode
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
        // The estimate is not a measurement, so the report counts the open but adds no minutes.
        if (outcome.opened && open.state != PhoneState.FREE) {
            record { it.withOpen(open.packageName, open.category.key, null) }
        }
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
            // Only minutes actually seen in the usage events go into the report.
            val measured = minutes.takeIf { visit.enteredAtMillis != null }
            if (open.inWindow) record { it.withOpen(open.packageName, open.category.key, measured) }
            NotificationLog.add(
                "TwinRouter",
                "Feedback: ${open.category.key} opened, %.1f min lost in ${open.state}".format(minutes),
            )
        }
        learnAll(settled)
    }

    // During focus: FOCUS_STEADY or FOCUS_RESTLESS from app switches in the last ten minutes,
    // and the fatigue-risk proxy. Nothing is read in FREE. Without usage access the state stays
    // as set and the proxy uses only time since the break and the hour.
    private fun readBehaviour(context: Context, nowMillis: Long) {
        if (_phoneState.value == PhoneState.FREE || sessionStartMillis == 0L) {
            lastDetectedState = null
            lastDetectedFatigue = null
            lastBehaviourAtMillis = 0L
            lastForegroundPackage = null
            return
        }
        val hasAccess = UsageReader.hasAccess(context)
        if (!hasAccess && !usageAccessWarned) {
            NotificationLog.add("TwinRouter", "No usage access: phone state stays as set, opens use the estimate")
        }
        usageAccessWarned = !hasAccess
        val sessionEvents = if (hasAccess) UsageReader.events(context, sessionStartMillis, nowMillis) else emptyList()
        val passThrough = UsageReader.systemPackages() + UsageReader.launcherPackages(context)
        fun switchesSince(fromMillis: Long) =
            ForegroundRules.countSwitches(sessionEvents.filter { it.atMillis >= fromMillis }, passThrough)

        if (hasAccess) {
            val switches = switchesSince(nowMillis - ForegroundRules.SWITCH_WINDOW_MILLIS)
            val state = if (ForegroundRules.isRestless(switches)) PhoneState.FOCUS_RESTLESS else PhoneState.FOCUS_STEADY
            if (state != lastDetectedState) {
                lastDetectedState = state
                if (_phoneState.value != state) {
                    _phoneState.value = state
                    NotificationLog.add("TwinRouter", "Phone state $state: $switches app switches in the last 10 min")
                }
            }
        }

        if (hasAccess) recordSwitches(context, sessionEvents, passThrough)

        val sessionMinutes = (nowMillis - sessionStartMillis) / 60_000.0
        val assessment = FatigueRules.assess(
            minutesSinceBreak = sessionMinutes,
            recentSwitches = switchesSince(nowMillis - (FatigueRules.RECENT_WINDOW_MIN * 60_000).toLong()),
            sessionSwitches = switchesSince(sessionStartMillis),
            sessionMinutes = sessionMinutes,
            hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        )
        _fatigueReasons.value = assessment.reasons
        recordFocusTime(nowMillis, assessment.risk)
        // Like the phone state, a level picked on the Debug screen stays until the proxy changes.
        if (assessment.risk != lastDetectedFatigue) {
            lastDetectedFatigue = assessment.risk
            if (_fatigueRisk.value != assessment.risk) {
                _fatigueRisk.value = assessment.risk
                val why = assessment.reasons.joinToString().ifEmpty { "no signals" }
                NotificationLog.add("TwinRouter", "Fatigue risk (proxy) ${assessment.risk}: $why")
            }
        }
    }

    // Adds the focus time since the last reading to its hour, as steady or restless, and one
    // fatigue reading.
    private fun recordFocusTime(nowMillis: Long, risk: FatigueRisk) {
        val previous = lastBehaviourAtMillis
        lastBehaviourAtMillis = nowMillis
        if (previous == 0L || nowMillis - previous > MAX_READING_GAP_MILLIS) return
        val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
        val restless = _phoneState.value == PhoneState.FOCUS_RESTLESS
        record { it.withFocusTime(hour, restless, (nowMillis - previous) / 1000).withFatigue(hour, risk) }
    }

    // Counts each move to another app since the last reading (the launcher and the shade are
    // passed through, and moving to Pratibimb itself is not counted).
    private fun recordSwitches(context: Context, events: List<ForegroundEvent>, passThrough: Set<String>) {
        val fresh = events
            .filter { it.kind == ForegroundKind.APP_RESUMED && it.atMillis > lastSwitchEventAtMillis && it.packageName !in passThrough }
            .sortedBy { it.atMillis }
        for (event in fresh) {
            val previous = lastForegroundPackage
            if (previous != null && event.packageName != previous && event.packageName != context.packageName) {
                record { it.withSwitch(event.packageName) }
            }
            lastForegroundPackage = event.packageName
            lastSwitchEventAtMillis = event.atMillis
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
        if (!sessionOver) readBehaviour(context, now)
        sweepOutcomes()
        settleOpenTimers(context, now, sessionOver)
        releaseExpiredHolds(context, now)
    }

    // Each item waits at most its own cap. An item whose deadline is now within two hours is
    // released too, unless a content rule flagged it (a new sender's link stays held).
    private fun releaseExpiredHolds(context: Context, nowMillis: Long) {
        val expired = _heldTray.value.filter { item ->
            val capMillis = ((item.capMinutes ?: holdCapMin) * 60_000).toLong()
            val deadlineSoon = item.flag == null && item.deadlineAtMillis?.let {
                it - nowMillis <= ContentPolicy.DEADLINE_SOON_MILLIS
            } == true
            nowMillis - item.heldAtMillis >= capMillis || deadlineSoon
        }
        if (expired.isEmpty()) return
        val ids = expired.map { it.id }.toSet()
        updateTray { tray -> tray.filterNot { it.id in ids } }
        if (expired.size == 1) {
            val item = expired.single()
            DigestNotifier.postReleasedNotification(
                context,
                item.app,
                item.title,
                item.text,
                item.contentIntent,
                item.packageName,
            )
        } else {
            DigestNotifier.postBatchDigest(
                context = context,
                title = "Held long enough",
                summaryLines = expired.map { "${it.app}: ${it.title}" },
            )
        }
        NotificationLog.add(
            "TwinRouter",
            "Released ${expired.size} at their cap or a near deadline: " + expired.joinToString { it.title },
        )
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

    // An app's category from the active params' app map, for screens that list apps.
    fun categoryOf(packageName: String): Category = featureRules?.categoryOf(packageName) ?: Category.OTHER

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
            packageName = item.packageName,
        )
        learn(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = item.senderHash,
                label = WorthLabelKind.RELEASE_NOW,
            )
        )
        updateTray { tray -> tray.filterNot { it.id == trayId } }
    }

    fun releaseAllDigest(context: Context, title: String) {
        val currentItems = _heldTray.value
        if (currentItems.isNotEmpty()) {
            val notificationId = DigestNotifier.postBatchDigest(
                context = context,
                title = title,
                summaryLines = currentItems.map { "${it.app}: ${it.title}" },
            )
            val released = currentItems.map { it.id }.toSet()
            updateTray { tray -> tray.filterNot { it.id in released } }
            _lastDigest.value = ReleasedDigest(title, currentItems, System.currentTimeMillis(), notificationId)
        }
    }

    // Puts the last digest's items back in the tray and removes its notification, if it was
    // released within the undo window.
    fun undoLastDigest(context: Context): Boolean {
        val digest = _lastDigest.value ?: return false
        if (System.currentTimeMillis() - digest.releasedAtMillis > UNDO_WINDOW_MILLIS) return false
        digest.notificationId?.let { DigestNotifier.cancel(context, it) }
        updateTray { tray -> digest.items + tray }
        _lastDigest.value = null
        NotificationLog.add("TwinRouter", "Undo: ${digest.items.size} put back in the tray")
        return true
    }

    fun dismissLastDigest() {
        _lastDigest.value = null
    }

    // Suggest mode: the user takes the twin's advice to hold something already delivered.
    fun holdSuggested(liveItemId: String) {
        if (holdLiveItem(liveItemId)) {
            markSuggestion(liveItemId, Action.HOLD)
            NotificationLog.add("TwinRouter", "Suggestion taken: held \"$liveItemId\"")
        }
    }

    // Takes a delivered notification back: cancels it in the shade and puts it in the tray.
    private fun holdLiveItem(liveItemId: String): Boolean {
        val item = _liveNotifications.value.firstOrNull { it.id == liveItemId } ?: return false
        val key = item.key ?: return false
        activeListener?.let { cancelQuietly(it, key) }
        seenByKey[key]?.let { seenByKey[key] = it.copy(held = true) }
        outcomes.forget(key)
        val trayItem = HeldTrayItem(
            id = "tray_${key.hashCode()}_${System.currentTimeMillis()}",
            app = item.app,
            title = item.title,
            text = item.text,
            sender = item.senderDisplayName,
            packageName = item.packageName,
            heldReason = item.decision.reason,
            timeAgo = "Just now",
            minutesUntilRelease = item.decision.card.minutesUntilRelease.toInt().coerceAtLeast(1),
            decision = item.decision,
            contentIntent = item.contentIntent,
            senderHash = item.senderHash,
            capMinutes = item.features?.let { holdCaps.capFor(it) },
        )
        updateTray { listOf(trayItem) + it }
        _heldTodayCount.value += 1
        _netMinutesProtected.value += item.decision.card.netMinutesProtected
        return true
    }

    // ── Pause card ─────────────────────────────────────────────────────────
    fun resetPauseCards() {
        _pauseCardsThisSession.value = 0
        lastPauseCardAtMillis = 0L
        pauseCardsAskedLess = false
    }

    // The card for a tapped ping, or null when it should not show now; every number comes from
    // the item's Future Card and the running session.
    fun pauseCardFor(liveItemId: String): PauseCardView? {
        val item = _liveNotifications.value.firstOrNull { it.id == liveItemId } ?: return null
        val now = System.currentTimeMillis()
        if (!item.pauseCardEligible || sessionEndMillis <= now) return null
        if (!PauseCardRules.canShow(now, _pauseCardsThisSession.value, lastPauseCardAtMillis, pauseCardsAskedLess)) return null
        val card = item.decision.card
        return PauseCardView(
            liveItemId = item.id,
            app = item.app,
            title = item.title,
            packageName = item.packageName,
            senderHash = item.senderHash,
            lossLowMin = card.lP10.roundToInt(),
            lossHighMin = card.lP90.roundToInt().coerceAtLeast(card.lP10.roundToInt()),
            waitMin = card.minutesUntilRelease.roundToInt().coerceAtLeast(1),
            focusLeftMin = ((sessionEndMillis - now) / 60_000).toInt(),
        )
    }

    fun pauseCardShown(liveItemId: String) {
        _pauseCardsThisSession.value += 1
        lastPauseCardAtMillis = System.currentTimeMillis()
        logPauseCard("pause_card_shown", liveItemId)
    }

    fun pauseCardStayFocused(liveItemId: String) {
        logPauseCard("stay_focused", liveItemId)
        holdLiveItem(liveItemId)
    }

    // Opening anyway is pull, not worth: it is timed as an open, never taught as "worth it".
    fun pauseCardOpenAnyway(context: Context, liveItemId: String) {
        logPauseCard("open_anyway", liveItemId)
        _liveNotifications.value.firstOrNull { it.id == liveItemId }?.key?.let { key ->
            outcomes.opened(key, System.currentTimeMillis())?.let { startOpenTimer(it) }
        }
        openOriginal(context, liveItemId)
    }

    fun pauseCardAutoDismissed(liveItemId: String) {
        logPauseCard("auto_dismissed", liveItemId)
    }

    fun pauseCardAskLess(liveItemId: String) {
        pauseCardsAskedLess = true
        logPauseCard("ask_me_less", liveItemId)
    }

    private fun logPauseCard(event: String, liveItemId: String) {
        val app = appContext ?: return
        val item = _liveNotifications.value.firstOrNull { it.id == liveItemId }
        PauseCardLog.record(
            app,
            event,
            mapOf(
                "package" to item?.packageName,
                "category" to item?.features?.category?.key,
                "rule" to item?.decision?.rule?.name,
                "l_p50" to item?.decision?.card?.lP50,
                "minutes_until_release" to item?.decision?.card?.minutesUntilRelease,
                "card_this_session" to _pauseCardsThisSession.value,
            ),
        )
    }

    // Fires the original notification's tap action, or opens its app if that is gone.
    fun openOriginal(context: Context, liveItemId: String) {
        val item = _liveNotifications.value.firstOrNull { it.id == liveItemId } ?: return
        val intent = item.contentIntent
        if (intent != null) {
            try {
                // From Android 14 an app must opt in to lend its foreground status to a PendingIntent.
                val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle()
                } else {
                    null
                }
                intent.send(context, 0, null, null, null, null, options)
                return
            } catch (e: PendingIntent.CanceledException) {
                NotificationLog.add("TwinRouter", "Original tap action gone, opening ${item.app}")
            }
        }
        context.packageManager.getLaunchIntentForPackage(item.packageName)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // Suggest mode: the user keeps it delivered; it already is, so this only records the choice.
    fun deliverSuggested(liveItemId: String) {
        markSuggestion(liveItemId, Action.DELIVER)
    }

    private fun markSuggestion(liveItemId: String, choice: Action) {
        _liveNotifications.update { items ->
            items.map { if (it.id == liveItemId) it.copy(suggestionChoice = choice) else it }
        }
    }

    // The same notification decided again with what the twin knows now, so a detail screen can
    // show how its ranges moved since. Null for items that did not keep their inputs.
    fun decideAgain(item: LiveNotificationItem): Decision? {
        val features = item.features ?: return null
        val context = item.context ?: return null
        return engine.decide(features, context.copy(demoLearning = _demoLearning.value))
    }

    // A rule, not just a label: from now on this group, this sender or (for items with neither)
    // this app is always delivered. A group item marks only that group; marking the app would let
    // every group in it through. The item itself is released now.
    fun alwaysDeliverSender(context: Context, trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        val group = item.groupKey
        if (group != null) {
            TwinSettings.alwaysDeliverGroup(group)
            NotificationLog.add("TwinRouter", "Always deliver: group \"${item.title}\" in ${item.app}")
            DigestNotifier.postReleasedNotification(context, item.app, item.title, item.text, item.contentIntent, item.packageName)
            updateTray { tray -> tray.filterNot { it.id == trayId } }
            return
        }
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
        DigestNotifier.postReleasedNotification(
            context,
            item.app,
            item.title,
            item.text,
            item.contentIntent,
            item.packageName,
        )
        updateTray { tray -> tray.filterNot { it.id == trayId } }
    }

    // "This was important": the item is shown now, and the twin learns it should have come
    // through, for this sender and this app.
    fun markImportant(context: Context, trayId: String) {
        val item = _heldTray.value.firstOrNull { it.id == trayId } ?: return
        DigestNotifier.postReleasedNotification(
            context,
            item.app,
            item.title,
            item.text,
            item.contentIntent,
            item.packageName,
        )
        learn(
            FeedbackEvent.WorthLabel(
                packageName = item.packageName,
                senderHash = item.senderHash,
                label = WorthLabelKind.SHOULD_HAVE_COME_THROUGH,
            )
        )
        item.groupKey?.let { TwinSettings.markGroup(it, worth = true) }
        updateTray { tray -> tray.filterNot { it.id == trayId } }
        NotificationLog.add("TwinRouter", "Marked important: \"${item.title}\" released, twin taught it matters")
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
        item.groupKey?.let { TwinSettings.markGroup(it, worth = false) }
    }

    fun submitWorthFeedback(itemId: String, isWorth: Boolean) {
        val label = if (isWorth) WorthLabelKind.WORTH_IT else WorthLabelKind.NOT_WORTH_IT
        _liveNotifications.value = _liveNotifications.value.map { item ->
            if (item.id == itemId) {
                item.groupKey?.let { TwinSettings.markGroup(it, isWorth) }
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

    // A Debug scenario is posted as a silent demo notification, so it takes the real listener,
    // hold and ping path. Without the listener or the post permission it is decided directly.
    fun triggerScenario(context: Context, scenario: MockScenario) {
        if (appContext == null) {
            NotificationLog.add("TwinRouter", "Scenario ignored: engine params not loaded yet")
            return
        }
        if (activeListener != null && DemoNotifier.post(context, scenario)) return
        NotificationLog.add("TwinRouter", "Demo post unavailable, deciding the sample directly")
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
            updateTray { listOf(trayItem) + it }
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
        textBursts.clear()
        alwaysDeliverSenders = emptySet()
        alwaysDeliverPackages = emptySet()
        editSettings {
            remove(ALWAYS_SENDERS_KEY)
            remove(ALWAYS_PACKAGES_KEY)
        }
        _heldTodayCount.value = 0
        _netMinutesProtected.value = 0.0
        _lastDigest.value = null
        _liveNotifications.value = emptyList()
        TwinSettings.clear()
        _report.value = ReportHistory(sinceMillis = System.currentTimeMillis())
        appContext?.let { ReportStore.save(it, _report.value) }
        updateTray { emptyList() }
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

// The pause card's numbers for one tapped ping.
data class PauseCardView(
    val liveItemId: String,
    val app: String,
    val title: String,
    val packageName: String,
    val senderHash: String?,
    val lossLowMin: Int,
    val lossHighMin: Int,
    val waitMin: Int,
    val focusLeftMin: Int,
)

// A batch released as one digest notification, kept so it can be shown and briefly undone.
data class ReleasedDigest(
    val title: String,
    val items: List<HeldTrayItem>,
    val releasedAtMillis: Long,
    val notificationId: Int?,
) {
    // "WhatsApp ×3, Instagram": items grouped by app, in the order they were held.
    val summary: String
        get() = items.groupBy { it.app }.entries.joinToString { (app, held) ->
            if (held.size > 1) "$app ×${held.size}" else app
        }
}
