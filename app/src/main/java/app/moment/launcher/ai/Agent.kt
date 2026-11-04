package app.moment.launcher.ai

import app.moment.launcher.data.MomentContext
import org.json.JSONArray
import org.json.JSONObject

/** A change the agent wants to make to the home screen. Nothing is applied until the user accepts. */
sealed class PlanStep(val label: String) {
    class CreateContext(val context: MomentContext, label: String) : PlanStep(label)
    class DeleteContext(val id: String, label: String) : PlanStep(label)
    class UpdateContext(val context: MomentContext, label: String) : PlanStep(label)
}

data class AgentPlan(val reply: String, val steps: List<PlanStep>, val trace: List<String>)

/** What the agent can see and do on the phone. Implemented by the ViewModel. */
interface AgentHost {
    fun now(): JSONObject
    fun contexts(): JSONArray
    /** A saved context, by id, with everything the agent may change. */
    fun context(id: String): MomentContext?
    fun apps(): JSONArray
    fun memory(): List<String>
    /** Saves a fact; returns what happened (saved / already known / replaced an older one). */
    fun remember(fact: String, replaces: String = ""): String
    fun forget(fact: String): String
    fun addReminder(title: String, startMillis: Long): String
    fun openApp(name: String): String
    fun wear(accessory: String): String
    fun undress(): String
    fun share(): String
    fun paintBackground(description: String): String
    fun progress(text: String)
}

/**
 * The companion as an agent: Muse Spark with real tool calling. It can look at the time, place,
 * contexts and installed apps, then plan several changes at once ("set up my gym days").
 * Read-only and harmless tools run immediately; changes to the home screen are staged into a
 * plan the user approves on one card.
 */
object Agent {
    private const val MAX_TURNS = 8
    private const val HISTORY = 20

    private fun fn(name: String, desc: String, params: JSONObject = JSONObject().put("type", "object").put("properties", JSONObject())) =
        JSONObject().put("type", "function").put("function", JSONObject().put("name", name).put("description", desc).put("parameters", params))

    private fun props(vararg p: Pair<String, JSONObject>, required: List<String> = p.map { it.first }) = JSONObject()
        .put("type", "object").put("properties", JSONObject().apply { p.forEach { put(it.first, it.second) } })
        .put("required", JSONArray(required))

    private fun s(d: String) = JSONObject().put("type", "string").put("description", d)
    private fun i(d: String) = JSONObject().put("type", "integer").put("description", d)
    private fun e(v: List<String>, d: String) = JSONObject().put("type", "string").put("enum", JSONArray(v)).put("description", d)

    private val itemSchema = props(
        "label" to s("Row text, max 4 words"), "detail" to s("Tiny secondary text, 2-5 words"),
        "kind" to e(listOf("app", "navigate", "call", "share_location", "ride", "music", "web"), "What tapping does"),
        "target" to s("App name, destination, contact or URL"),
    )

    private val tools = JSONArray()
        .put(fn("get_now", "Current time, day, place label, Bluetooth/headphones/rain and the active context."))
        .put(fn("list_contexts", "The user's saved contexts with their triggers and rows."))
        .put(fn("list_apps", "Installed apps with a category (distracting, transport, payment, productivity...)."))
        .put(fn("propose_context", "Stage a new context (applied only after the user accepts the plan).", props(
            "name" to s("1-3 words"), "chip" to s("Status chip, e.g. 'Gym · evening'"),
            "place" to s("Place label or empty"), "start_hour" to i("0-23 or -1"), "end_hour" to i("1-24 or -1"),
            "weekdays_only" to JSONObject().put("type", "boolean"),
            "days" to JSONObject().put("type", "array").put("description", "Specific days, empty for any day")
                .put("items", e(listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"), "Day")),
            "bluetooth" to e(listOf("none", "car", "headphones"), "Trigger device"),
            "items" to JSONObject().put("type", "array").put("maxItems", 4).put("items", itemSchema),
            "hide" to JSONObject().put("type", "array").put("items", s("Installed app name to hide here")),
            "mood" to e(listOf("awake", "glasses", "seatbelt", "headphones", "sleepy"), "Companion look"),
            "look" to e(listOf("cream", "study", "car", "game", "rain", "night"), "Palette"),
            "greeting" to s("What the companion says when it starts, max 8 words"),
            "outfit" to s("Accessory the companion wears there, 3-8 words, never 'tiny'/'small', or empty"),
        )))
        .put(fn("update_context", "Stage a change to ONE existing context (applied after the user accepts). Only pass the fields to change; items/hide replace the whole list, add_items/remove_items/add_hide/remove_hide edit it.", JSONObject()
            .put("type", "object").put("required", JSONArray(listOf("id")))
            .put("properties", JSONObject()
                .put("id", s("Context id from list_contexts"))
                .put("name", s("New name")).put("chip", s("New chip")).put("place", s("Place label, or 'none' to clear"))
                .put("start_hour", i("0-23, -1 to clear")).put("end_hour", i("1-24, -1 to clear"))
                .put("days", JSONObject().put("type", "array").put("items", s("Day name")))
                .put("bluetooth", e(listOf("none", "car", "headphones"), "Trigger device"))
                .put("items", JSONObject().put("type", "array").put("maxItems", 4).put("items", itemSchema))
                .put("add_items", JSONObject().put("type", "array").put("items", itemSchema))
                .put("remove_items", JSONObject().put("type", "array").put("items", s("Row label to remove")))
                .put("hide", JSONObject().put("type", "array").put("items", s("App name")))
                .put("add_hide", JSONObject().put("type", "array").put("items", s("App name")))
                .put("remove_hide", JSONObject().put("type", "array").put("items", s("App name")))
                .put("greeting", s("Greeting")).put("outfit", s("Accessory, or 'none'"))
                .put("mood", e(listOf("awake", "glasses", "seatbelt", "headphones", "sleepy"), "Companion look"))
                .put("look", e(listOf("cream", "study", "car", "game", "rain", "night"), "Palette")))))
        .put(fn("delete_context", "Stage deleting one of the user's contexts by name.", props("name" to s("Context name"))))
        .put(fn("open_app", "Open an installed app right now.", props("name" to s("Exact app name"))))
        .put(fn("wear", "Put on, hold or play something right now (hat, glasses, makeup, instrument). Generated with Muse Image; stacks on what it wears.", props("accessory" to s("3-10 words, specific and visual"))))
        .put(fn("undress", "Take off everything it wears."))
        .put(fn("share_postcard", "Paint a postcard of the companion in the current moment with Muse Image and open the share sheet (for posting on X / Instagram)."))
        .put(fn("paint_background", "Paint a new home screen background for the current context with Muse Image (a calm wallpaper).", props("description" to s("What the wallpaper shows, 5-15 words, visual"))))
        .put(fn("remember", "Save a durable fact or preference about the user (e.g. 'plays guitar', 'gym on Tue and Thu'). If it updates something you already remember, pass the old fact in 'replaces'.",
            props("fact" to s("Short fact"), "replaces" to s("Exact old fact it supersedes, or empty"), required = listOf("fact"))))
        .put(fn("forget", "Forget a fact you remember (the user asked, or it is wrong or outdated).", props("fact" to s("Exact fact as remembered"))))
        .put(fn("add_reminder", "Open the calendar with a new event filled in (the user saves it). For 'remind me…', appointments, deadlines.", props(
            "title" to s("Short title"), "date" to s("YYYY-MM-DD"), "time" to s("HH:mm, 24h"))))

    private fun system(memory: List<String>) = """You are the companion living inside Moment, a calm Android home screen: a small round yellow fruit with a leaf.
You help the user shape their home screen into "contexts": for a place, time window or device, at most 4 rows (apps or actions) and which distracting apps to hide.
Use tools. Look before you act (get_now, list_contexts, list_apps) when it helps. You can call several tools in one turn.
To change an existing context, use update_context with its id (never delete and recreate it).
If a tool returns an error, read it and fix the call (right id, installed app name) instead of giving up; tell the user only if it still fails.
Remember new durable facts the user tells you (routines, tastes, people); do not store passwords, codes or anything private they did not ask you to keep.
Changes to contexts are only STAGED: the user approves them on a card. Opening apps, wearing things and remembering facts happen right away.
For a multi-part request ("set up my week", "I go to the gym Tuesday and Thursday after class"), stage every context needed in one go.
Only use installed app names for app rows; prefer actions (navigate, call, ride, share_location) when they save a step.
Hide apps only for focus moments (study, work, gym, driving, sleep) or when asked.
You are also a friend. When the user just talks (hello, how are you, a joke, a worry, a question about anything), answer warmly like a companion: at most 2 short sentences, plain, sometimes one light question back. Use what you know (time, weather, steps, next event, memory) when it fits; never lecture. No tools needed for small talk.
After you act or stage changes, finish with ONE short reply: at most 10 words, plain, no emoji, no markdown. Say what you staged or did, nothing else: never add "for you to approve", "ready", "let me know" or similar (the card already says it). Example: "Staged a Game time screen with your squad and top games."
${if (memory.isEmpty()) "" else "What you remember about the user: " + memory.joinToString("; ") + "."}"""

    /** [history]: the conversation so far, oldest first, as (fromUser, text). */
    suspend fun run(request: String, host: AgentHost, history: List<Pair<Boolean, String>> = emptyList()): AgentPlan {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system(host.memory())))
        history.takeLast(HISTORY).forEach { (me, text) -> messages.put(JSONObject().put("role", if (me) "user" else "assistant").put("content", text)) }
        messages.put(JSONObject().put("role", "user").put("content", request))
        val steps = mutableListOf<PlanStep>(); val trace = mutableListOf<String>()
        repeat(MAX_TURNS) {
            val msg = MuseClient.chat(messages, tools)
            val calls = msg.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                return AgentPlan(msg.optString("content").trim().ifBlank { "Done." }, steps, trace)
            }
            messages.put(msg)
            for (k in 0 until calls.length()) {
                val call = calls.getJSONObject(k)
                val f = call.getJSONObject("function")
                val args = runCatching { JSONObject(f.optString("arguments", "{}")) }.getOrDefault(JSONObject())
                val result = execute(f.getString("name"), args, host, steps, trace)
                messages.put(JSONObject().put("role", "tool").put("tool_call_id", call.getString("id")).put("content", result))
            }
        }
        return AgentPlan("Here's what I put together.", steps, trace)
    }

    private fun execute(name: String, a: JSONObject, host: AgentHost, steps: MutableList<PlanStep>, trace: MutableList<String>): String {
        fun step(t: String) { trace += t; host.progress(t) }
        return when (name) {
            "get_now" -> { step("Checking the moment"); host.now().toString() }
            "list_contexts" -> { step("Looking at your contexts"); host.contexts().toString() }
            "list_apps" -> { step("Looking at your apps"); host.apps().toString() }
            "propose_context" -> {
                val ctxJson = JSONObject()
                    .put("name", a.optString("name")).put("chip", a.optString("chip", a.optString("name")))
                    .put("trigger", JSONObject().put("place", a.optString("place")).put("start_hour", a.optInt("start_hour", -1))
                        .put("end_hour", a.optInt("end_hour", -1)).put("weekdays_only", a.optBoolean("weekdays_only"))
                        .put("days", a.optJSONArray("days") ?: JSONArray())
                        .put("bluetooth", a.optString("bluetooth", "none")))
                    .put("items", a.optJSONArray("items") ?: JSONArray()).put("hide", a.optJSONArray("hide") ?: JSONArray())
                    .put("mood", a.optString("mood", "awake")).put("look", a.optString("look", "cream"))
                    .put("greeting", a.optString("greeting")).put("outfit", a.optString("outfit"))
                val c = MomentContext.fromJson(ctxJson, id = "ctx_" + System.nanoTime())
                val label = describe(c)
                steps += PlanStep.CreateContext(c, short(c)); step("Drafting '${c.name}'")
                "staged as step ${steps.size}: $label"
            }
            "update_context" -> {
                val old = host.context(a.optString("id")) ?: return err("no context with id '${a.optString("id")}'; call list_contexts for ids")
                val c = patch(old, a)
                steps.removeAll { it is PlanStep.UpdateContext && it.context.id == c.id }
                steps += PlanStep.UpdateContext(c, "Update '${c.name}'"); step("Editing '${old.name}'")
                "staged as step ${steps.size}: ${describe(c)}"
            }
            "delete_context" -> {
                val target = a.optString("name")
                val all = host.contexts()
                val match = (0 until all.length()).map { all.getJSONObject(it) }.firstOrNull { it.optString("name").equals(target, true) }
                if (match == null) err("no context named '$target'; call list_contexts") else {
                    steps += PlanStep.DeleteContext(match.getString("id"), "Remove '${match.optString("name")}'")
                    step("Planning to remove '$target'"); "staged removal"
                }
            }
            "open_app" -> { step("Opening ${a.optString("name")}"); host.openApp(a.optString("name")) }
            "wear" -> { step("Trying on ${a.optString("accessory")}"); host.wear(a.optString("accessory")) }
            "undress" -> { step("Taking everything off"); host.undress() }
            "share_postcard" -> { step("Painting a postcard"); host.share() }
            "paint_background" -> { step("Painting a background"); host.paintBackground(a.optString("description")) }
            "remember" -> { step("Remembering: ${a.optString("fact")}"); host.remember(a.optString("fact"), a.optString("replaces")) }
            "forget" -> { step("Forgetting: ${a.optString("fact")}"); host.forget(a.optString("fact")) }
            "add_reminder" -> {
                val at = runCatching { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ENGLISH).parse("${a.optString("date")} ${a.optString("time")}")!!.time }
                    .getOrNull() ?: return err("date must be YYYY-MM-DD and time HH:mm")
                step("Adding '${a.optString("title")}' to your calendar"); host.addReminder(a.optString("title"), at)
            }
            else -> err("unknown tool '$name'")
        }
    }

    private fun err(msg: String) = JSONObject().put("error", msg).toString()

    /** Applies only the fields the model passed to [old]. */
    private fun patch(old: MomentContext, a: JSONObject): MomentContext {
        fun str(k: String) = a.optString(k).takeIf { a.has(k) && it.isNotBlank() }
        fun list(k: String) = if (a.has(k)) (0 until (a.optJSONArray(k)?.length() ?: 0)).map { a.getJSONArray(k).optString(it) } else null
        fun items(k: String) = a.optJSONArray(k)?.let { arr -> (0 until arr.length()).map { app.moment.launcher.data.NowItem.fromJson(arr.getJSONObject(it)) } }
        val t = old.trigger
        val hour = { k: String, cur: Int? -> if (a.has(k)) a.optInt(k, -1).takeIf { it in 0..24 } else cur }
        val trigger = t.copy(
            place = str("place")?.let { if (it.equals("none", true)) null else it } ?: t.place,
            startHour = hour("start_hour", t.startHour), endHour = hour("end_hour", t.endHour),
            days = list("days") ?: t.days,
            bluetooth = str("bluetooth")?.let { if (it == "none") null else it } ?: t.bluetooth,
        )
        val removeLabels = list("remove_items").orEmpty()
        val rows = ((items("items") ?: old.items).filter { r -> removeLabels.none { it.equals(r.label, true) } } + items("add_items").orEmpty())
            .distinctBy { it.label.lowercase() }.take(4)
        val removeHide = list("remove_hide").orEmpty()
        val hide = ((list("hide") ?: old.hide) + list("add_hide").orEmpty()).filter { h -> removeHide.none { it.equals(h, true) } }.distinct()
        return old.copy(
            name = str("name") ?: old.name, chip = str("chip") ?: old.chip, trigger = trigger, items = rows, hide = hide,
            focus = hide.isNotEmpty() || (old.focus && old.hide.isEmpty()), greeting = str("greeting") ?: old.greeting,
            outfit = str("outfit")?.let { if (it.equals("none", true)) "" else app.moment.launcher.data.cleanAccessory(it) } ?: old.outfit,
            mood = str("mood")?.let { app.moment.launcher.data.enumOr(it, old.mood) } ?: old.mood,
            look = str("look")?.let { app.moment.launcher.data.enumOr(it, old.look) } ?: old.look,
        )
    }

    /** What the plan card shows for a staged context: its name and when, nothing more. */
    fun short(c: MomentContext): String {
        val t = c.trigger
        val whenText = buildList {
            if (t.days.isNotEmpty()) add(t.days.joinToString(", ") { it.take(3) }) else if (t.weekdaysOnly) add("weekdays")
            if (t.startHour != null && t.endHour != null) add("${t.startHour}–${t.endHour}h")
            t.bluetooth?.let { add("$it Bluetooth") }
            t.place?.let { add(it) }
        }.take(2).joinToString(" · ").ifBlank { "when you ask" }
        return "${c.name} · $whenText"
    }

    fun describe(c: MomentContext): String {
        val t = c.trigger
        val whenText = buildList {
            t.place?.let { add(it) }
            if (t.startHour != null && t.endHour != null) add("${t.startHour}–${t.endHour}h")
            if (t.days.isNotEmpty()) add(t.days.joinToString(", ") { it.take(3) })
            else if (t.weekdaysOnly) add("weekdays")
            t.bluetooth?.let { add("$it Bluetooth") }
        }.joinToString(" · ").ifBlank { "when you ask" }
        val shows = c.items.joinToString(", ") { it.label }
        val hides = if (c.hide.isEmpty()) "" else "; hides ${c.hide.joinToString(", ")}"
        return "${c.name} · $whenText · $shows$hides"
    }
}
