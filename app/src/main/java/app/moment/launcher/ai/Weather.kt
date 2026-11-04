package app.moment.launcher.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class WeatherNow(val rainingNow: Boolean, val rainSoon: Boolean, val tempC: Int, val summary: String)

/**
 * Live weather with Muse Spark + the built-in web_search tool (search grounding, Responses API).
 * No weather API, no location: only the city name is sent.
 */
object Weather {
    suspend fun now(city: String): WeatherNow = withContext(Dispatchers.IO) {
        val schema = JSONObject().put("type", "object").put("additionalProperties", false)
            .put("properties", JSONObject()
                .put("raining_now", JSONObject().put("type", "boolean"))
                .put("rain_next_3h", JSONObject().put("type", "boolean"))
                .put("temp_c", JSONObject().put("type", "integer"))
                .put("summary", JSONObject().put("type", "string").put("description", "max 4 words incl. temperature, e.g. 'Light rain, 11°C'")))
            .put("required", JSONArray(listOf("raining_now", "rain_next_3h", "temp_c", "summary")))
        val body = JSONObject()
            .put("model", MuseClient.TEXT_MODEL)
            .put("reasoning", JSONObject().put("effort", "minimal"))
            .put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
            .put("input", "Use web search: what is the weather in $city right now, and will it rain in the next 3 hours?")
            .put("text", JSONObject().put("format", JSONObject().put("type", "json_schema").put("name", "weather").put("schema", schema)))
        val res = MuseClient.postRaw("/responses", body)
        val out = res.getJSONArray("output")
        // The final message (after the web_search_call items) carries the JSON.
        val text = (out.length() - 1 downTo 0).map { out.getJSONObject(it) }
            .filter { it.optString("type") == "message" }
            .flatMap { m -> (0 until m.getJSONArray("content").length()).map { m.getJSONArray("content").getJSONObject(it).optString("text") } }
            .first { it.trim().startsWith("{") }
        Log.d("Muse", "[weather] $text")
        val j = JSONObject(text)
        WeatherNow(j.optBoolean("raining_now"), j.optBoolean("rain_next_3h"), j.optInt("temp_c"), j.optString("summary"))
    }
}
