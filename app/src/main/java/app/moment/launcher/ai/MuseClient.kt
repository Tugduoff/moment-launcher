package app.moment.launcher.ai

import android.util.Base64
import android.util.Log
import app.moment.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal client for Meta Model API (OpenAI-compatible surface).
 * Text: muse-spark-1.3 with constrained JSON output. Images: muse-image-1.0.
 */
object MuseClient {
    private const val BASE = "https://api.meta.ai/v1"
    const val TEXT_MODEL = "muse-spark-1.3"
    const val IMAGE_MODEL = "muse-image-1.0"
    private const val TAG = "Muse"

    val hasKey get() = BuildConfig.MODEL_API_KEY.isNotBlank()

    /** Last round-trip time, shown in the demo panel. */
    @Volatile var lastLatencyMs: Long = 0
        private set

    /** Chat completion constrained to [schema]. Returns the parsed JSON object. */
    suspend fun json(system: String, user: String, schemaName: String, schema: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", TEXT_MODEL)
                // "minimal" keeps answers around 3 s on the home screen; default effort took ~14 s.
                .put("reasoning_effort", "minimal")
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)))
                .put("response_format", JSONObject()
                    .put("type", "json_schema")
                    .put("json_schema", JSONObject().put("name", schemaName).put("schema", schema)))
            val started = System.currentTimeMillis()
            val res = post("/chat/completions", body)
            lastLatencyMs = System.currentTimeMillis() - started
            val content = res.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            Log.d(TAG, "[$schemaName] ${lastLatencyMs}ms $content")
            JSONObject(content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
        }

    /** Generates one image and returns its PNG/JPEG bytes. */
    suspend fun image(prompt: String): ByteArray = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", IMAGE_MODEL)
            .put("prompt", prompt)
            .put("n", 1)
            .put("response_format", "b64_json")
        val res = post("/images/generations", body)
        val item = res.getJSONArray("data").getJSONObject(0)
        val b64 = item.optString("b64_json")
        if (b64.isNotBlank()) Base64.decode(b64, Base64.DEFAULT)
        else URL(item.getString("url")).openStream().use { it.readBytes() }
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        check(hasKey) { "No MODEL_API_KEY in this build" }
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${BuildConfig.MODEL_API_KEY}")
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            Log.e(TAG, "HTTP $code $text")
            error("Meta Model API $code: ${text.take(200)}")
        }
        return JSONObject(text)
    }
}
