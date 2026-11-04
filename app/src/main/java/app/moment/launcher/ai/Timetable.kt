package app.moment.launcher.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import app.moment.launcher.MomentViewModel
import app.moment.launcher.data.MomentContext
import app.moment.launcher.data.Look
import app.moment.launcher.data.Mood
import app.moment.launcher.data.NowItem
import app.moment.launcher.data.Trigger
import app.moment.launcher.data.enumOr
import app.moment.launcher.data.objects
import app.moment.launcher.data.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * "Snap your timetable": Muse Spark reads a photo of a weekly schedule (image understanding on
 * Chat Completions: an `image_url` content part with a base64 data URL) and proposes contexts.
 * The rules still decide WHEN (hours + days + place label); the model only decides WHAT.
 * Only the photo and app names leave the phone, and only after the user picked or took the photo.
 */

/** One proposed activity (a course, lunch, sport...) and the contexts its weekly slots expand to. */
data class TimetableGroup(
    val name: String,
    val slotsText: String,   // "Mon 9–11 · Thu 10–12"
    val showsText: String,   // "Notion, Calculator · hides 4 apps"
    val contexts: List<MomentContext>,
)

data class TimetableResult(val summary: String, val groups: List<TimetableGroup>, val latencyMs: Long) {
    val contexts get() = groups.flatMap { it.contexts }
}

private val DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun str(d: String) = JSONObject().put("type", "string").put("description", d)
private fun int(d: String) = JSONObject().put("type", "integer").put("description", d)
private fun enum(v: List<String>, d: String) = JSONObject().put("type", "string").put("enum", JSONArray(v)).put("description", d)
private fun arr(items: JSONObject, d: String = "") = JSONObject().put("type", "array").put("items", items).put("description", d)
private fun obj(vararg p: Pair<String, JSONObject>) = JSONObject().put("type", "object")
    .put("properties", JSONObject().apply { p.forEach { put(it.first, it.second) } })
    .put("required", JSONArray(p.map { it.first })).put("additionalProperties", false)

private val slotSchema = obj(
    "days" to arr(enum(DAYS, "Day"), "Days on which this slot happens at exactly these hours"),
    "start_hour" to int("Start hour 0-23"),
    "end_hour" to int("End hour 1-24 (exclusive)"),
    "place" to str("Exactly 'Library' if the slot takes place in the library, else an empty string"),
    "room" to str("Room or venue as written, e.g. 'Amphi B' or 'Gym', or empty"),
)

private val timetableSchema = obj(
    "summary" to str("One friendly line about what you found, max 14 words"),
    "contexts" to arr(obj(
        "name" to str("Short context name, 1-3 words, e.g. 'Algorithms' or 'Lunch'"),
        "chip" to str("Status chip text, e.g. 'Algorithms · class'"),
        "slots" to arr(slotSchema, "Every weekly time slot of this activity"),
        "items" to arr(obj(
            "label" to str("What the row says, e.g. 'Notion'"),
            "detail" to str("Tiny secondary text, 2-5 words"),
            "kind" to enum(listOf("app", "navigate", "call", "share_location", "ride", "music", "web"), "app opens an app; navigate opens directions; music plays a playlist; web opens a URL"),
            "target" to str("App name for app/music, destination for navigate, URL for web, empty otherwise"),
        )).put("maxItems", 4),
        "hide" to arr(str("Exact name of an installed app to hide")),
        "mood" to enum(listOf("awake", "glasses", "headphones", "sleepy"), "How the companion looks: glasses to focus, headphones for sport or music"),
        "look" to enum(listOf("cream", "study", "game", "rain", "night"), "Home screen palette"),
        "greeting" to str("What the companion says when this starts, max 8 words, friendly, no emoji"),
        "outfit" to str("One small accessory the companion (a round yellow fruit with a leaf) wears, 3-8 words, never the words tiny or small, e.g. 'a white chef hat'. Empty if none. No glasses or headphones."),
    )).put("maxItems", 10),
)

private const val SYSTEM = """You are the companion inside Moment, a minimalist Android home screen. You read a photo of a person's weekly schedule (class timetable, work shifts, gym plan, a screenshot) and turn it into contexts: for each recurring activity, which apps or actions to show (max 4, most useful first) and which distracting apps to hide.
Rules:
- One context per activity (a course, lunch, sport, a shift). Put every weekly occurrence in slots; merge days that share the same hours into one slot.
- Hours are whole numbers, 24h clock. Round half hours outward.
- Class, study, lab, work: 2-4 useful apps (notes, calendar, calculator; a course portal as a web item only if you are sure of the URL) and hide the distracting apps (social media, video, games) from the installed list. mood glasses, look study.
- Lunch and meals: hide empty, 1-2 items (a payment app if installed, directions), mood awake, look cream.
- Sport or gym: mood headphones, a music item, hide empty.
- Only use apps from the installed list for kind app or music, with the exact name from the list.
- Read only what is on the image. Never invent slots. If it is not a schedule, return an empty contexts array and say so in summary."""

class Prepared(val jpeg: ByteArray, val thumb: Bitmap)

/** Decodes any photo, fixes EXIF rotation, downsizes to ~[maxEdge] px and re-encodes as JPEG. */
fun prepareJpeg(raw: ByteArray, maxEdge: Int = 1280): Prepared {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("That file isn't an image")
    val degrees = runCatching {
        when (ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
        }
    }.getOrDefault(0f)
    val k = maxEdge.toFloat() / maxOf(bmp.width, bmp.height)
    if (degrees != 0f || k < 1f) {
        val m = Matrix().apply { if (k < 1f) postScale(k, k); postRotate(degrees) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    if (bmp.hasAlpha()) { // JPEG has no alpha: flatten onto white
        val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
        bmp = flat
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    val t = 220f / maxOf(bmp.width, bmp.height)
    return Prepared(out.toByteArray(), Bitmap.createScaledBitmap(bmp, (bmp.width * t).toInt().coerceAtLeast(1), (bmp.height * t).toInt().coerceAtLeast(1), true))
}

/**
 * Reads a timetable photo with Muse Spark (one structured-output call) and returns proposed contexts,
 * one per weekly slot, grouped by activity. [now] is a label like "Wednesday 16:20".
 */
suspend fun readTimetable(jpegBytes: ByteArray, installedApps: List<String>, now: String): TimetableResult =
    withContext(Dispatchers.IO) {
        val user = JSONArray()
            .put(JSONObject().put("type", "text").put("text",
                "Installed apps: ${installedApps.joinToString(", ")}\nNow: $now\nThe place label 'Library' is the only place name Moment knows; use it only when the schedule says library.\nRead this schedule and propose the contexts."))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject()
                .put("url", "data:image/jpeg;base64," + Base64.encodeToString(jpegBytes, Base64.NO_WRAP))))
        val body = JSONObject()
            .put("model", MuseClient.TEXT_MODEL)
            .put("reasoning_effort", "minimal")
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM))
                .put(JSONObject().put("role", "user").put("content", user)))
            .put("response_format", JSONObject().put("type", "json_schema")
                .put("json_schema", JSONObject().put("name", "timetable").put("schema", timetableSchema)))
        val started = System.currentTimeMillis()
        val res = MuseClient.post("/chat/completions", body)
        val ms = System.currentTimeMillis() - started
        val content = res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        Log.d("Muse", "[timetable] ${ms}ms $content")
        parseTimetable(JSONObject(content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")), installedApps, ms)
    }

private fun parseTimetable(o: JSONObject, installed: List<String>, ms: Long): TimetableResult {
    val groups = o.optJSONArray("contexts").objects().mapIndexedNotNull { gi, c ->
        val name = c.optString("name").ifBlank { return@mapIndexedNotNull null }
        val slots = c.optJSONArray("slots").objects().filter { it.optInt("end_hour", -1) in 1..24 && it.optInt("start_hour", -1) in 0..23 }
        if (slots.isEmpty()) return@mapIndexedNotNull null
        val hide = c.optJSONArray("hide").strings().mapNotNull { h -> installed.firstOrNull { it.equals(h, true) } }
        val items = c.optJSONArray("items").objects().map(NowItem::fromJson).filter { it.label.isNotBlank() }.take(4)
        val slug = name.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "c$gi" }
        val contexts = slots.mapIndexed { si, s ->
            val days = s.optJSONArray("days").strings().map { it.lowercase().take(3) }.filter { it in DAYS }.distinct()
            val place = s.optString("place").trim().takeIf { it.isNotBlank() }
            val room = s.optString("room").trim()
            MomentContext(
                id = "tt_${slug}_$si",
                name = name,
                chip = (room.ifBlank { place.orEmpty() }).let { w -> if (w.isNotBlank()) "$name · $w" else c.optString("chip").ifBlank { name } },
                trigger = Trigger(place = place, startHour = s.optInt("start_hour"), endHour = s.optInt("end_hour"), days = days),
                items = items, hide = hide,
                mood = enumOr(c.optString("mood"), Mood.AWAKE), look = enumOr(c.optString("look"), Look.CREAM),
                reason = "From your timetable: ${slotText(s)}" + if (room.isNotBlank()) " in $room." else ".",
                greeting = c.optString("greeting"), focus = hide.isNotEmpty(), outfit = c.optString("outfit"),
            )
        }
        val shows = items.joinToString(", ") { it.label } + if (hide.isNotEmpty()) " · hides ${hide.size} app${if (hide.size > 1) "s" else ""}" else ""
        TimetableGroup(name, slots.joinToString(" · ") { slotText(it) }, shows.removePrefix(" · "), contexts)
    }
    return TimetableResult(o.optString("summary"), groups, ms)
}

/** "Mon–Fri 12–13", "Mon, Thu 9–11". */
private fun slotText(s: JSONObject): String {
    val idx = s.optJSONArray("days").strings().map { DAYS.indexOf(it.lowercase().take(3)) }.filter { it >= 0 }.distinct().sorted()
    val days = when {
        idx.isEmpty() -> "Every day"
        idx.size >= 3 && idx.last() - idx.first() == idx.size - 1 -> "${DAY_LABELS[idx.first()]}–${DAY_LABELS[idx.last()]}"
        else -> idx.joinToString(", ") { DAY_LABELS[it] }
    }
    return "$days ${s.optInt("start_hour")}–${s.optInt("end_hour")}"
}

/** UI state of the flow, kept outside the ViewModel so the feature stays in its own files. */
object Timetable {
    sealed interface State {
        data object Idle : State
        data class Reading(val thumb: ImageBitmap? = null) : State
        data class Review(val result: TimetableResult, val thumb: ImageBitmap?) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** Runs the whole read: shrink, ask Muse Spark (companion thinks meanwhile), then show the review card. */
    fun start(vm: MomentViewModel, raw: ByteArray) {
        if (_state.value is State.Reading) return
        _state.value = State.Reading()
        vm.setThinking(true)
        vm.viewModelScope.launch {
            val result = runCatching {
                val prep = withContext(Dispatchers.Default) { prepareJpeg(raw) }
                val thumb = prep.thumb.asImageBitmap()
                _state.value = State.Reading(thumb)
                val st = vm.ui.value.signals
                val now = st?.let { "%s %02d:%02d".format(it.dayLabel, it.minuteOfDay / 60, it.minuteOfDay % 60) } ?: ""
                readTimetable(prep.jpeg, vm.ui.value.apps.map { it.label }, now) to thumb
            }
            vm.setThinking(false)
            result.onSuccess { (r, thumb) ->
                _state.value = if (r.groups.isEmpty()) State.Failed(r.summary.ifBlank { "I couldn't find a schedule in that photo." })
                else State.Review(r, thumb)
            }.onFailure { e ->
                Log.e("Moment", "timetable failed", e)
                _state.value = State.Failed(if (MuseClient.hasKey) "I couldn't read that one. Try a clearer photo?" else "No API key in this build.")
            }
        }
    }

    /** Demo hook: run the flow on an image file on the device. */
    fun startFromFile(vm: MomentViewModel, path: String) {
        vm.viewModelScope.launch {
            // Scoped storage blocks /sdcard/Download for adb-pushed files: fall back to the app's own
            // folder (/sdcard/Android/data/app.moment.launcher/files/<same file name>).
            runCatching {
                withContext(Dispatchers.IO) {
                    runCatching { File(path).readBytes() }.getOrElse {
                        File(vm.getApplication<android.app.Application>().getExternalFilesDir(null), File(path).name).readBytes()
                    }
                }
            }
                .onSuccess { start(vm, it) }
                .onFailure { Log.e("Moment", "timetable file $path", it); _state.value = State.Failed("I can't open that file.") }
        }
    }

    fun add(vm: MomentViewModel, picked: List<TimetableGroup>) {
        val all = picked.flatMap { it.contexts }
        if (all.isNotEmpty()) vm.addContexts(all, "Added ${picked.size} from your timetable.")
        _state.value = State.Idle
    }

    fun close() { _state.value = State.Idle }
}
