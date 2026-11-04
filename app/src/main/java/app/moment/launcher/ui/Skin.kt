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
data class Skin(val body: Int = 0, val leaf: Int = 0, val eyes: Int = 0) {
    val isDefault get() = body == 0 && leaf == 0

    fun encode() = "$body,$leaf,$eyes"

    companion object {
        /** Name and target hue in degrees; index 0 is the original drawing. */
        val BODIES = listOf("Peach" to -1f, "Lemon" to 52f, "Mint" to 150f, "Sky" to 205f, "Lavender" to 262f, "Rose" to 335f, "Coral" to 8f)
        val LEAVES = listOf("Green" to -1f, "Autumn" to 28f, "Sakura" to 335f, "Ocean" to 195f, "Plum" to 285f)
        val EYES = listOf("Cocoa" to Color(0xFF4A3024), "Ink" to Color(0xFF1E1E24), "Ocean" to Color(0xFF1F4E79), "Forest" to Color(0xFF2E5E3A))

        fun decode(s: String?): Skin = s?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 }
            ?.let { (b, l, e) -> Skin(b.coerceIn(BODIES.indices), l.coerceIn(LEAVES.indices), e.coerceIn(EYES.indices)) } ?: Skin()

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
