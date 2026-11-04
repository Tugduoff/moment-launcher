package app.moment.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated home screen effects, picked in Style. Everything is procedural (no assets) and drawn
 * on one Canvas from a continuous clock, with small particle counts so the launcher stays light.
 */
object Effects {
    /** 0 = automatic (the context's own look: rain, stars, glow), 1 = none. */
    val NAMES = listOf("Auto", "None", "Neon", "Fire", "Fireworks", "Dripping", "Snow", "Fireflies", "Bubbles",
        "Aurora", "Confetti", "Sakura", "Code rain", "Shooting stars")
}

@Composable
fun EffectLayer(effect: Int) {
    val t by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1e9f }
    }
    val seeds = remember(effect) { val r = Random(effect * 31 + 7); List(80) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        when (Effects.NAMES.getOrNull(effect)) {
            "Neon" -> neon(t)
            "Fire" -> fire(t, seeds)
            "Fireworks" -> fireworks(t)
            "Dripping" -> dripping(t, seeds)
            "Snow" -> snow(t, seeds)
            "Fireflies" -> fireflies(t, seeds)
            "Bubbles" -> bubbles(t, seeds)
            "Aurora" -> aurora(t)
            "Confetti" -> confetti(t, seeds)
            "Sakura" -> sakura(t, seeds)
            "Code rain" -> codeRain(t, seeds)
            "Shooting stars" -> shootingStars(t, seeds)
        }
    }
}

private fun hue(h: Float, s: Float = .85f, v: Float = 1f, a: Float = 1f) =
    Color(android.graphics.Color.HSVToColor((a * 255).toInt().coerceIn(0, 255), floatArrayOf(((h % 360f) + 360f) % 360f, s, v)))

private fun frac(x: Float) = x - floor(x)

/** A glowing tube around the screen edge whose colour slowly cycles and flickers like a sign. */
private fun DrawScope.neon(t: Float) {
    val flicker = if (frac(t * 0.37f) > 0.97f) 0.35f else 1f
    val c = hue(t * 25f + 290f)
    val inset = 14 * density; val r = 36 * density
    for (k in 6 downTo 1) {
        drawRoundRect(c.copy(alpha = 0.06f * flicker * (7 - k)), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
            CornerRadius(r), style = Stroke(k * 5f * density))
    }
    drawRoundRect(Color.White.copy(alpha = 0.85f * flicker), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(r), style = Stroke(1.6f * density))
    // A second tube colour as a soft floor glow.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, hue(t * 25f + 200f, a = 0.22f * flicker)), startY = size.height * .7f))
}

/** Warm flickering glow at the bottom and embers drifting up. */
private fun DrawScope.fire(t: Float, s: List<FloatArray>) {
    val pulse = 0.75f + 0.25f * sin(t * 7f) * sin(t * 3.3f)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF5A1F).copy(alpha = .28f * pulse), Color(0xFFFFB21F).copy(alpha = .42f * pulse)), startY = size.height * .55f))
    s.take(45).forEach { (x, y, sp, k) ->
        val p = frac(y + t * (0.08f + sp * 0.12f))
        val px = x * size.width + sin(t * 2f + k * 9f) * 18 * density * p
        val py = size.height * (1f - p)
        val a = (1f - p) * (0.5f + 0.5f * sin(t * 13f + k * 20f))
        drawCircle(hue(15f + 35f * k, .9f, 1f, a * .9f), (1.5f + 2.5f * k) * density * (1f - p * .5f), Offset(px, py))
    }
}

/** Bursts every ~1.6 s at pseudo-random spots, falling with gravity and fading. */
private fun DrawScope.fireworks(t: Float) {
    val period = 1.6f
    for (lag in 0..2) {
        val n = floor(t / period).toInt() - lag
        if (n < 0) continue
        val age = t - n * period
        if (age > 2.6f) continue
        val r = Random(n * 977)
        val c = Offset(size.width * (0.15f + r.nextFloat() * .7f), size.height * (0.12f + r.nextFloat() * .35f))
        val h = r.nextFloat() * 360f
        val fade = (1f - age / 2.6f).coerceIn(0f, 1f)
        repeat(36) { i ->
            val ang = i / 36f * 2 * PI.toFloat() + r.nextFloat() * .1f
            val v = (110f + r.nextFloat() * 60f) * density
            val d = v * (1f - Math.exp(-age * 2.2).toFloat()) / 2.2f * 2f
            val p = c + Offset(cos(ang) * d, sin(ang) * d + 30f * density * age * age)
            drawCircle(hue(h + i * 4f, .7f, 1f, fade), 2.2f * density * (0.6f + fade * .6f), p)
            drawLine(hue(h + i * 4f, .7f, 1f, fade * .35f), p, p - Offset(cos(ang) * 10 * density, sin(ang) * 10 * density), 1.4f * density, StrokeCap.Round)
        }
    }
}

/** Water gathering along the top edge, stretching into drops and falling. */
private fun DrawScope.dripping(t: Float, s: List<FloatArray>) {
    val water = Color(0xFF9FD8FF)
    drawRect(Brush.verticalGradient(listOf(water.copy(alpha = .35f), Color.Transparent), endY = 26 * density))
    s.take(10).forEachIndexed { i, (x, ph, sp, _) ->
        val cyc = 3.2f + sp * 2.5f
        val p = frac(t / cyc + ph)
        val cx = (0.05f + 0.9f * x) * size.width
        val grow = (p / 0.55f).coerceAtMost(1f)
        if (p < 0.55f) {
            // A bead swelling on the edge.
            val rr = (3f + 5f * grow) * density
            drawOval(water.copy(alpha = .75f), Offset(cx - rr, 0f), Size(rr * 2, rr * 2 + grow * 10 * density))
        } else {
            val f = (p - 0.55f) / 0.45f
            val y = 14 * density + f * f * size.height * 1.1f
            val rr = 5f * density
            drawOval(water.copy(alpha = .8f), Offset(cx - rr, y - rr * 1.6f), Size(rr * 2, rr * 2.6f))
            drawCircle(Color.White.copy(alpha = .7f), rr * .35f, Offset(cx - rr * .3f, y - rr * .4f))
        }
    }
}

private fun DrawScope.snow(t: Float, s: List<FloatArray>) = s.forEach { (x, y, sp, k) ->
    val p = frac(y + t * (0.03f + sp * 0.05f))
    val px = frac(x + sin(t * (0.6f + k) + k * 10f) * 0.02f) * size.width
    drawCircle(Color.White.copy(alpha = .5f + .4f * k), (1.2f + 3f * k) * density, Offset(px, p * size.height))
}

private fun DrawScope.fireflies(t: Float, s: List<FloatArray>) = s.take(22).forEach { (x, y, sp, k) ->
    val px = (x + 0.06f * sin(t * (0.3f + sp * .4f) + k * 7f)) * size.width
    val py = (0.25f + 0.7f * y + 0.04f * cos(t * (0.25f + k * .3f) + sp * 5f)) * size.height
    val glow = (0.5f + 0.5f * sin(t * (1.2f + sp * 2f) + k * 12f)).let { it * it }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF59A).copy(alpha = .55f * glow), Color.Transparent), Offset(px, py), 16 * density), 16 * density, Offset(px, py))
    drawCircle(Color(0xFFFFFBD0).copy(alpha = .9f * glow), 2.2f * density, Offset(px, py))
}

private fun DrawScope.bubbles(t: Float, s: List<FloatArray>) = s.take(24).forEach { (x, y, sp, k) ->
    val p = frac(y + t * (0.04f + sp * 0.06f))
    val r = (6f + 16f * k) * density
    val c = Offset((x + 0.03f * sin(t * 2f + k * 9f)) * size.width, size.height * (1.05f - p * 1.1f))
    drawCircle(Color.White.copy(alpha = .08f), r, c)
    drawCircle(Color.White.copy(alpha = .45f), r, c, style = Stroke(1.2f * density))
    drawCircle(Color.White.copy(alpha = .7f), r * .22f, c + Offset(-r * .4f, -r * .4f))
}

/** Soft ribbons of green and violet light waving across the top of the screen. */
private fun DrawScope.aurora(t: Float) {
    for (band in 0..2) {
        val h = 130f + band * 70f + 20f * sin(t * .2f + band)
        val col = hue(h, .7f, 1f, .22f)
        val steps = 24
        for (i in 0 until steps) {
            val fx = i / steps.toFloat()
            val y = size.height * (0.12f + 0.06f * band + 0.05f * sin(fx * 6f + t * (0.5f + band * .2f)))
            val hgt = size.height * (0.16f + 0.06f * sin(fx * 4f - t * .7f + band))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, col, Color.Transparent), startY = y, endY = y + hgt),
                Offset(fx * size.width, y), Size(size.width / steps + 1, hgt))
        }
    }
}

private fun DrawScope.confetti(t: Float, s: List<FloatArray>) = s.take(50).forEach { (x, y, sp, k) ->
    val p = frac(y + t * (0.05f + sp * 0.07f))
    val c = Offset(frac(x + 0.03f * sin(t * 1.5f + k * 8f)) * size.width, p * size.height)
    rotate(t * 200f * (sp - .5f) + k * 360f, c) {
        drawRect(hue(k * 360f, .75f, 1f, .9f), c - Offset(4 * density, 2 * density), Size(8 * density, 4 * density * (0.3f + abs(sin(t * 5f + k * 6f)))))
    }
}

private fun DrawScope.sakura(t: Float, s: List<FloatArray>) = s.take(28).forEach { (x, y, sp, k) ->
    val p = frac(y + t * (0.025f + sp * 0.035f))
    val c = Offset(frac(x + p * 0.25f + 0.04f * sin(t + k * 10f)) * size.width, p * size.height)
    rotate(t * 60f * (sp + .3f) + k * 360f, c) {
        val w = (7f + 5f * k) * density
        drawOval(Color(0xFFFFC1D6).copy(alpha = .85f), c - Offset(w / 2, w / 4), Size(w, w / 2))
        drawOval(Color(0xFFFF8FB4).copy(alpha = .5f), c - Offset(w / 4, w / 8), Size(w / 2, w / 4))
    }
}

/** Columns of green "code" streaming down: dashes instead of glyphs, so no text layout per frame. */
private fun DrawScope.codeRain(t: Float, s: List<FloatArray>) {
    val cols = 16; val cell = 14 * density
    for (i in 0 until cols) {
        val (_, ph, sp, _) = s[i]
        val headY = frac(ph + t * (0.12f + sp * .15f)) * (size.height + cell * 14)
        for (j in 0 until 14) {
            val y = headY - j * cell
            if (y < 0 || y > size.height) continue
            val a = if (j == 0) 1f else (1f - j / 14f) * .6f
            val wobble = ((floor(t * 8f + j + i * 3f).toInt() * 31) % 3) * 2 * density
            drawRect(if (j == 0) Color(0xFFD7FFD9).copy(alpha = a) else Color(0xFF34E35A).copy(alpha = a),
                Offset((i + .5f) * size.width / cols - 3 * density, y), Size(6 * density - wobble / 2, cell * .55f))
        }
    }
}

private fun DrawScope.shootingStars(t: Float, s: List<FloatArray>) {
    s.take(30).forEach { (x, y, _, k) -> drawCircle(Color.White.copy(alpha = .2f + .4f * k * (0.6f + .4f * sin(t * 2f + k * 20f))), (0.7f + k) * density, Offset(x * size.width, y * size.height * .55f)) }
    for (lane in 0..2) {
        val cyc = 3.5f + lane * 1.3f
        val p = frac(t / cyc + lane * .37f)
        if (p > 0.35f) continue
        val f = p / 0.35f
        val r = Random(floor(t / cyc + lane * .37f).toInt() * 13 + lane)
        val start = Offset(size.width * (0.3f + r.nextFloat() * .7f), size.height * (0.05f + r.nextFloat() * .25f))
        val dir = Offset(-0.8f, 0.45f)
        val head = start + dir * (f * size.width * .6f)
        drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 1f - f)), head - dir * (90 * density), head),
            head - dir * (90 * density), head, 2f * density, StrokeCap.Round)
    }
}
