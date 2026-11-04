package app.moment.launcher

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.moment.launcher.ai.MuseClient
import app.moment.launcher.ai.MuseJobs
import app.moment.launcher.ai.Outfits
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
    val outfit: androidx.compose.ui.graphics.ImageBitmap? = null,
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
    /** The companion-painted background of the current context, if any. */
    val backdrop: androidx.compose.ui.graphics.ImageBitmap? = null,
    /** A context the user picked by hand in the Contexts list; it wins until [pinnedUntil]. */
    val pinnedId: String? = null,
    val pinnedUntil: Long = 0,
    val wardrobe: List<String> = emptyList(),
    val contexts: List<MomentContext> = emptyList(),
)

private const val WARDROBE = "wardrobe"
private const val CITY = "Paris, France"

class MomentViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ContextStore(app)
    val phone = Phone(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var lastContextId: String? = null
    private var greetingJob: Job? = null
    private var dismissStreak = 0
    private var showOffUntil = 0L
    private val outfitCache = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap?>()

    private val real = RealSignals(app)
    val body = Body(app)
    // Declared before init: recompute() already runs from the init block below.
    private val uiPrefs = app.getSharedPreferences("moment_ui", android.content.Context.MODE_PRIVATE)
    private val backdropCache = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap?>()
    private var painting = false
    private var lowBatteryWarned = false

    init {
        real.start(); body.start()
        viewModelScope.launch { body.steps.collect { recompute() } }
        viewModelScope.launch { real.devices.collect { d -> _ui.update { it.copy(realCar = d.carName) }; recompute() } }
        viewModelScope.launch { while (true) { refreshWeather(); delay(30 * 60_000L) } }
        refreshApps()
        viewModelScope.launch {
            while (true) { recompute(); delay(5_000) }
        }
        classifyAppsIfNeeded()
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
            place = d.place,
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
        val ctx = pinned ?: ContextEngine.resolve(s, user, distracting)
        val hidden = if (ctx.focus) (ctx.hide + distracting).distinct() else emptyList()
        val items = ctx.items.sortedByDescending { store.tapCount(ctx.id, it.label) }
        val pattern = ContextEngine.patternAt(s, store.visits(), user, store.dismissedPlaces())
            .takeIf { dismissStreak < 2 }

        val outfit = (if (ctx.builtIn) null else outfitImage(ctx.id)) ?: outfitImage(WARDROBE)
        bodyMoments(s)
        val backdrop = backdropFor(ctx)
        val changed = ctx.id != lastContextId
        if (changed) {
            lastContextId = ctx.id
            _ui.update { it.copy(focusUnlocked = false, openAnywaySeconds = null) }
            if (ctx.greeting.isNotBlank()) say(ctx.greeting, 5_000)
        }
        _ui.update {
            it.copy(
                time = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)),
                date = SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH).format(c.time) +
                    (_ui.value.weather?.takeIf { _ui.value.demo.clockOffsetMs == 0L }?.let { " · ${it.summary}" } ?: "") +
                    (s.steps?.takeIf { it > 0 }?.let { " · " + "%,d".format(Locale.ENGLISH, it) + " steps" } ?: ""),
                signals = s, context = ctx, items = items, hiddenApps = hidden, pattern = pattern, outfit = outfit, backdrop = backdrop,
                wardrobe = store.wardrobe(), contexts = user,
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
            st.pattern != null -> Mood.AWAKE
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

    fun tapCompanion() {
        _ui.update { it.copy(hop = it.hop + 1) }
        val st = _ui.value
        when {
            st.thinking || st.proposal != null -> Unit
            st.pattern != null -> draftFromPattern()
            st.context?.id == "home" -> say("All calm. Long-press me for my wardrobe, or ask me anything below.")
            else -> say(st.context?.reason?.ifBlank { null } ?: whyText(st))
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
        val key = sentence.trim().lowercase().trimEnd('.', '?', '!')
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
                .put("wearing", org.json.JSONArray(store.wardrobe()))
                .put("weather", st.weather?.summary ?: "unknown").put("rain_soon", st.weather?.rainSoon)
        }
        override fun contexts() = org.json.JSONArray(store.userContexts().map {
            org.json.JSONObject().put("id", it.id).put("name", it.name).put("when", Agent.describe(it))
        })
        override fun apps(): org.json.JSONArray {
            val cats = store.appCategories()
            return org.json.JSONArray(_ui.value.apps.map { org.json.JSONObject().put("name", it.label).put("category", cats[it.label] ?: "other") })
        }
        override fun memory() = store.memory()
        override fun remember(fact: String) = store.remember(fact)
        override fun openApp(name: String): String {
            viewModelScope.launch { phone.openApp(name, _ui.value.apps) }
            return if (phone.findApp(name, _ui.value.apps) != null) "opened" else "not installed; opened the store"
        }
        override fun wear(accessory: String): String { this@MomentViewModel.wear(accessory, "Trying on $accessory…"); return "putting it on now (takes ~15 s)" }
        override fun undress(): String { viewModelScope.launch { this@MomentViewModel.undress() }; return "done" }
        override fun share(): String { sharePostcard(); return "painting a postcard and opening the share sheet" }
        override fun paintBackground(description: String): String {
            val c = _ui.value.context ?: return "no context"
            paintBackdrop(c, description); return "painting it now (takes ~15 s)"
        }
        override fun progress(text: String) { viewModelScope.launch { say("$text…", 30_000) } }
    }

    private fun runAgent(request: String) {
        _ui.update { it.copy(thinking = true, bubble = null, bubbleIsIdea = false) }
        updateMood()
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            runCatching { Agent.run(request, host) }
                .onSuccess { plan ->
                    _ui.update { it.copy(thinking = false, museStatus = "Muse Spark agent · ${plan.trace.size} steps · ${System.currentTimeMillis() - started} ms") }
                    if (plan.steps.isEmpty()) say(plan.reply, 7_000)
                    else { _ui.update { it.copy(plan = plan, bubble = null) } }
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
            }
        }
        _ui.update { it.copy(plan = null) }
        lastContextId = null
        recompute()
        say("Done! ${plan.steps.size} change${if (plan.steps.size > 1) "s" else ""} applied.")
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
                else _ui.update { it.copy(proposal = p, editing = false) }
            }.onFailure { e ->
                Log.e("Moment", "Muse failed", e)
                // Rules-only fallback: never leave the user with nothing.
                val s = _ui.value.signals
                if (_ui.value.pattern != null && s != null) _ui.update { it.copy(proposal = fallbackProposal(s)) }
                else say("I couldn't reach Muse just now. Try again?")
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
        if (_ui.value.context?.id != p.context.id) say("Saved '${p.context.name}'. I'll show it when it fits.")
        if (p.context.outfit.isNotBlank()) dress(p.context)
    }

    // ---- Weather (Muse Spark + web_search grounding) and postcards -------------------------

    private var rainWarned = false

    fun refreshWeather() = viewModelScope.launch {
        if (!MuseClient.hasKey) return@launch
        runCatching { Weather.now(CITY) }
            .onSuccess { w ->
                _ui.update { it.copy(weather = w) }
                recompute()
                if (w.rainSoon && !w.rainingNow && !rainWarned) { rainWarned = true; say("Rain is coming in the next hours. Umbrella?", 6_000) }
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
        say("Hold still… painting a postcard of us.", 30_000)
        val appCtx = getApplication<Application>()
        val outfit = (if (c != null && !c.builtIn) Outfits.load(appCtx, c.id) else null) ?: Outfits.load(appCtx, WARDROBE)
        runCatching { Postcard.make(appCtx, outfit, scene, store.wardrobe()) }
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

    // ---- Wardrobe: Muse Image adds accessories on top of what the companion already wears ----

    private fun outfitImage(key: String) = outfitCache.getOrPut(key) {
        Outfits.load(getApplication(), key)?.asImageBitmap()
    }

    /** A new context gets its own look, built on the current wardrobe. */
    private fun dress(c: MomentContext) = viewModelScope.launch {
        delay(2_500)
        putOn(c.id, c.outfit, "One sec, getting ready: ${c.outfit}.", "Ta-da! Dressed for ${c.name}.")
    }

    /** "Put on a cowboy hat" / "play the guitar": stacks onto whatever the companion wears now. */
    fun wear(accessory: String, reply: String = "") = viewModelScope.launch {
        if (accessory.isBlank()) return@launch
        val ctx = _ui.value.context
        val key = if (ctx != null && !ctx.builtIn && outfitImage(ctx.id) != null) ctx.id else WARDROBE
        putOn(key, accessory, reply.ifBlank { "Ooh! Trying on $accessory." }, "How do I look?")
    }

    private suspend fun putOn(key: String, rawAccessory: String, intro: String, done: String) {
        val accessory = cleanAccessory(rawAccessory)
        val app = getApplication<Application>()
        val base = Outfits.load(app, key) ?: Outfits.load(app, WARDROBE)
        _ui.update { it.copy(dressing = true) }
        updateMood()
        say(intro, 25_000)
        runCatching { Outfits.generate(app, key, accessory, base) }
            .onSuccess { bmp ->
                outfitCache[key] = bmp.asImageBitmap()
                store.addToWardrobe(accessory)
                showOffUntil = System.currentTimeMillis() + 9_000
                _ui.update { it.copy(dressing = false, hop = it.hop + 1) }
                recompute()
                say(done, 6_000)
                viewModelScope.launch { delay(9_100); updateMood() }
            }
            .onFailure { e ->
                Log.e("Moment", "outfit failed", e)
                _ui.update { it.copy(dressing = false) }
                updateMood()
                say("That didn't fit this time. Try again?")
            }
    }

    fun openWardrobe(open: Boolean) = _ui.update { it.copy(showWardrobe = open, showContexts = false) }
    fun openContexts(open: Boolean) = _ui.update { it.copy(showContexts = open, showWardrobe = false) }
    fun outfitFor(contextId: String) = outfitImage(contextId)

    /**
     * Takes one accessory off: Muse Image re-dresses the plain body with what is left, and only a
     * successful result replaces the current look (a failed call changes nothing).
     */
    fun removeAccessory(item: String) {
        val rest = store.wardrobe().toMutableList().also { it.remove(item) }
        if (rest.isEmpty()) { undress(); return }
        viewModelScope.launch {
            val app = getApplication<Application>()
            _ui.update { it.copy(dressing = true) }
            updateMood()
            say("Taking off ${item.removePrefix("a ")}. One sec.", 60_000)
            val tmp = "wardrobe_new"
            runCatching {
                var base: android.graphics.Bitmap? = null
                for (a in rest) base = runCatching { Outfits.generate(app, tmp, a, base) }.getOrElse { Outfits.generate(app, tmp, a, base) }
                base!!
            }.onSuccess {
                app.filesDir.listFiles { f -> f.name.startsWith("outfit_") && f.name != Outfits.file(app, tmp).name }?.forEach { it.delete() }
                Outfits.file(app, tmp).renameTo(Outfits.file(app, WARDROBE))
                outfitCache.clear(); store.clearWardrobe(); rest.forEach { store.addToWardrobe(it) }
                showOffUntil = System.currentTimeMillis() + 6_000
                _ui.update { it.copy(dressing = false, hop = it.hop + 1) }
                recompute()
                say("Better?", 4_000)
            }.onFailure { e ->
                Log.e("Moment", "remove accessory failed", e)
                Outfits.file(app, tmp).delete()
                _ui.update { it.copy(dressing = false) }
                updateMood()
                say("Couldn't change that. Try again?")
            }
        }
    }

    fun undress() {
        val app = getApplication<Application>()
        app.filesDir.listFiles { f -> f.name.startsWith("outfit_") }?.forEach { it.delete() }
        outfitCache.clear(); store.clearWardrobe()
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
        if (dismissStreak >= 2) say("Got it. I'll stay quiet for a while.")
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
        val hidden = if (st.focusUnlocked) emptySet() else st.hiddenApps.map { it.lowercase() }.toSet()
        val res = if (q.isBlank()) emptyList() else st.apps
            .filter { it.label.lowercase() !in hidden }
            .filter { it.label.contains(q.trim(), ignoreCase = true) }
            .sortedBy { !it.label.startsWith(q.trim(), ignoreCase = true) }
            .take(6)
        _ui.update { it.copy(results = res) }
    }

    fun launch(app: InstalledApp) { _ui.update { it.copy(query = "", results = emptyList()) }; phone.launch(app) }

    /** Distractions stay one deliberate step away: a 10-second wait, then they're back. */
    fun openAnyway() {
        if (_ui.value.openAnywaySeconds != null) return
        viewModelScope.launch {
            for (sec in 10 downTo 1) { _ui.update { it.copy(openAnywaySeconds = sec) }; delay(1_000) }
            _ui.update { it.copy(openAnywaySeconds = null, focusUnlocked = true) }
            say("Okay, they're back in search until you leave.")
        }
    }

    // ---- Apps -------------------------------------------------------------------------------

    fun refreshApps() { _ui.update { it.copy(apps = phone.installedApps()) } }

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
        if (!backdropOk(c)) return null
        val img = backdropCache.getOrPut(c.id) { Backdrops.load(getApplication(), c.id)?.asImageBitmap() }
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
            say(if (wish == null) "I'm painting you a background for ${c.name}…" else "Painting it…", 30_000)
            runCatching { Backdrops.paint(getApplication(), c.id, subject) }
                .onSuccess { bmp ->
                    backdropCache[c.id] = bmp.asImageBitmap()
                    _ui.update { it.copy(hop = it.hop + 1) }
                    say("There. A background for ${c.name}.", 5_000)
                    recompute()
                }
                .onFailure { Log.e("Moment", "backdrop", it); say("My brushes slipped. Try again later?", 4_000) }
            painting = false
        }
    }

    fun clearBackdrops() { Backdrops.clear(getApplication()); backdropCache.clear(); recompute() }

    // ---- Body: steps and battery ---------------------------------------------------------------


    /** The companion notices step milestones and a low battery, once each. */
    private fun bodyMoments(s: Signals) {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now().time)
        val steps = s.steps ?: 0
        val reached = listOf(10_000, 5_000).firstOrNull { steps >= it }
        if (reached != null && uiPrefs.getInt("milestone_$day", 0) < reached) {
            uiPrefs.edit().putInt("milestone_$day", reached).apply()
            _ui.update { it.copy(hop = it.hop + 1) }
            say(if (reached >= 10_000) "10,000 steps today. Legend." else "5,000 steps already. Nice walk.", 6_000)
        }
        val b = s.battery
        if (b != null && b <= 15 && !s.charging && !lowBatteryWarned) {
            lowBatteryWarned = true
            say("Battery at $b%. Maybe plug me in soon?", 6_000)
        } else if (b != null && (b > 20 || s.charging)) lowBatteryWarned = false
    }

    fun replayOnboarding() = _ui.update { it.copy(showOnboarding = true, demoOpen = false) }
    fun backToAutomatic() { _ui.update { it.copy(pinnedId = null) }; recompute(); say("Back to automatic.", 3_000) }

    fun finishOnboarding() {
        uiPrefs.edit().putBoolean("onboarded", true).apply()
        _ui.update { it.copy(showOnboarding = false) }
        say("Hi! I'll sleep until I notice something. Ask me anything below.", 6_000)
    }

    fun liveClock() { _ui.update { it.copy(demo = DemoState()) }; recompute() }

    /** Every context the user can switch to by hand: home, their own, and the built-in ones. */
    private fun pickable(s: Signals, user: List<MomentContext>, distracting: List<String>) =
        listOf(ContextEngine.home(s)) + user + listOf(
            ContextEngine.study(distracting), ContextEngine.driving, ContextEngine.game, ContextEngine.rain, ContextEngine.nightOut,
        ).filter { b -> user.none { it.id == b.id } }

    /** Tapping a context in the list switches to it for an hour; tapping the active one goes back to automatic. */
    fun useContext(id: String) {
        val st = _ui.value
        val back = st.pinnedId == id
        _ui.update { it.copy(pinnedId = if (back) null else id, pinnedUntil = System.currentTimeMillis() + 60 * 60_000L, showContexts = false) }
        recompute()
        val name = _ui.value.context?.name ?: return
        say(if (back) "Back to automatic." else "$name for the next hour. Tap it again in the list to go back to automatic.", 5_000)
    }

    fun seedVisits() {
        repeat(3) { i ->
            store.logVisit(ContextStore.Visit(Place.JOURDAN.label, true, 12, System.currentTimeMillis() - (i + 1) * 86_400_000L))
        }
        say("Seeded 3 weekday lunches at 17 bd Jourdan.")
        recompute()
    }

    fun resetAll() {
        _ui.update { it.copy(plan = null) }
        store.reset(); dismissStreak = 0; outfitCache.clear()
        getApplication<Application>().filesDir.listFiles { f -> f.name.startsWith("outfit_") }?.forEach { it.delete() }
        _ui.update { it.copy(proposal = null, bubble = null, demo = DemoState()) }
        classifyAppsIfNeeded()
        recompute()
    }
}
