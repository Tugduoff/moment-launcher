package app.moment.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Everything Moment remembers, stored only on the phone (SharedPreferences). */
class ContextStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("moment", Context.MODE_PRIVATE)

    data class Visit(val place: String, val weekday: Boolean, val hour: Int, val at: Long)

    fun userContexts(): List<MomentContext> =
        JSONArray(prefs.getString("contexts", "[]")).objects().map { MomentContext.fromJson(it) }

    fun saveContext(c: MomentContext) {
        val list = userContexts().filter { it.id != c.id } + c
        prefs.edit().putString("contexts", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun visits(): List<Visit> = JSONArray(prefs.getString("visits", "[]")).objects().map {
        Visit(it.optString("place"), it.optBoolean("weekday"), it.optInt("hour"), it.optLong("at"))
    }

    fun logVisit(v: Visit) {
        val arr = JSONArray(prefs.getString("visits", "[]"))
        arr.put(JSONObject().put("place", v.place).put("weekday", v.weekday).put("hour", v.hour).put("at", v.at))
        prefs.edit().putString("visits", arr.toString()).apply()
    }

    /** Places the user said "no" to: the companion won't propose them again. */
    fun dismissedPlaces(): Set<String> = prefs.getStringSet("dismissed", emptySet()) ?: emptySet()
    fun dismissPlace(p: String) = prefs.edit().putStringSet("dismissed", dismissedPlaces() + p).apply()

    fun appCategories(): Map<String, String> {
        val o = JSONObject(prefs.getString("categories", "{}") ?: "{}")
        return o.keys().asSequence().associateWith { o.optString(it) }
    }

    fun saveAppCategories(m: Map<String, String>) =
        prefs.edit().putString("categories", JSONObject(m).toString()).apply()

    /** Taps per app inside a context, used to reorder its rows. */
    fun tapCount(contextId: String, label: String) = prefs.getInt("tap:$contextId:$label", 0)
    fun countTap(contextId: String, label: String) =
        prefs.edit().putInt("tap:$contextId:$label", tapCount(contextId, label) + 1).apply()

    /** What the companion wears right now, newest last. */
    fun wardrobe(): List<String> = JSONArray(prefs.getString("wardrobe", "[]")).strings()
    fun addToWardrobe(item: String) = prefs.edit().putString("wardrobe", JSONArray(wardrobe() + item).toString()).apply()
    fun removeFromWardrobe(item: String) = prefs.edit().putString("wardrobe", JSONArray(wardrobe().filter { !it.equals(item, true) }).toString()).apply()
    fun clearWardrobe() = prefs.edit().remove("wardrobe").apply()

    fun deleteContext(id: String) =
        prefs.edit().putString("contexts", JSONArray(userContexts().filter { it.id != id }.map { it.toJson() }).toString()).apply()

    /** Facts the companion remembers about the user (agent "remember" tool). */
    fun memory(): List<String> = JSONArray(prefs.getString("memory", "[]")).strings()
    fun forget(fact: String): Boolean {
        val m = memory(); val hit = m.firstOrNull { it.equals(fact.trim(), true) } ?: m.firstOrNull { it.contains(fact.trim(), true) } ?: return false
        prefs.edit().putString("memory", JSONArray(m - hit).toString()).apply(); return true
    }
    /** Saves [fact] unless already known (case and punctuation ignored); drops [replaces] if given. */
    fun remember(fact: String, replaces: String = ""): String {
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
        val f = fact.trim(); if (f.isBlank()) return "nothing to save"
        val old = memory()
        if (old.any { norm(it) == norm(f) }) return "already known"
        val dropped = old.filter { replaces.isNotBlank() && norm(it) == norm(replaces) }
        prefs.edit().putString("memory", JSONArray(((old - dropped.toSet()) + f).takeLast(40)).toString()).apply()
        return if (dropped.isEmpty()) "saved" else "saved, replaced '${dropped.first()}'"
    }

    /** Rows the user edited by hand (long-press: swap or remove), per context. */
    fun itemsFor(contextId: String): List<NowItem>? =
        prefs.getString("items_$contextId", null)?.let { JSONArray(it).objects().map { o -> NowItem.fromJson(o) } }
    fun clearItems(contextId: String) = prefs.edit().remove("items_$contextId").apply()
    fun setItems(contextId: String, items: List<NowItem>) =
        prefs.edit().putString("items_$contextId", JSONArray(items.map { it.toJson() }).toString()).apply()

    fun reset() = prefs.edit().clear().apply()
}
