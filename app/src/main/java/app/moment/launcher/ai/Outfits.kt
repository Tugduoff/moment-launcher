package app.moment.launcher.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import app.moment.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.ArrayDeque

/**
 * The companion dresses for each new context. Muse Image edits the faceless body to add the
 * accessory Muse Spark picked ("a tiny chef hat"); the phone then cuts the white background out
 * and caches the result. The face is still drawn live on top, so it keeps blinking and talking.
 */
object Outfits {
    private const val SIDE = 360

    fun file(ctx: Context, contextId: String) = File(ctx.filesDir, "outfit_$contextId.png")

    fun load(ctx: Context, contextId: String): Bitmap? =
        file(ctx, contextId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    suspend fun generate(ctx: Context, contextId: String, outfit: String, base: Bitmap? = null): Bitmap {
        val png = withContext(Dispatchers.Default) { baseOnWhite(ctx, base) }
        val prompt = "Same exact character, same style, same colors, same size and same position in the frame. " +
            "Keep the face blank exactly as it is (no eyes, no mouth), keep the blush and keep everything it already wears. " +
            "Add $outfit, clearly visible, worn or held naturally (makeup goes on the cheeks or around where the eyes would be), " +
            "in the same soft flat illustration style. Keep the plain solid pure green background."
        val bytes = MuseClient.editImage(png, prompt)
        return withContext(Dispatchers.Default) {
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val cut = cutOut(Bitmap.createScaledBitmap(raw, SIDE, SIDE, true))
            file(ctx, contextId).outputStream().use { cut.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cut
        }
    }

    /** The bundled faceless body on a pure green key, so white accessories survive the cut-out. */
    private fun baseOnWhite(ctx: Context, base: Bitmap?): ByteArray {
        val body = base ?: BitmapFactory.decodeResource(ctx.resources, R.drawable.body)
        val out = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.rgb(0, 255, 0))
            drawBitmap(Bitmap.createScaledBitmap(body, 1024, 1024, true), 0f, 0f, null)
        }
        return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Flood-fills the green key from the edges to transparent, with a soft, de-spilled fringe. */
    private fun cutOut(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val bg = BooleanArray(w * h)
        // How "key green" a pixel is: 0 = not at all, 255 = pure key.
        fun key(c: Int) = (Color.green(c) - maxOf(Color.red(c), Color.blue(c))).coerceIn(0, 255)
        val q = ArrayDeque<Int>()
        for (x in 0 until w) { q.add(x); q.add((h - 1) * w + x) }
        for (y in 0 until h) { q.add(y * w); q.add(y * w + w - 1) }
        while (q.isNotEmpty()) {
            val i = q.poll()!!
            if (bg[i] || key(px[i]) < 110) continue
            bg[i] = true
            val x = i % w; val y = i / w
            if (x > 0) q.add(i - 1); if (x < w - 1) q.add(i + 1)
            if (y > 0) q.add(i - w); if (y < h - 1) q.add(i + w)
        }
        for (i in px.indices) {
            if (bg[i]) { px[i] = Color.TRANSPARENT; continue }
            val x = i % w; val y = i / w
            val nearBg = (x > 0 && bg[i - 1]) || (x < w - 1 && bg[i + 1]) || (y > 0 && bg[i - w]) || (y < h - 1 && bg[i + w])
            if (nearBg) {
                val c = px[i]; val k = key(c)
                val a = (255 - (k - 30).coerceAtLeast(0) * 3).coerceIn(0, 255)
                val g = minOf(Color.green(c), maxOf(Color.red(c), Color.blue(c)) + 20)
                px[i] = Color.argb(a, Color.red(c), g, Color.blue(c))
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
