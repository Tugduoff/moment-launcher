package app.moment.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.moment.launcher.R
import app.moment.launcher.data.Mood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * The companion is a rig, not a picture.
 * - The body (and its glasses / headphones / seatbelt variants) was generated with Muse Image,
 *   with the face removed (art/generate_companion.sh).
 * - The face is drawn here every frame: blinks on a random schedule (sometimes twice), eyes that
 *   glance around, read, or check mirrors, a mouth that moves while it talks, z's that drift up
 *   while it sleeps, thinking dots, breathing squash-and-stretch, nodding to the beat, a hop when tapped.
 * Face anchors are measured from the Muse Image sprites, in units of the sprite's side.
 */

private val INK = Color(0xFF4A3024)
private const val EYE_L_X = 0.378f
private const val EYE_R_X = 0.606f
private const val EYE_Y = 0.598f
private const val EYE_W = 0.061f
private const val EYE_H = 0.075f
private const val MOUTH_X = 0.493f
private const val MOUTH_Y = 0.672f

private fun body(m: Mood) = when (m) {
    Mood.GLASSES -> R.drawable.body_glasses
    Mood.SEATBELT -> R.drawable.body_seatbelt
    Mood.HEADPHONES -> R.drawable.body_headphones
    else -> R.drawable.body
}

/** Per-instance animation state, advanced once per frame. */
private class Rig {
    val rnd = Random(System.nanoTime())
    var t = 0f
    var nextBlink = 1.5f
    var blinkStart = -10f
    var gazeX = 0f; var gazeY = 0f
    var gazeTX = 0f; var gazeTY = 0f
    var nextGaze = 1f
    var readLine = 0
    var moodSince = 0f
    var hopStart = -10f
    var speakStart = -10f

    fun step(now: Float, mood: Mood) {
        val dt = if (t == 0f) 0f else min(0.05f, now - t)
        t = now
        // Blinks: random 2.4–5.8 s apart, 25% chance of a quick double blink.
        if (t >= nextBlink) {
            blinkStart = t
            nextBlink = t + if (rnd.nextFloat() < 0.25f) 0.32f else 2.4f + rnd.nextFloat() * 3.4f
        }
        // Gaze: idle glances; reading sweeps (glasses); mirror checks (driving); up-right (thinking).
        if (t >= nextGaze) {
            when (mood) {
                Mood.GLASSES -> {
                    readLine = (readLine + 1) % 7
                    gazeTX = if (readLine == 6) -0.6f else -0.6f + readLine * 0.24f; gazeTY = 0.25f
                    nextGaze = t + 0.45f
                }
                Mood.SEATBELT -> {
                    gazeTX = listOf(0f, 0f, -1f, 0f, 1f)[rnd.nextInt(5)]; gazeTY = -0.1f
                    nextGaze = t + 1.4f + rnd.nextFloat() * 1.8f
                }
                Mood.THINKING -> { gazeTX = 0.8f; gazeTY = -0.9f; nextGaze = t + 0.6f }
                else -> {
                    val still = rnd.nextFloat() < 0.45f
                    gazeTX = if (still) 0f else rnd.nextFloat() * 2f - 1f
                    gazeTY = if (still) 0f else (rnd.nextFloat() * 2f - 1f) * 0.5f
                    nextGaze = t + 1.2f + rnd.nextFloat() * 2.8f
                }
            }
        }
        // Eyes move fast but not instantly (a saccade lasts ~80 ms).
        val k = 1f - exp(-dt * 22f)
        gazeX += (gazeTX - gazeX) * k; gazeY += (gazeTY - gazeY) * k
    }

    /** 0 = open, 1 = closed. 70 ms close, 50 ms hold, 110 ms open. */
    fun blink(): Float {
        val e = t - blinkStart
        return when {
            e < 0f -> 0f
            e < 0.07f -> e / 0.07f
            e < 0.12f -> 1f
            e < 0.23f -> 1f - (e - 0.12f) / 0.11f
            else -> 0f
        }
    }

    fun hop(): Float = (t - hopStart).let { if (it in 0f..0.5f) it / 0.5f else -1f }
}

@Composable
fun Companion(mood: Mood, size: Dp = 96.dp, modifier: Modifier = Modifier, speakKey: Int = 0, hopKey: Int = 0) {
    val rig = remember { Rig() }
    var frame by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> frame = (now - start) / 1e9f }
    }
    // Pop when the mood changes: a small squash that springs back.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        rig.moodSince = rig.t
        pop.snapTo(0.88f)
        pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(hopKey) { if (hopKey > 0) rig.hopStart = rig.t }
    LaunchedEffect(speakKey) { if (speakKey > 0) rig.speakStart = rig.t }

    rig.step(frame, mood)
    val t = rig.t
    val asleep = mood == Mood.ASLEEP || mood == Mood.SLEEPY

    // Breathing: inhale stretches up and narrows a touch; asleep breathes slower and deeper.
    val period = if (asleep) 3.6f else 2.8f
    val br = sin(2 * PI * t / period).toFloat()
    val breatheY = 1f + br * (if (asleep) 0.028f else 0.014f)
    val breatheX = 1f - br * (if (asleep) 0.014f else 0.007f)

    // Idle bob, nodding to ~112 bpm with headphones, a slow sway while thinking.
    val beat = t * 112f / 60f
    val bob = when (mood) {
        Mood.HEADPHONES -> -abs(sin(PI * beat)).toFloat() * 4f
        Mood.THINKING -> sin(2 * PI * t / 1.1f).toFloat() * 1.5f
        Mood.ASLEEP, Mood.SLEEPY -> 0f
        else -> -(0.5f + 0.5f * sin(2 * PI * t / 1.9f).toFloat()) * 2f
    }
    val tilt = when (mood) {
        Mood.HEADPHONES -> sin(PI * beat).toFloat() * 4f
        Mood.THINKING -> 4f + sin(2 * PI * t / 2.2f).toFloat() * 2f
        Mood.SLEEPY, Mood.ASLEEP -> sin(2 * PI * t / 7f).toFloat() * 1.5f
        else -> sin(2 * PI * t / 5.3f).toFloat() * 1.2f
    }

    // Tap hop: anticipation squash, jump, landing squash.
    val hp = rig.hop()
    var hopY = 0f; var hopSX = 1f; var hopSY = 1f
    if (hp >= 0f) {
        when {
            hp < 0.15f -> { val q = hp / 0.15f; hopSX = 1f + 0.08f * q; hopSY = 1f - 0.08f * q }
            hp < 0.75f -> { val q = (hp - 0.15f) / 0.6f; hopY = -16f * 4f * q * (1 - q); hopSX = 0.96f; hopSY = 1.05f }
            else -> { val q = (hp - 0.75f) / 0.25f; val s = sin(PI * q).toFloat(); hopSX = 1f + 0.07f * s; hopSY = 1f - 0.07f * s }
        }
    }
    val happy = hp in 0f..1f

    Box(modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0.92f)
                scaleX = breatheX * pop.value * hopSX
                scaleY = breatheY * (2f - pop.value) * hopSY
                translationY = (bob + hopY) * density
                rotationZ = tilt
            }
        ) {
            Image(painterResource(body(mood)), contentDescription = "Companion, ${mood.name.lowercase()}", Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) { drawFace(rig, mood, happy, br) }
        }
        Canvas(Modifier.fillMaxSize()) {
            when (mood) {
                Mood.ASLEEP -> drawZs(t)
                Mood.THINKING -> drawDots(t)
                Mood.SLEEPY -> drawDrop(t)
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawFace(rig: Rig, mood: Mood, happy: Boolean, breath: Float) {
    val u = size.width
    val sw = u * 0.013f
    val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val t = rig.t

    fun closedArc(cx: Float, cy: Float, up: Boolean) {
        val w = u * 0.085f; val d = u * 0.022f
        val p = Path().apply {
            moveTo(cx - w / 2, cy)
            quadraticTo(cx, cy + if (up) -d * 2 else d * 2, cx + w / 2, cy)
        }
        drawPath(p, INK, style = stroke)
    }

    fun openEye(cx: Float, cy: Float, blink: Float) {
        val gx = rig.gazeX * u * 0.012f; val gy = rig.gazeY * u * 0.012f
        val w = u * EYE_W; val h = max(u * 0.012f, u * EYE_H * (1f - blink))
        if (blink > 0.85f) { closedArc(cx + gx, cy + gy + u * 0.01f, up = false); return }
        drawOval(INK, Offset(cx + gx - w / 2, cy + gy - h / 2), Size(w, h))
        if (blink < 0.4f) drawCircle(Color.White.copy(alpha = 0.9f), u * 0.011f, Offset(cx + gx + w * 0.18f, cy + gy - h * 0.2f))
    }

    val lx = u * EYE_L_X; val rx = u * EYE_R_X; val ey = u * EYE_Y
    val mx = u * MOUTH_X; val my = u * MOUTH_Y

    // Eyes.
    when {
        happy -> { closedArc(lx, ey, up = true); closedArc(rx, ey, up = true) }
        mood == Mood.ASLEEP -> { closedArc(lx, ey + u * 0.01f, up = false); closedArc(rx, ey + u * 0.01f, up = false) }
        mood == Mood.SLEEPY -> { closedArc(lx, ey, up = true); closedArc(rx, ey, up = true) }
        else -> { val b = rig.blink(); openEye(lx, ey, b); openEye(rx, ey, b) }
    }

    // Mouth.
    fun smile(width: Float, depth: Float) {
        val p = Path().apply { moveTo(mx - width / 2, my); quadraticTo(mx, my + depth * 2, mx + width / 2, my) }
        drawPath(p, INK, style = stroke)
    }
    val eyesOpen = mood in setOf(Mood.AWAKE, Mood.TALKING, Mood.GLASSES, Mood.SEATBELT)
    val talkingNow = (mood == Mood.TALKING && t - rig.moodSince < 2.4f) || (eyesOpen && t - rig.speakStart < 1.8f)
    when {
        happy || mood == Mood.HEADPHONES -> {
            val w = u * 0.085f; val d = u * 0.04f
            val p = Path().apply { moveTo(mx - w / 2, my - d * 0.2f); quadraticTo(mx, my + d * 1.6f, mx + w / 2, my - d * 0.2f); close() }
            drawPath(p, INK)
        }
        talkingNow -> {
            // Syllable-like rhythm from layered sines.
            val o = 0.35f + 0.65f * abs(sin(t * 11f) * 0.7f + sin(t * 17.3f) * 0.3f)
            val w = u * (0.04f + 0.012f * o); val h = u * (0.015f + 0.04f * o)
            drawOval(INK, Offset(mx - w / 2, my - h / 2 + u * 0.005f), Size(w, h))
        }
        mood == Mood.TALKING -> drawOval(INK, Offset(mx - u * 0.02f, my - u * 0.012f), Size(u * 0.04f, u * 0.034f))
        mood == Mood.ASLEEP -> {
            val s = 1f + breath * 0.35f
            drawOval(INK.copy(alpha = 0.7f), Offset(mx - u * 0.012f * s, my), Size(u * 0.024f * s, u * 0.016f * s))
        }
        mood == Mood.THINKING -> drawLine(INK, Offset(mx - u * 0.01f, my + u * 0.008f), Offset(mx + u * 0.03f, my), sw, StrokeCap.Round)
        mood == Mood.GLASSES -> smile(u * 0.05f, u * 0.006f)
        else -> smile(u * 0.07f, u * 0.014f)
    }
}

/** Three z's drifting up and to the right, growing and fading. */
private fun DrawScope.drawZs(t: Float) {
    val u = size.width
    for (k in 0 until 3) {
        val p = ((t / 3.3f) + k / 3f) % 1f
        val x = u * (0.66f + 0.2f * p) + sin(p * 6f + k) * u * 0.015f
        val y = u * (0.30f - 0.28f * p)
        val s = u * (0.04f + 0.05f * p)
        val a = when { p < 0.15f -> p / 0.15f; p > 0.7f -> (1f - p) / 0.3f; else -> 1f } * 0.75f
        val path = Path().apply { moveTo(x, y); lineTo(x + s, y); lineTo(x, y + s); lineTo(x + s, y + s) }
        drawPath(path, Color(0xFF9A8F80).copy(alpha = a), style = Stroke(u * 0.016f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Thinking dots: appear one after another, bounce, then reset. */
private fun DrawScope.drawDots(t: Float) {
    val u = size.width
    val cycle = (t % 1.6f) / 1.6f
    for (k in 0 until 3) {
        val on = ((cycle * 4f) - k).coerceIn(0f, 1f)
        val lift = sin(PI * on).toFloat() * u * 0.02f
        drawCircle(Color(0xFF9A8F80).copy(alpha = 0.25f + 0.7f * on), u * 0.022f, Offset(u * (0.72f + k * 0.075f), u * (0.2f - k * 0.04f) - lift))
    }
}

/** A raindrop sitting on the leaf, swelling, then sliding off and starting again. */
private fun DrawScope.drawDrop(t: Float) {
    val u = size.width
    val p = (t % 4.5f) / 4.5f
    val grow = min(1f, p / 0.6f)
    val fall = if (p > 0.75f) (p - 0.75f) / 0.25f else 0f
    val cx = u * 0.66f; val cy = u * 0.25f + fall * fall * u * 0.5f
    val r = u * 0.022f * (0.6f + 0.4f * grow)
    val a = if (fall > 0.6f) (1f - fall) / 0.4f else 1f
    val path = Path().apply {
        moveTo(cx, cy - r * 2.1f)
        cubicTo(cx + r * 0.3f, cy - r * 1.2f, cx + r, cy - r * 0.4f, cx + r, cy)
        cubicTo(cx + r, cy + r * 1.3f, cx - r, cy + r * 1.3f, cx - r, cy)
        cubicTo(cx - r, cy - r * 0.4f, cx - r * 0.3f, cy - r * 1.2f, cx, cy - r * 2.1f)
        close()
    }
    drawPath(path, Color(0xFF8EC5F2).copy(alpha = a))
    drawCircle(Color.White.copy(alpha = 0.7f * a), r * 0.3f, Offset(cx - r * 0.35f, cy - r * 0.2f))
}
