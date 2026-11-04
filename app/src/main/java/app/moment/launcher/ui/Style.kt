package app.moment.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily

/** Home screen typography: font, clock weight and size, 12/24 h. Chosen in "Style", saved by the ViewModel. */
/** [clockFont] 0 = same as the app, otherwise 1 + an index in [FONTS]. */
data class Style(val font: Int = 0, val clockWeight: Int = 0, val clockSize: Int = 1, val h12: Boolean = false, val effect: Int = 0, val clockFont: Int = 0) {
    fun encode() = "$font,$clockWeight,$clockSize,${if (h12) 1 else 0},$effect,$clockFont"

    companion object {
        val FONTS = listOf("DM Sans", "Serif", "Mono", "Classic sans", "Handwritten", "Condensed", "Casual", "Small caps", "Serif mono")
        val CLOCK_FONTS = listOf("Same as app") + FONTS
        val WEIGHTS = listOf("Light" to 300, "Regular" to 400, "Bold" to 600)
        val SIZES = listOf("Small" to 64, "Medium" to 88, "Large" to 112)

        fun family(i: Int): FontFamily = when (i) {
            1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; 3 -> FontFamily.SansSerif; 4 -> FontFamily.Cursive
            5 -> system("sans-serif-condensed"); 6 -> system("casual"); 7 -> system("sans-serif-smallcaps"); 8 -> system("serif-monospace")
            else -> DmSansFamily
        }

        private fun system(name: String) =
            FontFamily(androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.create(name, android.graphics.Typeface.NORMAL)))

        /** The clock's own font (falls back to the app font). */
        fun clockFamily(s: Style): FontFamily = if (s.clockFont == 0) family(s.font) else family(s.clockFont - 1)

        fun decode(s: String?): Style {
            val v = s?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size >= 4 } ?: return Style()
            return Style(v[0].coerceIn(FONTS.indices), v[1].coerceIn(WEIGHTS.indices), v[2].coerceIn(SIZES.indices), v[3] == 1,
                v.getOrElse(4) { 0 }.coerceIn(Effects.NAMES.indices), v.getOrElse(5) { 0 }.coerceIn(CLOCK_FONTS.indices))
        }

        var current by mutableStateOf(Style())
    }
}
