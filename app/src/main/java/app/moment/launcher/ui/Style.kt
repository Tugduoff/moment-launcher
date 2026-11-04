package app.moment.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily

/** Home screen typography: font, clock weight and size, 12/24 h. Chosen in "Style", saved by the ViewModel. */
data class Style(val font: Int = 0, val clockWeight: Int = 0, val clockSize: Int = 1, val h12: Boolean = false) {
    fun encode() = "$font,$clockWeight,$clockSize,${if (h12) 1 else 0}"

    companion object {
        val FONTS = listOf("DM Sans", "Serif", "Mono", "Classic sans", "Handwritten")
        val WEIGHTS = listOf("Light" to 300, "Regular" to 400, "Bold" to 600)
        val SIZES = listOf("Small" to 64, "Medium" to 88, "Large" to 112)

        fun family(i: Int): FontFamily = when (i) {
            1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; 3 -> FontFamily.SansSerif; 4 -> FontFamily.Cursive
            else -> DmSansFamily
        }

        fun decode(s: String?): Style {
            val v = s?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 4 } ?: return Style()
            return Style(v[0].coerceIn(FONTS.indices), v[1].coerceIn(WEIGHTS.indices), v[2].coerceIn(SIZES.indices), v[3] == 1)
        }

        var current by mutableStateOf(Style())
    }
}
