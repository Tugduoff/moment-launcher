package app.moment.launcher.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.moment.launcher.R
import app.moment.launcher.data.Look

/** Tokens from the Figma file "Moment — launcher mockups" (see docs/design-spec.md). */
data class Palette(
    val bg: Brush,
    val text: Color,
    val text2: Color,
    val pill: Color,
    val stroke: Color,
    val card: Color,
    val accentFill: Color,
    val accentOn: Color,
    val dark: Boolean,
)

private val Ink = Color(0xFF2A2622)
private val Ink2 = Color(0xFF8A837A)
private val Cream = Color(0xFFF7F4EE)
private val Line = Color(0xFFE6DFD4)

fun palette(look: Look): Palette = when (look) {
    Look.CREAM -> Palette(Brush.verticalGradient(listOf(Cream, Cream)), Ink, Ink2, Color.White, Line, Color.White, Ink, Color.White, false)
    Look.STUDY -> Palette(Brush.verticalGradient(listOf(Color(0xFFECEFF4), Color(0xFFECEFF4))), Ink, Ink2, Color.White, Line, Color.White, Ink, Color.White, false)
    Look.CAR -> Palette(Brush.verticalGradient(listOf(Color(0xFF121317), Color(0xFF121317))), Color(0xFFF2F2F5), Color(0xFF8E909B),
        Color(0xFF23252B), Color(0xFF34363E), Color(0xFF23252B), Color(0xFFF2F2F5), Color(0xFF121317), true)
    Look.GAME -> Palette(Brush.linearGradient(listOf(Color(0xFFFF2BD6), Color(0xFF6A2BFF), Color(0xFF00D4FF))),
        Color.White, Color(0xFFE9DEFF), Color.White.copy(alpha = .18f), Color.White.copy(alpha = .30f),
        Color.White.copy(alpha = .22f), Color.White, Color(0xFF2A1060), true)
    Look.RAIN -> Palette(Brush.verticalGradient(listOf(Color(0xFF7E8CA2), Color(0xFFCBD3DE))),
        Color(0xFF1F2A36), Color(0xFF46566A), Color.White.copy(alpha = .40f), Color.White.copy(alpha = .50f),
        Color.White.copy(alpha = .70f), Color(0xFF1F2A36), Color.White, false)
    Look.NIGHT -> Palette(Brush.verticalGradient(listOf(Color(0xFF0E1428), Color(0xFF1C2546))),
        Color(0xFFEEF0FA), Color(0xFF9AA3C2), Color.White.copy(alpha = .10f), Color.White.copy(alpha = .18f),
        Color(0xFF242E52), Color(0xFFEEF0FA), Color(0xFF0E1428), true)
}

const val HINT_DOT = 0xFFFA8C59

@OptIn(ExperimentalTextApi::class)
private fun dm(weight: Int) = Font(
    R.font.dm_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", 14f)),
)

val DmSans = FontFamily(dm(300), dm(400), dm(500))
