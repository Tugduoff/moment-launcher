package app.moment.launcher.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * A theme = a saved look: Style (fonts, clock, effect) + Skin (companion colours and face).
 * Themes can be tied to a context (the look switches with the moment) and shared as a short code.
 */
data class Theme(val name: String, val style: String, val skin: String) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("style", style).put("skin", skin)

    /** "moment-theme:…" text anyone can paste into Moment's search to import it. */
    fun code(): String = PREFIX + android.util.Base64.encodeToString(toJson().toString().toByteArray(), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)

    companion object {
        const val PREFIX = "moment-theme:"
        fun fromJson(o: JSONObject) = Theme(o.optString("name", "Theme"), o.optString("style"), o.optString("skin"))
        fun fromCode(text: String): Theme? = runCatching {
            val raw = text.trim().substringAfter(PREFIX)
            fromJson(JSONObject(String(android.util.Base64.decode(raw, android.util.Base64.URL_SAFE))))
        }.getOrNull()
        fun list(json: String?): List<Theme> = runCatching { JSONArray(json ?: "[]").let { a -> (0 until a.length()).map { fromJson(a.getJSONObject(it)) } } }.getOrDefault(emptyList())
        fun save(list: List<Theme>) = JSONArray(list.map { it.toJson() }).toString()
    }
}
