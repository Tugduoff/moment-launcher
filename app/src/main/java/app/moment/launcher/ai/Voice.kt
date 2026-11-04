package app.moment.launcher.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import app.moment.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Push-to-talk for the companion: records 16 kHz mono PCM while the mic is held, then sends it
 * to Muse Voice Transcribe (muse-voice-transcribe-1.0) with the installed app names as
 * vocabulary keywords and English + French as language hints.
 */
class Voice {
    private val rate = 16_000
    @Volatile private var recording = false
    private val pcm = ByteArrayOutputStream()

    /** Records until [stop] is called; [onLevel] gets a 0..1 loudness for the listening ring. */
    @SuppressLint("MissingPermission")
    suspend fun record(onLevel: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096))
        pcm.reset(); recording = true
        val buf = ShortArray(1024)
        val bytes = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
        rec.startRecording()
        try {
            while (recording) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                bytes.clear()
                for (i in 0 until n) { bytes.putShort(buf[i]); sum += buf[i].toDouble() * buf[i] }
                pcm.write(bytes.array(), 0, n * 2)
                onLevel((sqrt(sum / n) / 6000.0).coerceIn(0.0, 1.0).toFloat())
            }
        } finally { rec.stop(); rec.release() }
    }

    fun stop() { recording = false }

    fun wav(): ByteArray {
        val data = pcm.toByteArray()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }
        return h.array() + data
    }

    fun seconds() = pcm.size() / (rate * 2f)

    companion object {
        suspend fun transcribe(wav: ByteArray, keywords: List<String>): String = withContext(Dispatchers.IO) {
            val boundary = "moment" + System.nanoTime()
            val request = JSONObject()
                .put("model", "muse-voice-transcribe-1.0").put("audioEncoding", "WAV").put("mode", "PUSH_TO_TALK")
                .put("languageBias", JSONArray(listOf("English", "French")))
                .put("keywords", JSONArray(keywords.distinct().take(100)))
            val conn = (URL("https://api.meta.ai/v1/asr/transcribe").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 60_000; doOutput = true
                setRequestProperty("Authorization", "Bearer ${BuildConfig.MODEL_API_KEY}")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            conn.outputStream.use { out ->
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"request\"\r\nContent-Type: application/json\r\n\r\n$request\r\n".toByteArray())
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"voice.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
                out.write(wav); out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val started = System.currentTimeMillis()
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) { Log.e("Muse", "asr HTTP $code $text"); error("Voice $code") }
            Log.d("Muse", "[voice] ${System.currentTimeMillis() - started}ms $text")
            JSONObject(text).optString("transcript").trim()
        }
    }
}
