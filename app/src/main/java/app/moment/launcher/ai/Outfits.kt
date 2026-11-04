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
 * The companion's wardrobe as layers. Muse Image edits the plain faceless body to add ONE
 * accessory; the phone keys the green out, then diffs the result against the plain body so only
 * the accessory is kept, as a transparent layer cached per item. Layers stack in Compose on top
 * of whichever body the mood uses, so wearing something already drawn, taking one item off or
 * switching context is instant, and only a never-seen item costs a Muse Image call.
 */
object Outfits {
    private const val SIDE = 360

    /** "A Cowboy Hat!" → "a-cowboy-hat": one cached layer per distinct accessory. */
    fun slug(item: String) = item.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).ifBlank { "item" }

    fun file(ctx: Context, item: String) = File(ctx.filesDir, "layer_${slug(item)}.png")

    fun has(ctx: Context, item: String) = file(ctx, item).exists()

    fun load(ctx: Context, item: String): Bitmap? =
        file(ctx, item).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Removes flattened outfits from before layers existed. */
    fun dropLegacy(ctx: Context) { ctx.filesDir.listFiles { f -> f.name.startsWith("outfit_") }?.forEach { it.delete() } }

    /** Draws one accessory as its own transparent layer and caches it. */
    suspend fun generate(ctx: Context, item: String): Bitmap {
        val plain = withContext(Dispatchers.Default) {
            BitmapFactory.decodeResource(ctx.resources, R.drawable.body).let { Bitmap.createScaledBitmap(it, SIDE, SIDE, true) }
        }
        val png = withContext(Dispatchers.Default) { baseOnWhite(plain) }
        val prompt = "Same exact character, same style, same colors, same size and same position in the frame. " +
            "Do not move, resize or redraw the body. Keep the face blank exactly as it is (no eyes, no mouth) and keep the blush. " +
            "Add $item, clearly visible, worn or held naturally (makeup goes on the cheeks or around where the eyes would be), " +
            "in the same soft flat illustration style. Keep the plain solid pure green background."
        val bytes = MuseClient.editImage(png, prompt)
        return withContext(Dispatchers.Default) {
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val layer = isolate(cutOut(Bitmap.createScaledBitmap(raw, SIDE, SIDE, true)), plain)
            file(ctx, item).outputStream().use { layer.compress(Bitmap.CompressFormat.PNG, 100, it) }
            layer
        }
    }

    /** The plain body with every layer on top, flattened (postcards, previews). */
    fun composite(ctx: Context, items: List<String>): Bitmap {
        val out = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawBitmap(Bitmap.createScaledBitmap(BitmapFactory.decodeResource(ctx.resources, R.drawable.body), SIDE, SIDE, true), 0f, 0f, null)
            items.mapNotNull { load(ctx, it) }.forEach { drawBitmap(it, 0f, 0f, null) }
        }
        return out
    }

    /**
     * Keeps only what the edit added. A pixel is accessory when it is opaque in the edit and no
     * plain-body pixel within 2 px looks like it (tolerates the model nudging the body a little).
     * Then an opening pass drops thin outline rings and specks smaller than 0.1 % of the frame.
     */
    private fun isolate(dressed: Bitmap, plain: Bitmap): Bitmap {
        val w = SIDE; val h = SIDE; val n = w * h
        val d = IntArray(n).also { dressed.getPixels(it, 0, w, 0, 0, w, h) }
        val b = IntArray(n).also { plain.getPixels(it, 0, w, 0, 0, w, h) }
        fun dist(c1: Int, c2: Int): Int {
            if (Color.alpha(c2) < 128) return Int.MAX_VALUE
            val dr = Color.red(c1) - Color.red(c2); val dg = Color.green(c1) - Color.green(c2); val db = Color.blue(c1) - Color.blue(c2)
            return dr * dr + dg * dg + db * db
        }
        val tol = 48 * 48; val r = 2
        var keep = BooleanArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (Color.alpha(d[i]) < 40) continue
            var best = Int.MAX_VALUE
            loop@ for (yy in maxOf(0, y - r)..minOf(h - 1, y + r)) for (xx in maxOf(0, x - r)..minOf(w - 1, x + r)) {
                best = minOf(best, dist(d[i], b[yy * w + xx])); if (best <= tol) break@loop
            }
            keep[i] = best > tol
        }
        // Opening (erode then dilate, radius 1): removes 1-2 px rings left by a shifted outline.
        fun morph(src: BooleanArray, erode: Boolean) = BooleanArray(n) { i ->
            val x = i % w; val y = i / w
            var all = true; var any = false
            for (yy in maxOf(0, y - 1)..minOf(h - 1, y + 1)) for (xx in maxOf(0, x - 1)..minOf(w - 1, x + 1)) {
                val v = src[yy * w + xx]; all = all && v; any = any || v
            }
            if (erode) all else any
        }
        keep = morph(morph(keep, true), false)
        // Drop small islands.
        val seen = BooleanArray(n); val q = ArrayDeque<Int>(); val comp = ArrayList<Int>()
        for (s in 0 until n) {
            if (!keep[s] || seen[s]) continue
            comp.clear(); q.add(s); seen[s] = true
            while (q.isNotEmpty()) {
                val i = q.poll()!!; comp.add(i); val x = i % w; val y = i / w
                for (j in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1))
                    if (j >= 0 && keep[j] && !seen[j]) { seen[j] = true; q.add(j) }
            }
            if (comp.size < n / 1000) comp.forEach { keep[it] = false }
        }
        val out = IntArray(n) { i -> if (keep[i]) d[i] else Color.TRANSPARENT }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** The bundled faceless body on a pure green key, so white accessories survive the cut-out. */
    private fun baseOnWhite(body: Bitmap): ByteArray {
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
        // Enclosed gaps (between a headband and the head, a guitar neck and the body) are not reached by
        // the flood fill: clear any pixel that is unmistakably key green as well.
        for (i in px.indices) if (!bg[i] && key(px[i]) >= 150) bg[i] = true
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
