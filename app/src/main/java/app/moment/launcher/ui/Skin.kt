package app.moment.launcher.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The companion's colours, chosen in the wardrobe. The bundled bodies are recoloured on the
 * phone (hue shift of the body and the leaf only; blush, outlines and accessories stay), so
 * every mood body and every outfit layer keeps working.
 */
data class Skin(val body: Int = 0, val leaf: Int = 0, val eyes: Int = 0, val eyeStyle: Int = 0, val mouth: Int = 0) {
    val isDefault get() = body == 0 && leaf == 0

    fun encode() = "$body,$leaf,$eyes,$eyeStyle,$mouth"

    companion object {
        /** Name and target hue in degrees; index 0 is the original drawing. */
        val BODIES = listOf("Peach" to -1f, "Lemon" to 52f, "Mint" to 150f, "Sky" to 205f, "Lavender" to 262f, "Rose" to 335f, "Coral" to 8f)
        val LEAVES = listOf("Green" to -1f, "Autumn" to 28f, "Sakura" to 335f, "Ocean" to 195f, "Plum" to 285f)
        val EYES = listOf("Cocoa" to Color(0xFF4A3024), "Ink" to Color(0xFF1E1E24), "Ocean" to Color(0xFF1F4E79), "Forest" to Color(0xFF2E5E3A))

        val EYE_STYLES = listOf("Round", "Sparkly", "Dots", "Dreamy")
        val MOUTHS = listOf("Smile", "Cat", "Grin")

        fun decode(s: String?): Skin {
            val v = s?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return Skin()
            if (v.size < 3) return Skin()
            return Skin(v[0].coerceIn(BODIES.indices), v[1].coerceIn(LEAVES.indices), v[2].coerceIn(EYES.indices),
                v.getOrElse(3) { 0 }.coerceIn(EYE_STYLES.indices), v.getOrElse(4) { 0 }.coerceIn(MOUTHS.indices))
        }

        /** Accessory colours: name and hue; index 0 keeps the drawing as Muse Image made it. */
        val TINTS = listOf("Original" to -1f, "Red" to 2f, "Orange" to 28f, "Yellow" to 50f, "Green" to 125f, "Blue" to 212f, "Purple" to 270f, "Pink" to 325f)
        private val tintCache = mutableMapOf<Pair<Int, Int>, Bitmap>()

        /**
         * Gives a layer a new main colour: finds its dominant hue (circular mean of the coloured
         * pixels) and rotates every coloured pixel by the same amount, so wood grain, shading and
         * highlights survive.
         */
        fun tint(key: Int, src: Bitmap, tint: Int): Bitmap {
            val target = TINTS.getOrNull(tint)?.second ?: -1f
            if (target < 0) return src
            return tintCache.getOrPut(key to tint) {
                val w = src.width; val h = src.height
                val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
                val hsv = FloatArray(3); var sx = 0.0; var sy = 0.0
                for (c in px) {
                    if (AColor.alpha(c) < 128) continue
                    AColor.colorToHSV(c, hsv); if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue
                    val r = Math.toRadians(hsv[0].toDouble()); sx += Math.cos(r) * hsv[1]; sy += Math.sin(r) * hsv[1]
                }
                val dominant = ((Math.toDegrees(Math.atan2(sy, sx)) + 360) % 360).toFloat()
                for (i in px.indices) {
                    val c = px[i]; if (AColor.alpha(c) == 0) continue
                    AColor.colorToHSV(c, hsv); if (hsv[1] < 0.15f) continue
                    hsv[0] = (hsv[0] + target - dominant + 720f) % 360f
                    px[i] = AColor.HSVToColor(AColor.alpha(c), hsv)
                }
                Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            }
        }

        /** The skin in use; Compose reads it, so changing it redraws every companion at once. */
        var current by mutableStateOf(Skin())

        private const val BODY_HUE = 32f // the drawing's peach
        private const val LEAF_HUE = 88f // the drawing's green
        private val cache = mutableMapOf<Pair<Int, Skin>, Bitmap>()

        fun recolor(key: Int, src: Bitmap, skin: Skin): Bitmap {
            if (skin.isDefault) return src
            return cache.getOrPut(key to skin) {
                val w = src.width; val h = src.height
                val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
                val hsv = FloatArray(3)
                val bodyShift = BODIES[skin.body].second.let { if (it < 0) 0f else it - BODY_HUE }
                val leafShift = LEAVES[skin.leaf].second.let { if (it < 0) 0f else it - LEAF_HUE }
                for (i in px.indices) {
                    val c = px[i]; val a = AColor.alpha(c)
                    if (a == 0) continue
                    AColor.colorToHSV(c, hsv)
                    if (hsv[1] < 0.22f) continue
                    val shift = when (hsv[0]) { in 18f..58f -> bodyShift; in 62f..170f -> leafShift; else -> 0f }
                    if (shift == 0f) continue
                    hsv[0] = (hsv[0] + shift + 360f) % 360f
                    px[i] = AColor.HSVToColor(a, hsv)
                }
                Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            }
        }
    }
}
