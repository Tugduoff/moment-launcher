package app.moment.launcher

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.moment.launcher.ai.MuseClient
import app.moment.launcher.ai.MuseJobs
import app.moment.launcher.ai.Outfits
import app.moment.launcher.system.Widgets
import app.moment.launcher.ui.Skin
import app.moment.launcher.ui.Style
import app.moment.launcher.ui.Theme
import app.moment.launcher.ai.Agent
import app.moment.launcher.ai.AgentHost
import app.moment.launcher.ai.AgentPlan
import app.moment.launcher.ai.PlanStep
import app.moment.launcher.ai.Voice
import app.moment.launcher.data.ContextEngine
import app.moment.launcher.data.ContextStore
import app.moment.launcher.data.MomentContext
import app.moment.launcher.data.Mood
import app.moment.launcher.data.NowItem
import app.moment.launcher.data.Place
import app.moment.launcher.data.Proposal
import app.moment.launcher.data.Signals
import app.moment.launcher.data.Trigger
import app.moment.launcher.system.InstalledApp
import app.moment.launcher.system.Phone
import app.moment.launcher.system.RealSignals
import app.moment.launcher.system.Body
import app.moment.launcher.system.Senses
import app.moment.launcher.ai.Weather
import app.moment.launcher.ai.WeatherNow
import app.moment.launcher.ai.Postcard
import app.moment.launcher.ai.Backdrops
import app.moment.launcher.data.Look
import app.moment.launcher.data.cleanAccessory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

data class DemoState(
    val clockOffsetMs: Long = 0,
    val place: Place = Place.HOME,
    val carBluetooth: Boolean = false,
    val headphones: Boolean = false,
    val raining: Boolean = false,
    /** True once the demo panel or hook changed anything: real weather then stays out of the way. */
    val touched: Boolean = false,
    /** Demo overrides for the body signals. */
    val steps: Int? = null,
    val battery: Int? = null,
)

/** A briefing: a title, a short companion line, and a few facts (each one line). */
data class Brief(val title: String, val line: String, val facts: List<String>, val evening: Boolean)

data class UiState(
    val time: String = "",
    val date: String = "",
    val signals: Signals? = null,
    val context: MomentContext? = null,
    val items: List<NowItem> = emptyList(),
    val hiddenApps: List<String> = emptyList(),
    val focusUnlocked: Boolean = false,
    val openAnywaySeconds: Int? = null,
    val mood: Mood = Mood.ASLEEP,
    val bubble: String? = null,
    val bubbleIsIdea: Boolean = false,
    val pattern: String? = null,
    val proposal: Proposal? = null,
    val editing: Boolean = false,
    val thinking: Boolean = false,
    val query: String = "",
    val results: List<InstalledApp> = emptyList(),
    val demo: DemoState = DemoState(),
    val demoOpen: Boolean = false,
    val apps: List<InstalledApp> = emptyList(),
    val museStatus: String = "",
    val hop: Int = 0,
    /** Accessory layers drawn over the body, bottom first. */
    val layers: List<androidx.compose.ui.graphics.ImageBitmap> = emptyList(),
    val dressing: Boolean = false,
    val listening: Boolean = false,
    val plan: AgentPlan? = null,
    val weather: WeatherNow? = null,
    val realCar: String? = null,
    val voiceLevel: Float = 0f,
    val speak: Int = 0,
    val showWardrobe: Boolean = false,
    val showContexts: Boolean = false,
    val showOnboarding: Boolean = false,
    /** Long-press editing: the row being edited, and the background menu. */
    val rowEdit: NowItem? = null,
    val backdropMenu: Boolean = false,
    /** "What I can see": about me, memory, and the switches. */
    val showSenses: Boolean = false,
    /** A conversation with the companion: lines as (fromUser, text), shown above it while [chatting]. */
    val chat: List<Pair<Boolean, String>> = emptyList(),
    val chatting: Boolean = false,
    val dizzy: Int = 0,
    /** Focus guard: a hidden app someone tried to open, and the wait before it opens. */
    val guard: InstalledApp? = null,
    val guardSeconds: Int? = null,
    /** Rain, stars and glow can be switched off with the small button by the clock. */
    val effectsOff: Boolean = false,
    val aboutMe: String = "",
    val memory: List<String> = emptyList(),
    val sense: Map<String, Boolean> = emptyMap(),
    val nextEvent: Senses.Event? = null,
    val homeSaved: Boolean = false,
    /** The companion-painted background of the current context, if any. */
    val backdrop: androidx.compose.ui.graphics.ImageBitmap? = null,
    /** A context the user picked by hand in the Contexts list; it wins until [pinnedUntil]. */
    val pinnedId: String? = null,
    val pinnedUntil: Long = 0,
    val wardrobe: List<String> = emptyList(),
    val contexts: List<MomentContext> = emptyList(),
    /** Built-in sample contexts (study, driving…): off by default, switched on from the demo panel. */
    val demoContexts: Boolean = false,
    /** Widgets on the home screen (AppWidgetHost ids), and the picker. */
    val widgetIds: List<Int> = emptyList(),
    val widgetPicker: Boolean = false,
    /** The widget being resized, and a counter bumped on every size change (to redraw). */
    val editingWidget: Int? = null,
    /** Bumped to focus the search field (swipe up). */
    val searchFocus: Int = 0,
    /** What the companion reacts to on its own: "music", "rain", "late", "charging", or null. */
    val vibe: String? = null,
    /** Morning or evening briefing card, shown once per half-day on the home screen. */
    val brief: Brief? = null,
    val holdNotifications: Boolean = false,
    /** Saved looks, and which context uses which (context id → theme name). */
    val themes: List<Theme> = emptyList(),
    val contextThemes: Map<String, String> = emptyMap(),
    val widgetSizes: Int = 0,
    /** Favourite contacts (first names shown), and the picker with every contact. */
    val favourites: List<String> = emptyList(),
    val contactPicker: Boolean = false,
    /** Style sheet (font, clock), and accessory colours by layer slug. */
    val showStyle: Boolean = false,
    val tints: Map<String, Int> = emptyMap(),
    val allContacts: List<String> = emptyList(),
)

private const val CITY = "Paris, France"
private val SENSE_PERMISSION = mapOf(
    "calendar" to android.Manifest.permission.READ_CALENDAR,
    "contacts" to android.Manifest.permission.READ_CONTACTS,
    "location" to android.Manifest.permission.ACCESS_COARSE_LOCATION,
)

class MomentViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ContextStore(app)
    val phone = Phone(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var lastContextId: String? = null
    private var greetingJob: Job? = null
    private var dismissStreak = 0
    private var showOffUntil = 0L
    private val layerCache = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()
    /** Layers being drawn right now, so the same accessory is never requested twice. */
    private val drawing = mutableSetOf<String>()
    /** Layers that failed recently: not retried for 10 minutes (recompute runs every 5 s). */
    private val drawFailedAt = mutableMapOf<String, Long>()

    val widgets = Widgets(app).also { w -> w.onLongPress = { id -> _ui.update { s -> s.copy(editingWidget = id) } } }
    private val real = RealSignals(app)
    val body = Body(app)
    val senses = Senses(app)
    // Declared before init: recompute() already runs from the init block below.
    private val uiPrefs = app.getSharedPreferences("moment_ui", android.content.Context.MODE_PRIVATE)
    private val backdropCache = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap?>()
    private var painting = false
    private var lowBatteryWarned = false
    private var lastLoc: Pair<Long, android.location.Location?> = 0L to null

    init {
        real.start(); body.start()
        Outfits.dropLegacy(app)
        Style.current = Style.decode(app.getSharedPreferences("moment_ui", android.content.Context.MODE_PRIVATE).getString("style", null))
        Skin.current = Skin.decode(app.getSharedPreferences("moment_ui", android.content.Context.MODE_PRIVATE).getString("skin", null))
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) { body.steps.collect { recompute() } }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) { body.shakes.collect { n -> if (n > 0) shaken() } }
        _ui.update { it.copy(effectsOff = uiPrefs.getBoolean("effects_off", false), demoContexts = uiPrefs.getBoolean("demo_contexts", false), holdNotifications = uiPrefs.getBoolean("hold_notifications", false),
            themes = Theme.list(uiPrefs.getString("themes", null)), contextThemes = loadContextThemes(), widgetIds = widgets.ids()) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) { real.devices.collect { d -> _ui.update { it.copy(realCar = d.carName) }; recompute() } }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) { while (true) { refreshWeather(); delay(30 * 60_000L) } }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) { refreshApps() }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            while (true) { recompute(); delay(5_000) }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) { classifyAppsIfNeeded() }
        _ui.update { it.copy(museStatus = if (MuseClient.hasKey) "Muse Spark ready" else "No API key in this build") }
    }

    // ---- Signals -----------------------------------------------------------------------------

    private fun now(): Calendar = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis() + _ui.value.demo.clockOffsetMs
    }

    private fun signals(): Signals {
        val c = now(); val d = _ui.value.demo
        val dow = c.get(Calendar.DAY_OF_WEEK)
        return Signals(
            minuteOfDay = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE),
            weekday = dow != Calendar.SATURDAY && dow != Calendar.SUNDAY,
            dayLabel = SimpleDateFormat("EEEE", Locale.ENGLISH).format(c.time),
            place = realPlace(c) ?: d.place,
            // Real signals OR the demo panel's fake ones.
            carBluetooth = d.carBluetooth || real.devices.value.car,
            headphones = d.headphones || real.devices.value.headphones,
            // Real weather only counts on the live clock: a faked demo time keeps its own sky.
            raining = d.raining || (_ui.value.weather?.rainingNow == true && d.clockOffsetMs == 0L && !d.touched),
            steps = d.steps ?: body.steps.value,
            battery = d.battery ?: body.battery()?.percent,
            charging = d.battery == null && body.battery()?.charging == true,
        )
    }

    private fun timeText(s: Signals) = "%s %02d:%02d".format(s.dayLabel, s.minuteOfDay / 60, s.minuteOfDay % 60)

    /** Re-runs the rules. Cheap and local: called on every tick and every signal change. */
    fun recompute() {
        val s = signals()
        val c = now()
        val apps = _ui.value.apps
        val categories = store.appCategories()
        val distracting = apps.map { it.label }.filter { label ->
            categories[label] == "distracting" || ContextEngine.DISTRACTING_DEFAULTS.any { it.equals(label, true) }
        }
        val user = store.userContexts()
        val st0 = _ui.value
        val pinned = st0.pinnedId?.takeIf { System.currentTimeMillis() < st0.pinnedUntil }?.let { id -> pickable(s, user, distracting).firstOrNull { it.id == id } }
        if (pinned == null && st0.pinnedId != null) _ui.update { it.copy(pinnedId = null) }
        val ctx = pinned ?: ContextEngine.resolve(s, user, distracting, st0.demoContexts)
        val hidden = if (ctx.focus) (ctx.hide + distracting).distinct() else emptyList()
        holdNotifications(ctx.focus && uiPrefs.getBoolean("hold_notifications", false), hidden, apps)
        val manual = store.itemsFor(ctx.id)
        val event = if (on("calendar")) senses.nextEvent(System.currentTimeMillis()) else null
        val friend = if (on("contacts")) favourites().firstOrNull() else null
        val base = (manual ?: ctx.items.sortedByDescending { store.tapCount(ctx.id, it.label) }).map { item ->
            if (item.kind == "call" && friend != null) item.copy(label = "Call ${friend.name.substringBefore(' ')}", detail = "", target = friend.number) else item
        }
        // The next event is a small line under the date, never one of the context's rows:
        // it used to fill any free row slot, which looked like a row the user never added.
        val items = base
        eventNudge(event)
        val pattern = ContextEngine.patternAt(s, store.visits(), user, store.dismissedPlaces())
            .takeIf { dismissStreak < 2 }

        val worn = wornIn(ctx)
        val layers = worn.mapNotNull { shownLayer(it) }
        worn.filter { layerImage(it) == null }.forEach { prefetch(it) }
        bodyMoments(s)
        val hourNow = s.minuteOfDay / 60
        val music = runCatching { (getApplication<Application>().getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager).isMusicActive }.getOrDefault(false)
        val vibe = when {
            music -> "music"
            s.raining -> "rain"
            hourNow in 0..4 -> "late"
            s.charging -> "charging"
            else -> null
        }
        maybeBrief(s, c)
        if (vibe != _ui.value.vibe) {
            _ui.update { it.copy(vibe = vibe) }
            when (vibe) { "music" -> say("♪ Good one.", 3_000); "late" -> say("It's late… *yawn*", 3_500); else -> Unit }
        }
        val backdrop = backdropFor(ctx)
        applyLookFor(ctx)
        val changed = ctx.id != lastContextId
        if (changed) {
            lastContextId = ctx.id
            _ui.update { it.copy(focusUnlocked = false, openAnywaySeconds = null) }
            if (ctx.greeting.isNotBlank()) say(ctx.greeting, 5_000)
        }
        _ui.update {
            it.copy(
                time = if (Style.current.h12) "%d:%02d".format((c.get(Calendar.HOUR) + 11) % 12 + 1, c.get(Calendar.MINUTE))
                    else "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)),
                date = SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(c.time),
                signals = s, context = ctx, items = items, hiddenApps = hidden, pattern = pattern, layers = layers, backdrop = backdrop,
                nextEvent = event,
                wardrobe = worn, contexts = user,
            )
        }
        updateMood()
        if (it_query().isNotBlank()) search(it_query())
    }

    private fun it_query() = _ui.value.query

    private fun updateMood() = _ui.update { st ->
        val ctxMood = st.context?.mood ?: Mood.ASLEEP
        val mood = when {
            st.listening -> Mood.AWAKE
            st.thinking || st.dressing -> Mood.THINKING
            System.currentTimeMillis() < showOffUntil -> Mood.AWAKE
            st.proposal != null || st.plan != null -> Mood.TALKING
            st.chatting -> Mood.AWAKE
            st.pattern != null -> Mood.AWAKE
            // On its own, it reacts to the moment: nods along to music, gets sleepy after midnight.
            st.vibe == "music" && ctxMood in setOf(Mood.ASLEEP, Mood.AWAKE) -> Mood.HEADPHONES
            st.vibe == "late" && ctxMood in setOf(Mood.ASLEEP, Mood.AWAKE) -> Mood.SLEEPY
            else -> ctxMood
        }
        val ideaBubble = st.pattern != null && st.proposal == null && !st.thinking && (st.bubble == null || st.bubbleIsIdea)
        if (ideaBubble) {
            val hour = (st.signals?.minuteOfDay ?: 0) / 60
            st.copy(mood = mood, bubble = if (hour in 11..14) "I have an idea for lunch" else "I have an idea for this place", bubbleIsIdea = true)
        } else if (st.pattern == null && st.bubbleIsIdea) st.copy(mood = mood, bubble = null, bubbleIsIdea = false)
        else st.copy(mood = mood)
    }

    /** The companion speaks only on the home screen, briefly, then goes quiet. */
    private fun say(text: String, ms: Long = 4_000) {
        greetingJob?.cancel()
        _ui.update { it.copy(bubble = text, bubbleIsIdea = false, speak = it.speak + 1) }
        greetingJob = viewModelScope.launch {
            delay(ms)
            _ui.update { if (it.bubble == text) it.copy(bubble = null) else it }
            updateMood()
        }
    }

    // ---- Companion ---------------------------------------------------------------------------

    /** Opens the conversation (tap the companion): it greets you, then the search bar talks to it. */
    fun startChat() {
        val st = _ui.value
        if (st.chatting) return
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hi = if (st.context?.id != "home" && st.context != null) whyText(st).ifBlank { "Hey!" }
            else when { h < 5 -> "Still up?"; h < 12 -> "Morning! What's up?"; h < 18 -> "Hey! What's up?"; else -> "Evening! How was today?" }
        _ui.update { it.copy(chatting = true, bubble = null, chat = if (it.chat.isEmpty()) listOf(false to hi) else it.chat, speak = it.speak + 1) }
    }

    fun shaken() {
        _ui.update { it.copy(dizzy = it.dizzy + 1) }
        say(listOf("Whoa… the room is spinning!", "Hey! Easy!", "Wheee… I'm dizzy.").random(), 3_000)
    }

    fun toggleEffects() {
        val off = !_ui.value.effectsOff
        uiPrefs.edit().putBoolean("effects_off", off).apply()
        _ui.update { it.copy(effectsOff = off) }
    }

    fun endChat() = _ui.update { it.copy(chatting = false) }

    fun tapCompanion() {
        _ui.update { it.copy(hop = it.hop + 1) }
        val st = _ui.value
        when {
            st.thinking || st.proposal != null -> Unit
            st.pattern != null -> draftFromPattern()
            st.chatting -> endChat()
            else -> startChat()
        }
    }

    private fun whyText(st: UiState): String {
        val c = st.context ?: return ""
        val t = c.trigger
        return when {
            c.id == "driving" -> "Your car's Bluetooth is connected."
            c.id == "study" -> "You're at the library. Focus is on."
            c.id == "game" -> "Headphones on, evening. Game time."
            c.id == "rain" -> "It's raining, so I kept things cozy."
            t.place != null -> "You're at ${t.place}" + (t.startHour?.let { " around $it:00" } ?: "") + "."
            else -> "This is your ${c.name} context."
        }
    }

    private fun draftFromPattern() {
        val st = _ui.value; val s = st.signals ?: return; val pattern = st.pattern ?: return
        runMuse {
            MuseJobs.draftFromPattern(s.place.label, s.place.address, pattern, timeText(s), st.apps.map { it.label })
                .let { p -> p.copy(context = p.context.copy(trigger = p.context.trigger.copy(place = s.place.label))) }
        }
    }

    fun ask(sentence: String) {
        val st = _ui.value; val s = st.signals ?: return
        if (sentence.isBlank()) return
        if (sentence.trim().contains(Theme.PREFIX)) {
            val code = sentence.trim().substring(sentence.trim().indexOf(Theme.PREFIX)).substringBefore(' ')
            val t = Theme.fromCode(code)
            _ui.update { it.copy(query = "", results = emptyList()) }
            if (t == null) say("That theme code looks broken.") else { saveTheme(t.name); applyTheme(t) }
            return
        }
        val key = sentence.trim().lowercase().trimEnd('.', '?', '!')
        if (key in setOf("me", "about me", "privacy", "permissions", "what you see", "memory")) { _ui.update { it.copy(query = "", results = emptyList()) }; openSenses(true); return }
        if (key in setOf("contexts", "my contexts", "wardrobe", "my wardrobe")) {
            _ui.update { it.copy(query = "", results = emptyList(), showContexts = "contexts" in key, showWardrobe = "wardrobe" in key) }
            return
        }
        // "open spotify" (or just an app's name) opens it right away: no model round trip.
        val verb = Regex("^(open|launch|start|ouvre|ouvrir|lance|lancer)\\s+(.+)$").find(key)?.groupValues?.get(2)
        val app = if (verb != null) phone.findApp(verb, st.apps)?.takeIf { verb.split(' ').size <= 3 }
            else st.apps.firstOrNull { it.label.equals(key, true) }
        if (app != null) { launch(app); return }
        _ui.update { it.copy(query = "", results = emptyList(), proposal = null, plan = null) }
        runAgent(sentence)
    }

    // ---- Agent: Muse Spark with tool calling ------------------------------------------------

    private val host = object : AgentHost {
        override fun now(): org.json.JSONObject {
            val st = _ui.value; val s = st.signals
            return org.json.JSONObject().put("time", st.time).put("day", s?.dayLabel).put("weekday", s?.weekday)
                .put("place", s?.place?.label).put("car_bluetooth", s?.carBluetooth).put("headphones", s?.headphones)
                .put("raining", s?.raining).put("active_context", st.context?.name)
                .put("steps_today", s?.steps).put("battery_percent", s?.battery).put("charging", s?.charging)
                .put("next_event", st.nextEvent?.let { e -> e.title + " at " + SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(e.start)) + (if (e.place.isNotBlank()) ", " + e.place else "") })
                .put("favourite_contacts", org.json.JSONArray(if (on("contacts")) favourites().map { f -> f.name.substringBefore(' ') } else emptyList<String>()))
                .put("wearing", org.json.JSONArray(store.wardrobe()))
                .put("weather", st.weather?.summary ?: "unknown").put("rain_soon", st.weather?.rainSoon)
        }
        override fun contexts() = org.json.JSONArray(store.userContexts().map {
            org.json.JSONObject().put("id", it.id).put("name", it.name).put("when", Agent.describe(it))
                .put("rows", org.json.JSONArray(it.items.map { r -> r.label })).put("hide", org.json.JSONArray(it.hide)).put("outfit", it.outfit)
        })
        override fun apps(): org.json.JSONArray {
            val cats = store.appCategories()
            return org.json.JSONArray(_ui.value.apps.map { org.json.JSONObject().put("name", it.label).put("category", cats[it.label] ?: "other") })
        }
        override fun memory() = listOfNotNull(uiPrefs.getString("about_me", "")?.takeIf { it.isNotBlank() }?.let { "In their own words: $it" }) + store.memory()
        override fun callContact(name: String): String {
            if (!on("contacts")) return "{\"error\":\"contacts are off; the user can turn them on in What I can see\"}"
            val f = senses.find(name) ?: return "{\"error\":\"no contact matches '$name'\"}"
            phone.run(NowItem("Call ${f.name}", "", "call", f.number), _ui.value.apps)
            return "dialer opened for ${f.name.substringBefore(' ')}; the user taps call"
        }
        override fun context(id: String) = store.userContexts().firstOrNull { it.id == id }
        override fun remember(fact: String, replaces: String) = store.remember(fact, replaces).also { refreshSenses() }
        override fun forget(fact: String) = if (store.forget(fact)) "forgotten".also { refreshSenses() } else "{\"error\":\"no such fact; use the exact wording from memory\"}"
        override fun addReminder(title: String, startMillis: Long): String {
            val i = android.content.Intent(android.content.Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
                .putExtra(android.provider.CalendarContract.Events.TITLE, title)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 30 * 60_000L)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { getApplication<Application>().startActivity(i); "calendar opened with the event filled in; the user saves it" }
                .getOrElse { "{\"error\":\"no calendar app\"}" }
        }
        override fun openApp(name: String): String {
            phone.findApp(name, _ui.value.apps)?.takeIf { hiddenNow(it.label) }?.let { app ->
                viewModelScope.launch { _ui.update { it.copy(guard = app, guardSeconds = null) } }
                return "hidden during focus: the user sees a 10-second wait before it opens"
            }
            viewModelScope.launch { phone.openApp(name, _ui.value.apps) }
            return if (phone.findApp(name, _ui.value.apps) != null) "opened" else "not installed; opened the store"
        }
        override fun wear(accessory: String): String {
            val ready = Outfits.has(getApplication<Application>(), cleanAccessory(accessory))
            this@MomentViewModel.wear(accessory, "Trying on $accessory…")
            return if (ready) "worn now" else "drawing it now (takes ~15 s)"
        }
        override fun undress(): String { viewModelScope.launch { this@MomentViewModel.undress() }; return "done" }
        override fun share(): String { sharePostcard(); return "painting a postcard and opening the share sheet" }
        override fun paintBackground(description: String): String {
            val c = _ui.value.context ?: return "no context"
            paintBackdrop(c, description); return "painting it now (takes ~15 s)"
        }
        override fun progress(text: String) { viewModelScope.launch { say("$text…", 30_000) } }
    }

    private fun runAgent(request: String) {
        val history = _ui.value.chat
        _ui.update { it.copy(thinking = true, bubble = null, bubbleIsIdea = false, chatting = true, chat = (it.chat + (true to request)).takeLast(20)) }
        updateMood()
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            runCatching { Agent.run(request, host, history) }
                .onSuccess { plan ->
                    _ui.update { it.copy(thinking = false, museStatus = "Muse Spark agent · ${plan.trace.size} steps · ${System.currentTimeMillis() - started} ms") }
                    if (plan.steps.isEmpty()) _ui.update { it.copy(chat = (it.chat + (false to plan.reply)).takeLast(20), speak = it.speak + 1) }
                    else {
                        _ui.update { it.copy(plan = plan, bubble = null, chatting = false, chat = (it.chat + (false to plan.reply)).takeLast(20)) }
                        plan.steps.forEach { st ->
                            when (st) { is PlanStep.CreateContext -> prefetch(st.context.outfit); is PlanStep.UpdateContext -> prefetch(st.context.outfit); else -> {} }
                        }
                    }
                }
                .onFailure { e ->
                    Log.e("Moment", "agent failed, falling back", e)
                    _ui.update { it.copy(thinking = false) }
                    val st = _ui.value; val s = st.signals ?: return@onFailure
                    runMuse { MuseJobs.fromSentence(request, s.place.label, timeText(s), st.apps.map { it.label }) }
                }
            updateMood()
        }
    }

    fun acceptPlan() {
        val plan = _ui.value.plan ?: return
        var dressFor: MomentContext? = null
        plan.steps.forEach { st ->
            when (st) {
                is PlanStep.CreateContext -> { store.saveContext(st.context); if (st.context.outfit.isNotBlank() && dressFor == null) dressFor = st.context }
                is PlanStep.DeleteContext -> store.deleteContext(st.id)
                is PlanStep.UpdateContext -> {
                    val before = store.userContexts().firstOrNull { it.id == st.context.id }
                    if (before?.items != st.context.items) store.clearItems(st.context.id) // the agent's rows win over old hand edits
                    store.saveContext(st.context)
                    if (st.context.outfit.isNotBlank() && st.context.outfit != before?.outfit && dressFor == null) dressFor = st.context
                }
            }
        }
        _ui.update { it.copy(plan = null) }
        lastContextId = null
        recompute()
        say("Done!")
        dressFor?.let { dress(it) }
    }

    fun dismissPlan() { _ui.update { it.copy(plan = null) }; updateMood(); say("No problem.") }

    private fun runMuse(job: suspend () -> Proposal) {
        _ui.update { it.copy(thinking = true, bubble = null, bubbleIsIdea = false) }
        updateMood()
        viewModelScope.launch {
            val result = runCatching { job() }
            _ui.update { it.copy(thinking = false, museStatus = "Muse Spark · ${MuseClient.lastLatencyMs} ms") }
            result.onSuccess { p ->
                if (p.source == "open_app") {
                    _ui.update { it.copy(proposal = null) }
                    say(p.question.ifBlank { "Opening ${p.explanation}" }, 3_000)
                    phone.openApp(p.explanation, _ui.value.apps)
                } else if (p.source == "dress") {
                    _ui.update { it.copy(proposal = null) }
                    wear(p.explanation, p.question)
                } else if (p.source == "undress") {
                    _ui.update { it.copy(proposal = null) }
                    undress()
                } else if (p.source == "answer") { _ui.update { it.copy(proposal = null) }; say(p.question, 8_000) }
                else { _ui.update { it.copy(proposal = p, editing = false) }; prefetch(p.context.outfit) }
            }.onFailure { e ->
                Log.e("Moment", "Muse failed", e)
                // Rules-only fallback: never leave the user with nothing.
                val s = _ui.value.signals
                if (_ui.value.pattern != null && s != null) _ui.update { it.copy(proposal = fallbackProposal(s)) }
                else say("No signal. Try again?")
                _ui.update { it.copy(museStatus = "Muse error: ${e.message?.take(60)}") }
            }
            updateMood()
        }
    }

    private fun fallbackProposal(s: Signals) = Proposal(
        context = MomentContext(
            id = "ctx_" + System.currentTimeMillis(), name = s.place.label, chip = s.place.label,
            trigger = Trigger(place = s.place.label, startHour = s.minuteOfDay / 60 - 1, endHour = s.minuteOfDay / 60 + 2, weekdaysOnly = s.weekday),
            items = listOfNotNull(s.place.address?.let { NowItem("Directions to ${s.place.label}", "Walking route in Maps", "navigate", it) }, NowItem("Messages")),
        ),
        question = "You come here often around this time. Make it a context?",
        explanation = "Shows directions and messages here.", source = "pattern",
    )

    fun acceptProposal() {
        val p = _ui.value.proposal ?: return
        store.saveContext(p.context)
        dismissStreak = 0
        _ui.update { it.copy(proposal = null, editing = false) }
        lastContextId = null // force a greeting for the new context
        recompute()
        if (_ui.value.context?.id != p.context.id) say("Saved.")
        if (p.context.outfit.isNotBlank()) dress(p.context)
    }

    // ---- Weather (Muse Spark + web_search grounding) and postcards -------------------------

    private var rainWarned = false

    fun refreshWeather() = viewModelScope.launch {
        if (!MuseClient.hasKey) return@launch
        runCatching { Weather.now(cityName()) }
            .onSuccess { w ->
                _ui.update { it.copy(weather = w) }
                recompute()
                if (w.rainSoon && !w.rainingNow && !rainWarned) { rainWarned = true; say("Rain soon. Umbrella?", 6_000) }
            }
            .onFailure { Log.e("Moment", "weather failed", it) }
    }

    fun sharePostcard() = viewModelScope.launch {
        val c = _ui.value.context
        val scene = when {
            c == null || c.id == "home" -> "a cozy corner of a Paris apartment"
            else -> "${c.name.lowercase()} (${c.chip.lowercase()}), in Paris"
        }
        _ui.update { it.copy(dressing = true) }; updateMood()
        say("Painting us…", 30_000)
        val appCtx = getApplication<Application>()
        val worn = wornIn(c)
        runCatching { Postcard.make(appCtx, Outfits.composite(appCtx, worn), scene, worn) }
            .onSuccess { f ->
                _ui.update { it.copy(dressing = false, hop = it.hop + 1) }; updateMood()
                say("Here you go!", 3_000)
                Postcard.share(appCtx, f, "My Moment companion today 🍑 Built with Meta Muse Spark + Muse Image. #MetaHackathon")
            }
            .onFailure { e -> Log.e("Moment", "postcard failed", e); _ui.update { it.copy(dressing = false) }; updateMood(); say("The paint didn't dry. Try again?") }
    }

    // ---- Timetable hooks (flow lives in ai/Timetable.kt, UI in ui/TimetableSheet.kt) ---------------

    fun setThinking(on: Boolean) { _ui.update { it.copy(thinking = on) }; updateMood() }

    fun addContexts(list: List<MomentContext>, message: String) {
        list.forEach { store.saveContext(it) }
        lastContextId = null
        recompute()
        say(message, 5_000)
    }

    // ---- Voice: push-to-talk with Muse Voice Transcribe ---------------------------------------

    private var voice: Voice? = null

    fun startListening() {
        if (_ui.value.listening) return
        val v = Voice().also { voice = it }
        _ui.update { it.copy(listening = true, voiceLevel = 0f) }
        updateMood()
        say("I'm listening…", 60_000)
        viewModelScope.launch {
            runCatching { v.record { lvl -> _ui.update { it.copy(voiceLevel = it.voiceLevel * 0.6f + lvl * 0.4f) } } }
                .onFailure { Log.e("Moment", "record failed", it) }
        }
    }

    fun stopListening() {
        val v = voice ?: return
        voice = null
        v.stop()
        _ui.update { it.copy(listening = false, voiceLevel = 0f) }
        updateMood()
        viewModelScope.launch {
            delay(150)
            if (v.seconds() < 0.4f) { say("Hold the mic while you talk."); return@launch }
            _ui.update { it.copy(thinking = true) }; updateMood()
            val keywords = _ui.value.apps.map { it.label } + listOf("Izly", "Moment", "17 bd Jourdan", "beret", "ukulele")
            runCatching { Voice.transcribe(v.wav(), keywords) }
                .onSuccess { text ->
                    _ui.update { it.copy(thinking = false) }
                    if (text.isBlank()) say("I didn't catch that.") else { say("\u201C$text\u201D", 2_000); ask(text) }
                }
                .onFailure { e -> Log.e("Moment", "voice failed", e); _ui.update { it.copy(thinking = false) }; say("I couldn't hear that. Try again?") }
            updateMood()
        }
    }

    // ---- Wardrobe: one Muse Image layer per accessory, stacked on the body ----------------------

    private fun layerImage(item: String): androidx.compose.ui.graphics.ImageBitmap? =
        layerCache[Outfits.slug(item)] ?: Outfits.load(getApplication<Application>(), item)?.asImageBitmap()?.also { layerCache[Outfits.slug(item)] = it }

    /** What the companion wears in [c]: the global wardrobe plus that context's own outfit. */
    private fun wornIn(c: MomentContext?): List<String> {
        val own = c?.takeIf { !it.builtIn }?.outfit?.takeIf { it.isNotBlank() }
        return (store.wardrobe() + listOfNotNull(own)).distinctBy { Outfits.slug(it) }
    }

    /** Draws a layer in the background, silently, so it is ready by the time it is needed. */
    fun prefetch(raw: String) {
        val item = cleanAccessory(raw)
        val slug = Outfits.slug(item)
        if (item.isBlank() || !MuseClient.hasKey || Outfits.has(getApplication<Application>(), item)) return
        if (System.currentTimeMillis() - (drawFailedAt[slug] ?: 0L) < 10 * 60_000L || !drawing.add(slug)) return
        viewModelScope.launch {
            runCatching { Outfits.generate(getApplication<Application>(), item) }
                .onSuccess { layerCache[Outfits.slug(item)] = it.asImageBitmap(); recompute() }
                .onFailure { Log.e("Moment", "layer failed: $item", it); drawFailedAt[slug] = System.currentTimeMillis() }
            drawing.remove(Outfits.slug(item))
        }
    }

    /** A new context gets its own look: instant when its layer was prefetched while the card was up. */
    private fun dress(c: MomentContext) = viewModelScope.launch {
        if (layerImage(c.outfit) != null) { showOff("Ta-da! Dressed for ${c.name}."); return@launch }
        delay(1_500)
        draw(c.outfit, "One sec, getting ready: ${c.outfit}.", "Ta-da! Dressed for ${c.name}.")
    }

    /** "Put on a cowboy hat" / "play the guitar": stacks onto whatever the companion wears now. */
    fun wear(accessory: String, reply: String = "") = viewModelScope.launch {
        val item = cleanAccessory(accessory)
        if (item.isBlank()) return@launch
        if (store.wardrobe().none { Outfits.slug(it) == Outfits.slug(item) }) store.addToWardrobe(item)
        if (layerImage(item) != null) { recompute(); showOff("How do I look?") }
        else draw(item, reply.ifBlank { "Ooh! Trying on $item." }, "How do I look?")
    }

    private fun showOff(line: String) {
        showOffUntil = System.currentTimeMillis() + 9_000
        _ui.update { it.copy(hop = it.hop + 1) }
        recompute(); say(line, 6_000)
        viewModelScope.launch { delay(9_100); updateMood() }
    }

    /** Asks Muse Image for a layer we have never drawn, with the companion narrating. */
    private suspend fun draw(item: String, intro: String, done: String) {
        val app = getApplication<Application>()
        val slug = Outfits.slug(item)
        _ui.update { it.copy(dressing = true) }
        updateMood()
        say(intro, 25_000)
        if (!drawing.add(slug)) { // already being drawn (prefetch): wait for it
            while (slug in drawing) delay(300)
            _ui.update { it.copy(dressing = false) }
            if (layerImage(item) != null) showOff(done) else { updateMood(); say("That didn't fit this time. Try again?") }
            return
        }
        runCatching { Outfits.generate(app, item) }
            .onSuccess { bmp ->
                layerCache[slug] = bmp.asImageBitmap()
                _ui.update { it.copy(dressing = false) }
                showOff(done)
            }
            .onFailure { e ->
                Log.e("Moment", "outfit failed", e)
                drawFailedAt[slug] = System.currentTimeMillis()
                store.removeFromWardrobe(item)
                _ui.update { it.copy(dressing = false) }
                updateMood()
                say("That didn't fit this time. Try again?")
            }
        drawing.remove(slug)
    }

    fun openWardrobe(open: Boolean) = _ui.update { it.copy(showWardrobe = open, showContexts = false) }
    fun openContexts(open: Boolean) = _ui.update { it.copy(showContexts = open, showWardrobe = false) }
    fun layersFor(c: MomentContext) = wornIn(c).mapNotNull { shownLayer(it) }

    /** Takes one accessory off: its layer just stops being drawn (and stays cached for next time). */
    fun removeAccessory(item: String) {
        store.removeFromWardrobe(item)
        _ui.value.context?.takeIf { !it.builtIn && Outfits.slug(it.outfit) == Outfits.slug(item) }
            ?.let { store.saveContext(it.copy(outfit = "")) }
        _ui.update { it.copy(hop = it.hop + 1) }
        recompute()
        say("Better?", 4_000)
    }

    fun undress() {
        store.clearWardrobe()
        _ui.value.context?.takeIf { !it.builtIn && it.outfit.isNotBlank() }?.let { store.saveContext(it.copy(outfit = "")) }
        _ui.update { it.copy(hop = it.hop + 1) }
        recompute()
        say("Back to basics!")
    }

    fun dismissProposal() {
        val p = _ui.value.proposal ?: return
        if (p.source == "pattern") p.context.trigger.place?.let { store.dismissPlace(it) }
        dismissStreak++
        _ui.update { it.copy(proposal = null, editing = false) }
        recompute()
        if (dismissStreak >= 2) say("Okay, quiet.")
    }

    fun toggleEdit() = _ui.update { it.copy(editing = !it.editing) }

    fun editProposal(name: String? = null, removeItem: Int? = null) = _ui.update { st ->
        val p = st.proposal ?: return@update st
        var c = p.context
        if (name != null) c = c.copy(name = name, chip = if (c.chip.contains("·")) name + " ·" + c.chip.substringAfter("·") else name)
        if (removeItem != null) c = c.copy(items = c.items.filterIndexed { i, _ -> i != removeItem })
        st.copy(proposal = p.copy(context = c))
    }

    // ---- Now row, search, focus ---------------------------------------------------------------

    fun runItem(item: NowItem) {
        _ui.value.context?.let { store.countTap(it.id, item.label) }
        phone.run(item, _ui.value.apps)
    }

    fun setQuery(q: String) { _ui.update { it.copy(query = q) }; search(q) }

    private fun search(q: String) {
        val st = _ui.value
        // Hidden apps stay findable: opening one goes through the focus guard instead.
        val needle = q.trim().lowercase()
        val res = if (needle.isEmpty()) emptyList() else st.apps
            .mapNotNull { a -> matchScore(a.label.lowercase(), needle)?.let { a to it + launches(a.label).coerceAtMost(500) } }
            .sortedByDescending { it.second }.map { it.first }
            .take(8)
        _ui.update { it.copy(results = res) }
    }

    /**
     * How well an app name matches what was typed: exact, then prefix, then a word starting with
     * it ("maps" → Google Maps), then anywhere, then initials or letters in order ("gm" → Google
     * Maps). Null when it doesn't match at all. Apps you open often get a nudge on top.
     */
    private fun matchScore(label: String, q: String): Int? {
        if (label == q) return 5000
        if (label.startsWith(q)) return 4000 - label.length
        val words = label.split(' ', '-', '.', '_').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 3000 - label.length
        if (label.contains(q)) return 2000 - label.length
        if (q.length >= 2 && words.joinToString("") { it.take(1) }.startsWith(q)) return 1500
        var i = 0
        for (ch in label) if (i < q.length && ch == q[i]) i++
        return if (q.length >= 3 && i == q.length) 1000 - label.length else null
    }

    private fun launches(label: String) = uiPrefs.getInt("launch_$label", 0)

    private fun hiddenNow(label: String) = !_ui.value.focusUnlocked && _ui.value.hiddenApps.any { it.equals(label, true) }

    fun launch(app: InstalledApp) {
        _ui.update { it.copy(query = "", results = emptyList()) }
        uiPrefs.edit().putInt("launch_${app.label}", launches(app.label) + 1).apply()
        if (hiddenNow(app.label)) { _ui.update { it.copy(guard = app, guardSeconds = null) }; return }
        phone.launch(app)
    }

    private var guardJob: kotlinx.coroutines.Job? = null

    /** "Open in 10 s": a deliberate wait, then it opens (and distractions are back until you leave). */
    fun guardOpen() {
        val app = _ui.value.guard ?: return
        guardJob?.cancel()
        guardJob = viewModelScope.launch {
            for (sec in 10 downTo 1) { _ui.update { it.copy(guardSeconds = sec) }; delay(1_000) }
            _ui.update { it.copy(guard = null, guardSeconds = null, focusUnlocked = true) }
            phone.launch(app)
        }
    }

    fun guardCancel() {
        guardJob?.cancel()
        _ui.update { it.copy(guard = null, guardSeconds = null, hop = it.hop + 1) }
        say("Proud of you.", 2_500)
    }

    /** Distractions stay one deliberate step away: a 10-second wait, then they're back. */
    fun openAnyway() {
        if (_ui.value.openAnywaySeconds != null) return
        viewModelScope.launch {
            for (sec in 10 downTo 1) { _ui.update { it.copy(openAnywaySeconds = sec) }; delay(1_000) }
            _ui.update { it.copy(openAnywaySeconds = null, focusUnlocked = true) }
            say("Back in search.")
        }
    }

    // ---- Apps -------------------------------------------------------------------------------

    private var appsLoadedAt = 0L

    /**
     * Reads the installed apps off the main thread. Called on every resume, so it is throttled:
     * coming back home must draw the first frame right away, not wait for the package manager.
     */
    fun refreshApps(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - appsLoadedAt < 60_000L && _ui.value.apps.isNotEmpty()) return
        appsLoadedAt = now
        viewModelScope.launch {
            val apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { phone.installedApps() }
            if (apps != _ui.value.apps) _ui.update { it.copy(apps = apps) }
        }
    }

    // ---- Smart notifications: held during focus, summed up after ---------------------------------

    private var wasHolding = false

    private fun holdNotifications(on: Boolean, hidden: List<String>, apps: List<app.moment.launcher.system.InstalledApp>) {
        val hub = app.moment.launcher.system.NotifyHub
        hub.heldPackages = if (on) apps.filter { a -> hidden.any { it.equals(a.label, true) } }.map { it.packageName }.toSet() else emptySet()
        hub.holding = on
        if (wasHolding && !on) {
            val lines = hub.drain { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: pkg }
            if (lines.isNotEmpty()) _ui.update { it.copy(brief = Brief("While you focused", "I held these back. All still in the apps.", lines, false)) }
        }
        wasHolding = on
    }

    fun setHoldNotifications(want: Boolean) {
        val appCtx = getApplication<Application>()
        if (want && !app.moment.launcher.system.NotifyHub.enabled(appCtx)) {
            say("Allow \"Moment\" in notification access, then switch this on again.", 6_000)
            runCatching { appCtx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        uiPrefs.edit().putBoolean("hold_notifications", want).apply()
        _ui.update { it.copy(holdNotifications = want) }; recompute()
    }

    // ---- Briefings ----------------------------------------------------------------------------

    /**
     * Morning (5–11 h): today at a glance. Evening (19–23 h): how today went and tomorrow's first
     * thing. Once each per day, only on the live clock, and never over a card the user is reading.
     */
    private fun maybeBrief(s: Signals, c: Calendar) {
        val st = _ui.value
        if (st.brief != null || st.proposal != null || st.plan != null || st.showOnboarding || st.demo.touched) return
        val hour = s.minuteOfDay / 60
        val evening = when (hour) { in 5..10 -> false; in 19..22 -> true; else -> return }
        val key = "brief_${if (evening) "pm" else "am"}"
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(c.time)
        if (uiPrefs.getString(key, "") == today) return
        uiPrefs.edit().putString(key, today).apply()
        _ui.update { it.copy(brief = buildBrief(s, c, evening)) }
        updateMood()
    }

    fun briefNow(evening: Boolean) { val s = _ui.value.signals ?: return; _ui.update { it.copy(brief = buildBrief(s, now(), evening)) } }
    fun dismissBrief() { _ui.update { it.copy(brief = null) }; updateMood() }

    private fun buildBrief(s: Signals, c: Calendar, evening: Boolean): Brief {
        val fmt = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        val day = Calendar.getInstance().apply { timeInMillis = c.timeInMillis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        val dayStart = day.timeInMillis; val dayMs = 86_400_000L
        val calOn = on("calendar")
        val facts = mutableListOf<String>()
        _ui.value.weather?.let { w -> facts += (if (w.summary.contains("°")) w.summary else "${w.summary}, ${w.tempC}°") + if (w.rainSoon && !w.rainingNow) " · rain later" else "" }
        if (!evening) {
            val events = if (calOn) senses.events(c.timeInMillis, dayStart + dayMs) else emptyList()
            if (events.isEmpty()) facts += if (calOn) "Nothing in your calendar today" else "Calendar is off (What I can see)"
            else {
                events.take(3).forEach { e -> facts += "${fmt.format(Date(e.start))} · ${e.title}" }
                if (events.size > 3) facts += "+${events.size - 3} more"
            }
        } else {
            s.steps?.let { facts += "%,d steps today".format(it) }
            val done = if (calOn) senses.events(dayStart, c.timeInMillis).size else 0
            if (calOn && done > 0) facts += "$done event${if (done > 1) "s" else ""} behind you"
            val tomorrow = if (calOn) senses.events(dayStart + dayMs, dayStart + 2 * dayMs).firstOrNull() else null
            facts += tomorrow?.let { "Tomorrow: ${fmt.format(Date(it.start))} · ${it.title}" } ?: "Tomorrow is open"
        }
        s.battery?.takeIf { it < 30 && !s.charging }?.let { facts += "Battery $it%, maybe charge" }
        val line = when {
            evening && (s.steps ?: 0) >= 10_000 -> "Big day. You earned the couch."
            evening -> "Wind down soon. I'll keep watch."
            s.raining -> "Grab a coat, it's wet out."
            facts.any { it.contains("Nothing in your calendar") } -> "A free day. Want me to set one up?"
            else -> "Here's your day."
        }
        return Brief(if (evening) "Good evening" else "Good morning", line, facts, evening)
    }

    // ---- Home gestures ------------------------------------------------------------------------

    /** Swipe down: the notification shade, like on any launcher. */
    fun expandNotifications() {
        runCatching {
            val sb = getApplication<Application>().getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        }.onFailure { Log.e("Moment", "shade", it) }
    }

    fun focusSearch() = _ui.update { it.copy(searchFocus = it.searchFocus + 1) }

    /** Double-tap: lock. Needs the small accessibility service on, which the user enables once. */
    fun lockScreen() {
        if (app.moment.launcher.system.LockService.lock()) return
        say("Turn on \"Moment: double-tap to lock\" in Accessibility, then double-tap again.", 6_000)
        runCatching {
            getApplication<Application>().startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---- Widgets ------------------------------------------------------------------------------

    fun openWidgetPicker(open: Boolean) = _ui.update { it.copy(widgetPicker = open, backdropMenu = false) }
    fun widgetAdded(id: Int) { widgets.add(id); _ui.update { it.copy(widgetIds = widgets.ids(), widgetPicker = false) }; say("Added.", 2_000) }
    fun removeWidget(id: Int) { widgets.remove(id); _ui.update { it.copy(widgetIds = widgets.ids(), backdropMenu = false) } }
    fun refreshWidgets() = _ui.update { it.copy(widgetIds = widgets.ids()) }

    /** Long-press on a widget: resize handles appear around it (tap outside or Done to finish). */
    fun editWidget(id: Int?) = _ui.update { it.copy(editingWidget = id) }
    /** Pins a widget to the current context, or back to every context. */
    fun toggleWidgetContext(id: Int) {
        val here = _ui.value.context?.id ?: return
        widgets.setContext(id, if (widgets.contextOf(id) == here) "" else here)
        _ui.update { it.copy(widgetSizes = it.widgetSizes + 1) }
    }

    fun resizeWidget(id: Int, heightDp: Int, quarters: Int, rowWidthDp: Int) {
        widgets.setSize(id, heightDp, quarters, rowWidthDp); _ui.update { it.copy(widgetSizes = it.widgetSizes + 1) }
    }

    /** Demo panel: show or hide the built-in sample contexts. */
    fun openStyle(open: Boolean) = _ui.update { it.copy(showStyle = open, backdropMenu = false) }
    fun setStyle(style: Style) {
        Style.current = style
        if (!editThemeOfContext { it.copy(style = style.encode()) }) uiPrefs.edit().putString("style", style.encode()).apply()
        recompute()
    }

    /** In a context that wears a theme, look changes edit that theme (else they'd snap back). */
    private fun editThemeOfContext(change: (Theme) -> Theme): Boolean {
        val st = _ui.value
        val name = st.context?.id?.let { st.contextThemes[it] } ?: return false
        val list = st.themes.map { if (it.name == name) change(it) else it }
        uiPrefs.edit().putString("themes", Theme.save(list)).apply()
        _ui.update { it.copy(themes = list) }
        return true
    }

    // Accessory colours: a hue per layer, applied on the phone.
    private val tintedCache = mutableMapOf<Pair<String, Int>, androidx.compose.ui.graphics.ImageBitmap>()
    private fun tints(): Map<String, Int> = org.json.JSONObject(uiPrefs.getString("tints", "{}") ?: "{}").let { o -> o.keys().asSequence().associateWith { o.optInt(it) } }
    fun tintOf(item: String) = tints()[Outfits.slug(item)] ?: 0
    fun setTint(item: String, tint: Int) {
        val m = tints().toMutableMap().also { it[Outfits.slug(item)] = tint }
        uiPrefs.edit().putString("tints", org.json.JSONObject(m as Map<*, *>).toString()).apply()
        _ui.update { it.copy(tints = m) }; recompute()
    }
    /** The layer as worn: its own colour applied. */
    private fun shownLayer(item: String): androidx.compose.ui.graphics.ImageBitmap? {
        val base = layerImage(item) ?: return null
        val t = tintOf(item); if (t == 0) return base
        val slug = Outfits.slug(item)
        return tintedCache.getOrPut(slug to t) { Skin.tint(slug.hashCode(), base.asAndroidBitmap(), t).asImageBitmap() }
    }

    // ---- Themes -------------------------------------------------------------------------------

    private fun loadContextThemes(): Map<String, String> =
        org.json.JSONObject(uiPrefs.getString("ctx_themes", "{}") ?: "{}").let { o -> o.keys().asSequence().associateWith { o.optString(it) } }

    /** A context with a theme wears it; any other context wears the user's default look. */
    private fun applyLookFor(ctx: MomentContext) {
        val st = _ui.value
        val theme = st.contextThemes[ctx.id]?.let { n -> st.themes.firstOrNull { it.name == n } }
        val style = theme?.let { Style.decode(it.style) } ?: Style.decode(uiPrefs.getString("style", null))
        val skin = theme?.let { Skin.decode(it.skin) } ?: Skin.decode(uiPrefs.getString("skin", null))
        if (style != Style.current) Style.current = style
        if (skin != Skin.current) Skin.current = skin
    }

    fun saveTheme(name: String) {
        val t = Theme(name.trim().ifBlank { "Theme ${_ui.value.themes.size + 1}" }, Style.current.encode(), Skin.current.encode())
        val list = _ui.value.themes.filter { it.name != t.name } + t
        uiPrefs.edit().putString("themes", Theme.save(list)).apply()
        _ui.update { it.copy(themes = list) }; say("Saved \"${t.name}\".", 2_500)
    }

    /** Makes a theme the default look (and saves it as such). */
    fun applyTheme(t: Theme) {
        uiPrefs.edit().putString("style", t.style).putString("skin", t.skin).apply()
        Style.current = Style.decode(t.style); Skin.current = Skin.decode(t.skin)
        _ui.update { it.copy(hop = it.hop + 1) }; recompute()
    }

    fun deleteTheme(t: Theme) {
        val list = _ui.value.themes - t
        val map = _ui.value.contextThemes.filterValues { it != t.name }
        uiPrefs.edit().putString("themes", Theme.save(list)).putString("ctx_themes", org.json.JSONObject(map as Map<*, *>).toString()).apply()
        _ui.update { it.copy(themes = list, contextThemes = map) }; recompute()
    }

    /** Ties a theme to the current context (or unties it if it already was). */
    fun toggleThemeForContext(t: Theme) {
        val id = _ui.value.context?.id ?: return
        val map = _ui.value.contextThemes.toMutableMap()
        if (map[id] == t.name) map.remove(id) else map[id] = t.name
        uiPrefs.edit().putString("ctx_themes", org.json.JSONObject(map as Map<*, *>).toString()).apply()
        _ui.update { it.copy(contextThemes = map) }; recompute()
    }

    fun shareTheme(t: Theme) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, "My Moment theme \"${t.name}\". Paste this in Moment's search: ${t.code()}")
        runCatching { getApplication<Application>().startActivity(android.content.Intent.createChooser(send, "Share theme").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** The companion's colours (wardrobe). */
    fun setSkin(skin: Skin) {
        Skin.current = skin
        if (!editThemeOfContext { it.copy(skin = skin.encode()) }) uiPrefs.edit().putString("skin", skin.encode()).apply()
        _ui.update { it.copy(hop = it.hop + 1) }
    }

    fun setDemoContexts(on: Boolean) {
        uiPrefs.edit().putBoolean("demo_contexts", on).apply()
        _ui.update { it.copy(demoContexts = on, pinnedId = null) }
        lastContextId = null; recompute()
    }

    fun classifyAppsIfNeeded(force: Boolean = false) {
        if (!MuseClient.hasKey || (!force && store.appCategories().isNotEmpty())) return
        viewModelScope.launch {
            runCatching { MuseJobs.classifyApps(_ui.value.apps.map { it.label }) }
                .onSuccess { store.saveAppCategories(it); recompute(); if (force) say("Sorted ${it.size} apps. ${it.count { e -> e.value == "distracting" }} are distracting.") }
                .onFailure { Log.e("Moment", "classify failed", it) }
        }
    }

    // ---- Demo panel -------------------------------------------------------------------------

    fun openDemo(open: Boolean) = _ui.update { it.copy(demoOpen = open) }

    fun setDemo(f: (DemoState) -> DemoState) {
        val before = _ui.value.demo
        val after = f(before).copy(touched = true)
        _ui.update { it.copy(demo = after) }
        if (after.place != before.place && after.place != Place.HOME) {
            val s = signals()
            store.logVisit(ContextStore.Visit(after.place.label, s.weekday, s.minuteOfDay / 60, System.currentTimeMillis()))
        }
        recompute()
    }

    /** Jumps the clock to a given day/time (demo only). */
    fun setClock(year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        val target = Calendar.getInstance().apply { set(year, month, day, hour, minute, 0) }
        setDemo { it.copy(clockOffsetMs = target.timeInMillis - System.currentTimeMillis()) }
    }

    // ---- First run --------------------------------------------------------------------------

    init { if (!uiPrefs.getBoolean("onboarded", false)) _ui.update { it.copy(showOnboarding = true) } }

    // ---- Backgrounds the companion paints ------------------------------------------------------


    /** Calm looks only: rain, night, game and driving keep their own animated backgrounds. */
    private fun backdropOk(c: MomentContext) = c.look == Look.CREAM || c.look == Look.STUDY

    private fun backdropFor(c: MomentContext): androidx.compose.ui.graphics.ImageBitmap? {
        val custom = uiPrefs.getBoolean("custom_${c.id}", false)
        if (!backdropOk(c) && !custom) return null
        val img = backdropCache.getOrPut(c.id) { Backdrops.load(getApplication<Application>(), c.id)?.asImageBitmap() }
        // Proactive: the first time one of your contexts (or home) is on screen, it paints one, once.
        if (img == null && !painting && MuseClient.hasKey && !_ui.value.showOnboarding && (!c.builtIn || c.id == "home") &&
            !uiPrefs.getBoolean("painted_${c.id}", false)
        ) {
            uiPrefs.edit().putBoolean("painted_${c.id}", true).apply()
            viewModelScope.launch { delay(4_000); if (_ui.value.context?.id == c.id) paintBackdrop(c, null) }
        }
        return img
    }

    fun paintBackdrop(c: MomentContext, wish: String?) {
        if (painting) return
        painting = true
        viewModelScope.launch {
            val subject = wish?.takeIf { it.isNotBlank() } ?: when (c.id) {
                "home" -> "a cozy morning corner of a Paris apartment, a plant on the window sill"
                else -> "${c.name.lowercase()}${if (c.chip.isNotBlank()) " (" + c.chip.lowercase() + ")" else ""}, in Paris"
            }
            say("Painting…", 30_000)
            runCatching { Backdrops.paint(getApplication<Application>(), c.id, subject) }
                .onSuccess { bmp ->
                    backdropCache[c.id] = bmp.asImageBitmap()
                    _ui.update { it.copy(hop = it.hop + 1) }
                    say("There.", 3_000)
                    recompute()
                }
                .onFailure { Log.e("Moment", "backdrop", it); say("My brushes slipped. Try again later?", 4_000) }
            painting = false
        }
    }

    // ---- What I can see: about me, memory, calendar, contacts, approximate location ------------

    private var favCache: Pair<Long, List<Senses.Friend>> = 0L to emptyList()

    /** Cached: recompute runs every 5 s and must not query the contacts database each time. */
    private fun favourites(): List<Senses.Friend> {
        if (System.currentTimeMillis() - favCache.first > 10 * 60_000L)
            favCache = System.currentTimeMillis() to senses.favourites(uiPrefs.getStringSet("fav_contacts", emptySet()).orEmpty())
        return favCache.second
    }

    fun openContactPicker(open: Boolean) {
        _ui.update { it.copy(contactPicker = open) }
        if (open) viewModelScope.launch {
            val names = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { senses.contacts().map { it.name } }
            _ui.update { it.copy(allContacts = names) }
        }
    }

    fun toggleFavourite(name: String) {
        val cur = uiPrefs.getStringSet("fav_contacts", emptySet()).orEmpty()
        val starred = senses.favourites().map { it.name }
        if (name in starred && name !in cur) { say("Starred in Contacts: unstar it there.", 3_000); return }
        uiPrefs.edit().putStringSet("fav_contacts", if (name in cur) cur - name else cur + name).apply()
        favCache = 0L to emptyList(); refreshSenses(); recompute()
    }

    private fun on(sense: String) = uiPrefs.getBoolean("sense_$sense", false) && senses.granted(SENSE_PERMISSION.getValue(sense))

    private fun refreshSenses() = _ui.update {
        it.copy(aboutMe = uiPrefs.getString("about_me", "").orEmpty(), memory = store.memory(),
            sense = SENSE_PERMISSION.keys.associateWith { k -> on(k) }, homeSaved = uiPrefs.contains("home_lat"),
            favourites = if (on("contacts")) favourites().map { f -> f.name } else emptyList())
    }

    fun openSenses(open: Boolean) { refreshSenses(); _ui.update { it.copy(showSenses = open, showContexts = false, showWardrobe = false, demoOpen = false) } }
    fun setAboutMe(text: String) { uiPrefs.edit().putString("about_me", text.take(400)).apply(); _ui.update { it.copy(aboutMe = text.take(400)) } }
    fun forget(fact: String) { store.forget(fact); refreshSenses() }

    /** Called with the permission result: the switch only stays on when Android said yes. */
    fun setSense(sense: String, wanted: Boolean) {
        uiPrefs.edit().putBoolean("sense_$sense", wanted).apply()
        if (sense == "contacts") favCache = 0L to emptyList()
        if (wanted && sense == "location" && !uiPrefs.contains("home_lat")) saveHomeHere()
        refreshSenses(); recompute()
        if (sense == "location") refreshWeather()
    }

    fun saveHomeHere() {
        val l = senses.approxLocation() ?: run { say("Can't find where we are yet.", 3_000); return }
        uiPrefs.edit().putFloat("home_lat", l.latitude.toFloat()).putFloat("home_lon", l.longitude.toFloat()).apply()
        say("Home, saved.", 3_000); refreshSenses(); recompute()
    }

    private fun approx(): android.location.Location? {
        if (System.currentTimeMillis() - lastLoc.first > 60_000) lastLoc = System.currentTimeMillis() to senses.approxLocation()
        return lastLoc.second
    }

    /** With approximate location on: home within ~1 km of where you saved it, otherwise away (at night) or elsewhere. */
    private fun realPlace(c: Calendar): Place? {
        if (_ui.value.demo.touched || !on("location") || !uiPrefs.contains("home_lat")) return null
        val l = approx() ?: return null
        val d = FloatArray(1)
        android.location.Location.distanceBetween(l.latitude, l.longitude, uiPrefs.getFloat("home_lat", 0f).toDouble(), uiPrefs.getFloat("home_lon", 0f).toDouble(), d)
        val h = c.get(Calendar.HOUR_OF_DAY)
        return when { d[0] < 1_000 -> Place.HOME; h >= 21 || h < 5 -> Place.AWAY; else -> Place.ELSEWHERE }
    }

    private fun cityName(): String = if (on("location")) approx()?.let { senses.city(it) } ?: CITY else CITY

    fun openEvent() { _ui.value.nextEvent?.let { phone.run(eventRow(it), _ui.value.apps) } }

    private fun eventRow(e: Senses.Event): NowItem {
        val t = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(e.start))
        return NowItem(e.title, listOf(t, e.place).filter { it.isNotBlank() }.joinToString(" · "), "calendar", e.id.toString())
    }

    /** Ten minutes before an event, the companion says so, once. */
    private fun eventNudge(e: Senses.Event?) {
        e ?: return
        val mins = (e.start - System.currentTimeMillis()) / 60_000
        if (mins in 0..10 && !uiPrefs.getBoolean("nudged_${e.id}_${e.start}", false)) {
            uiPrefs.edit().putBoolean("nudged_${e.id}_${e.start}", true).apply()
            say("${e.title} in $mins min.", 6_000)
        }
    }

    // ---- Long-press editing: rows and background, no AI needed -------------------------------

    fun editRow(item: NowItem?) = _ui.update { it.copy(rowEdit = item) }

    /** Opens the app picker straight away (an empty label means "add", not "edit"). */
    fun addApp() = _ui.update { it.copy(rowEdit = NowItem(""), backdropMenu = false) }

    private fun editItems(f: (MutableList<NowItem>) -> Unit) {
        val st = _ui.value; val c = st.context ?: return
        val list = st.items.toMutableList(); f(list)
        store.setItems(c.id, list); _ui.update { it.copy(rowEdit = null) }; recompute()
    }

    fun removeRow(item: NowItem) = editItems { it.removeAll { r -> r.label == item.label } }

    fun swapRow(item: NowItem?, app: InstalledApp) = editItems { list ->
        val row = NowItem(app.label, "", "app", app.label)
        val i = list.indexOfFirst { it.label == item?.label }
        if (i >= 0) list[i] = row else if (list.size < 4) list += row
    }

    fun addRow(app: InstalledApp) = editItems { if (it.size < 4 && it.none { r -> r.label == app.label }) it += NowItem(app.label, "", "app", app.label) }

    fun moveUp(item: NowItem) = editItems { list ->
        val i = list.indexOfFirst { it.label == item.label }
        if (i > 0) { val r = list.removeAt(i); list.add(i - 1, r) }
    }

    fun resetRows() { _ui.value.context?.let { store.setItems(it.id, it.items) }; _ui.update { it.copy(rowEdit = null) }; recompute() }

    fun openBackdropMenu(open: Boolean) = _ui.update { it.copy(backdropMenu = open) }

    fun repaint(wish: String?) {
        val c = _ui.value.context ?: return
        uiPrefs.edit().putBoolean("custom_${c.id}", true).apply()
        _ui.update { it.copy(backdropMenu = false) }
        paintBackdrop(c, wish)
    }

    fun useMyPhoto(uri: android.net.Uri) {
        val c = _ui.value.context ?: return
        _ui.update { it.copy(backdropMenu = false) }
        viewModelScope.launch {
            runCatching { Backdrops.fromPhoto(getApplication<Application>(), c.id, uri) }
                .onSuccess { bmp ->
                    uiPrefs.edit().putBoolean("custom_${c.id}", true).putBoolean("painted_${c.id}", true).apply()
                    backdropCache[c.id] = bmp.asImageBitmap(); recompute()
                }
                .onFailure { Log.e("Moment", "photo backdrop", it); say("Couldn't open that photo.", 3_000) }
        }
    }

    fun removeBackdrop() {
        val c = _ui.value.context ?: return
        Backdrops.remove(getApplication<Application>(), c.id)
        uiPrefs.edit().putBoolean("custom_${c.id}", false).putBoolean("painted_${c.id}", true).apply()
        backdropCache.remove(c.id); _ui.update { it.copy(backdropMenu = false) }; recompute()
    }

    fun clearBackdrops() { Backdrops.clear(getApplication<Application>()); backdropCache.clear(); recompute() }

    // ---- Body: steps and battery ---------------------------------------------------------------


    /** The companion notices step milestones and a low battery, once each. */
    private fun bodyMoments(s: Signals) {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now().time)
        val steps = s.steps ?: 0
        val reached = listOf(10_000, 5_000).firstOrNull { steps >= it }
        if (reached != null && uiPrefs.getInt("milestone_$day", 0) < reached) {
            uiPrefs.edit().putInt("milestone_$day", reached).apply()
            _ui.update { it.copy(hop = it.hop + 1) }
            say(if (reached >= 10_000) "10,000 steps!" else "5,000 steps!", 5_000)
        }
        val b = s.battery
        if (b != null && b <= 15 && !s.charging && !lowBatteryWarned) {
            lowBatteryWarned = true
            say("Battery $b%. Charge me?", 6_000)
        } else if (b != null && (b > 20 || s.charging)) lowBatteryWarned = false
    }

    fun replayOnboarding() = _ui.update { it.copy(showOnboarding = true, demoOpen = false) }
    fun backToAutomatic() { _ui.update { it.copy(pinnedId = null) }; recompute(); say("Automatic.", 3_000) }

    fun finishOnboarding() {
        uiPrefs.edit().putBoolean("onboarded", true).apply()
        _ui.update { it.copy(showOnboarding = false) }
        say("Hi!", 6_000)
    }

    fun liveClock() { _ui.update { it.copy(demo = DemoState()) }; recompute() }

    /** Every context the user can switch to by hand: home, their own, and the built-in ones. */
    private fun pickable(s: Signals, user: List<MomentContext>, distracting: List<String>) =
        listOf(ContextEngine.home(s, _ui.value.demoContexts)) + user +
            (if (_ui.value.demoContexts) ContextEngine.demoContexts(distracting) else emptyList()).filter { b -> user.none { it.id == b.id } }

    /** Tapping a context in the list switches to it for an hour; tapping the active one goes back to automatic. */
    fun useContext(id: String) {
        val st = _ui.value
        val back = st.pinnedId == id
        _ui.update { it.copy(pinnedId = if (back) null else id, pinnedUntil = System.currentTimeMillis() + 60 * 60_000L, showContexts = false) }
        recompute()
        val name = _ui.value.context?.name ?: return
        say(if (back) "Automatic." else "$name, for an hour.", 3_000)
    }

    fun seedVisits() {
        repeat(3) { i ->
            store.logVisit(ContextStore.Visit(Place.JOURDAN.label, true, 12, System.currentTimeMillis() - (i + 1) * 86_400_000L))
        }
        say("Seeded 3 weekday lunches at 17 bd Jourdan.")
        recompute()
    }

    /**
     * "Reset everything" really means everything: contexts, memory, visits, wardrobe and layers,
     * colours, fonts, effects, themes, widgets, backgrounds, senses, briefings, onboarding.
     * Only the step counter's own bookkeeping survives (it is sensor calibration, not your data).
     */
    fun resetAll() {
        val ctx = getApplication<Application>()
        widgets.ids().forEach { widgets.remove(it) }
        store.reset()
        uiPrefs.edit().clear().apply()
        ctx.getSharedPreferences("moment_widgets", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        ctx.filesDir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        layerCache.clear(); tintedCache.clear(); backdropCache.clear(); drawing.clear(); drawFailedAt.clear()
        favCache = 0L to emptyList(); wasHolding = false
        app.moment.launcher.system.NotifyHub.held.clear()
        Style.current = Style(); Skin.current = Skin()
        dismissStreak = 0; lastContextId = null; showOffUntil = 0L
        val apps = _ui.value.apps
        _ui.value = UiState(apps = apps, showOnboarding = true, museStatus = _ui.value.museStatus)
        classifyAppsIfNeeded()
        recompute()
    }
}
