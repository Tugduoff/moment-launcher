package app.moment.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/** What the phone knows right now. In the demo, every field can be faked from the demo panel. */
data class Signals(
    val minuteOfDay: Int,
    val weekday: Boolean,
    val dayLabel: String,
    val place: Place,
    val carBluetooth: Boolean = false,
    val headphones: Boolean = false,
    val raining: Boolean = false,
)

/** Places are labels, never coordinates: only the label ever reaches the model. */
enum class Place(val label: String, val address: String?) {
    HOME("Home", null),
    JOURDAN("17 bd Jourdan", "17 boulevard Jourdan, Paris"),
    LIBRARY("Library", "BU Sainte-Geneviève, Paris"),
    AWAY("Away from home, bar district", null),
    ELSEWHERE("Somewhere new", null),
}

enum class Mood { ASLEEP, AWAKE, TALKING, THINKING, GLASSES, SEATBELT, HEADPHONES, SLEEPY }

enum class Look { CREAM, STUDY, CAR, GAME, RAIN, NIGHT }

/** One entry of the Now row: an app or an action. Same shape as the model's JSON output. */
data class NowItem(
    val label: String,
    val detail: String = "",
    val kind: String = "app", // app | navigate | call | share_location | ride | music | web
    val target: String = "",
) {
    fun toJson() = JSONObject().put("label", label).put("detail", detail).put("kind", kind).put("target", target)

    companion object {
        fun fromJson(o: JSONObject) = NowItem(
            label = o.optString("label"),
            detail = o.optString("detail"),
            kind = o.optString("kind", "app"),
            target = o.optString("target"),
        )
    }
}

data class Trigger(
    val place: String? = null,
    val startHour: Int? = null,
    val endHour: Int? = null,
    val weekdaysOnly: Boolean = false,
    val bluetooth: String? = null, // car | headphones
) {
    fun toJson() = JSONObject().apply {
        put("place", place ?: "")
        put("start_hour", startHour ?: -1)
        put("end_hour", endHour ?: -1)
        put("weekdays_only", weekdaysOnly)
        put("bluetooth", bluetooth ?: "none")
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Trigger() else Trigger(
            place = o.optStringOrNull("place"),
            startHour = o.optInt("start_hour", -1).takeIf { it in 0..24 },
            endHour = o.optInt("end_hour", -1).takeIf { it in 0..24 },
            weekdaysOnly = o.optBoolean("weekdays_only", false),
            bluetooth = o.optStringOrNull("bluetooth")?.takeIf { it != "none" },
        )
    }
}

/** A context: when it applies, what it shows, what it hides, and how the companion looks. */
data class MomentContext(
    val id: String,
    val name: String,
    val chip: String,
    val trigger: Trigger,
    val items: List<NowItem>,
    val hide: List<String> = emptyList(),
    val mood: Mood = Mood.AWAKE,
    val look: Look = Look.CREAM,
    val reason: String = "",
    val greeting: String = "",
    val builtIn: Boolean = false,
    val bigButtons: Boolean = false,
    val hideSearch: Boolean = false,
    val focus: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("chip", chip)
        put("trigger", trigger.toJson())
        put("items", JSONArray(items.map { it.toJson() }))
        put("hide", JSONArray(hide))
        put("mood", mood.name.lowercase()); put("look", look.name.lowercase())
        put("reason", reason); put("greeting", greeting)
        put("focus", focus)
    }

    companion object {
        fun fromJson(o: JSONObject, id: String = o.optString("id")): MomentContext = MomentContext(
            id = id.ifBlank { "ctx_" + System.currentTimeMillis() },
            name = o.optString("name", "New context"),
            chip = o.optString("chip", o.optString("name")),
            trigger = Trigger.fromJson(o.optJSONObject("trigger")),
            items = o.optJSONArray("items").objects().map(NowItem::fromJson).take(4),
            hide = o.optJSONArray("hide").strings(),
            mood = enumOr(o.optString("mood"), Mood.AWAKE),
            look = enumOr(o.optString("look"), Look.CREAM),
            reason = o.optString("reason"),
            greeting = o.optString("greeting"),
            focus = o.optBoolean("focus", false) || o.optJSONArray("hide").strings().isNotEmpty(),
        )
    }
}

/** A drafted context the companion proposes. The user accepts, edits or dismisses it. */
data class Proposal(
    val context: MomentContext,
    val question: String,
    val explanation: String,
    val source: String, // "pattern" or "sentence"
)

fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
