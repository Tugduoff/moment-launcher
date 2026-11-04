package app.moment.launcher.ai

import app.moment.launcher.data.MomentContext
import app.moment.launcher.data.Proposal
import org.json.JSONArray
import org.json.JSONObject

/**
 * The jobs Muse Spark does for Moment. Rules decide WHEN a context exists;
 * the model decides WHAT goes in it. Only labels are sent: place names,
 * hour buckets and app names. Never coordinates, never usage history.
 */
object MuseJobs {

    private val ITEM_KINDS = listOf("app", "navigate", "call", "share_location", "ride", "music", "web")
    private val MOODS = listOf("awake", "glasses", "seatbelt", "headphones", "sleepy")
    private val LOOKS = listOf("cream", "study", "car", "game", "rain", "night")

    private fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)
    private fun int(desc: String) = JSONObject().put("type", "integer").put("description", desc)
    private fun enum(values: List<String>, desc: String) =
        JSONObject().put("type", "string").put("enum", JSONArray(values)).put("description", desc)

    private fun obj(vararg props: Pair<String, JSONObject>) = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject().apply { props.forEach { put(it.first, it.second) } })
        .put("required", JSONArray(props.map { it.first }))
        .put("additionalProperties", false)

    private val contextSchema = obj(
        "name" to str("Short context name, 1-3 words, e.g. 'Uni restaurant'"),
        "chip" to str("Status chip text, e.g. 'Uni restaurant · lunch'"),
        "trigger" to obj(
            "place" to str("Place label this context is tied to, or empty string"),
            "start_hour" to int("Start hour 0-23, or -1 if not time-based"),
            "end_hour" to int("End hour 1-24 (exclusive), or -1"),
            "weekdays_only" to JSONObject().put("type", "boolean"),
            "bluetooth" to enum(listOf("none", "car", "headphones"), "Bluetooth device that triggers it"),
        ),
        "items" to JSONObject().put("type", "array").put("maxItems", 4).put("items", obj(
            "label" to str("What the row says, e.g. 'Pay with Izly' or 'Spotify'"),
            "detail" to str("Tiny secondary text, e.g. 'Opens your payment QR code' or 'Lunch mix'"),
            "kind" to enum(ITEM_KINDS, "app opens an app; navigate opens directions; call calls a contact; share_location; ride books a ride; music plays a playlist; web opens a URL"),
            "target" to str("App name for app/music, destination for navigate, contact for call, URL for web, empty otherwise"),
        )),
        "hide" to JSONObject().put("type", "array").put("items", str("Exact name of an installed app to hide in this context")),
        "mood" to enum(MOODS, "How the companion looks: glasses to focus, seatbelt when driving, headphones for games/music, sleepy at night"),
        "look" to enum(LOOKS, "Home screen palette"),
        "greeting" to str("What the companion says when the context starts, max 8 words, friendly, no emoji"),
    )

    private val proposalSchema = obj(
        "intent" to enum(listOf("context", "answer"), "context if the user describes a situation or rule; answer for a plain question"),
        "answer" to str("If intent=answer: one short sentence. Else empty."),
        "question" to str("What the companion asks to confirm, e.g. \"You come here most weekdays at noon. Make it 'Uni restaurant'?\""),
        "explanation" to str("One line: what accepting will do, e.g. 'Adds Izly and directions to 17 bd Jourdan on weekdays around noon.'"),
        "context" to contextSchema,
    )

    private const val SYSTEM = """You are the companion inside Moment, a minimalist Android home screen.
The home screen shows a clock, you (a small round fruit character), at most 4 rows for this moment, and a search bar.
You build "contexts": for a given place, time or device, which apps or actions to show (max 4, most useful first) and which distracting apps to hide.
Rules:
- Only use apps from the installed list when kind is app or music; use the exact name from the list. If the user names an app that is not installed, still use its name.
- Prefer actions over apps when they save a step: navigate to a saved place, call someone, share location, book a ride, pay.
- Keep labels short (max 4 words). Details are 2-5 words.
- Hide only apps that distract from the moment (social, video, games) and only when it makes sense (study, work, driving).
- You never see coordinates or history, only labels. Be concise and warm, never cute-overload."""

    /** A pattern the rules noticed ("3 visits here around noon on weekdays") becomes a drafted context. */
    suspend fun draftFromPattern(
        placeLabel: String, address: String?, patternText: String, timeText: String, installedApps: List<String>,
    ): Proposal {
        val user = """Pattern detected on the phone: $patternText
Current place label: $placeLabel${address?.let { " (address: $it)" } ?: ""}
Now: $timeText
Installed apps: ${installedApps.joinToString(", ")}
Draft a context for this place and time. The user is a student in Paris; the CROUS student card app 'Izly' is how they pay at the uni restaurant (use kind app, target Izly).
Include directions to the address as a navigate item if relevant.
The question must mention the pattern and propose the context name."""
        val o = MuseClient.json(SYSTEM, user, "proposal", proposalSchema)
        return o.toProposal("pattern")
    }

    /** "At the library, hide social apps and show Notion" becomes a drafted context. */
    suspend fun fromSentence(sentence: String, placeLabel: String, timeText: String, installedApps: List<String>): Proposal {
        val user = """The user typed to you: "$sentence"
Current place label: $placeLabel
Now: $timeText
Installed apps: ${installedApps.joinToString(", ")}
If this describes a situation, rule or preference, draft the context. If it is a plain question, set intent=answer and still fill context with your best guess of a related context."""
        val o = MuseClient.json(SYSTEM, user, "proposal", proposalSchema)
        return o.toProposal("sentence")
    }

    /** Sorts installed apps once, so Focus knows what to hide by default. */
    suspend fun classifyApps(appNames: List<String>): Map<String, String> {
        val schema = obj(
            "apps" to JSONObject().put("type", "array").put("items", obj(
                "name" to str("Exact app name from the list"),
                "category" to enum(listOf("distracting", "transport", "payment", "safety", "communication", "productivity", "other"), "Category"),
            )),
        )
        val o = MuseClient.json(
            "You sort a phone's installed apps for a focus-friendly launcher. Social media, short video, video streaming, games and news feeds are distracting.",
            "Apps: ${appNames.joinToString(", ")}",
            "app_categories", schema,
        )
        val arr = o.optJSONArray("apps") ?: return emptyMap()
        return (0 until arr.length()).associate {
            val a = arr.getJSONObject(it); a.optString("name") to a.optString("category")
        }
    }

    private fun JSONObject.toProposal(source: String): Proposal {
        val ctx = MomentContext.fromJson(getJSONObject("context"), id = "ctx_" + System.currentTimeMillis())
        return Proposal(
            context = ctx,
            question = if (optString("intent") == "answer") optString("answer") else optString("question"),
            explanation = optString("explanation"),
            source = if (optString("intent") == "answer") "answer" else source,
        )
    }
}
