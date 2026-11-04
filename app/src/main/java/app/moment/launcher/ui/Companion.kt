package app.moment.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.moment.launcher.R
import app.moment.launcher.data.Mood

/** Expressions generated with Muse Image (see art/generate_companion.sh). */
private fun sprite(m: Mood) = when (m) {
    Mood.ASLEEP -> R.drawable.mascot_asleep
    Mood.AWAKE -> R.drawable.mascot_awake
    Mood.TALKING -> R.drawable.mascot_talking
    Mood.THINKING -> R.drawable.mascot_thinking
    Mood.GLASSES -> R.drawable.mascot_glasses
    Mood.SEATBELT -> R.drawable.mascot_seatbelt
    Mood.HEADPHONES -> R.drawable.mascot_headphones
    Mood.SLEEPY -> R.drawable.mascot_sleepy
}

@Composable
fun Companion(mood: Mood, size: Dp = 96.dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "life")
    val asleep = mood == Mood.ASLEEP || mood == Mood.SLEEPY
    // Asleep: slow breathing. Awake: a gentle bob. Thinking: a faster wobble.
    val breathe by t.animateFloat(1f, if (asleep) 1.035f else 1.0f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val bob by t.animateFloat(0f, if (asleep) 0f else if (mood == Mood.THINKING) -5f else -3f,
        infiniteRepeatable(tween(if (mood == Mood.THINKING) 500 else 1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")

    AnimatedContent(
        targetState = mood,
        transitionSpec = {
            (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f), initialScale = 0.86f))
                .togetherWith(fadeOut(tween(120)))
        },
        label = "mood",
        modifier = modifier,
    ) { m ->
        Image(
            painter = painterResource(sprite(m)),
            contentDescription = "Companion, ${m.name.lowercase()}",
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleX = breathe; scaleY = breathe
                    translationY = bob * density
                },
        )
    }
}
