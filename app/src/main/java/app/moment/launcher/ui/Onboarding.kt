package app.moment.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.moment.launcher.MomentViewModel
import app.moment.launcher.UiState
import app.moment.launcher.data.Look
import app.moment.launcher.data.Mood

private val PAGES = listOf(
    Triple(Mood.AWAKE, "Hi, I'm your companion", "Your home screen will show what this moment needs, and hide the rest."),
    Triple(Mood.THINKING, "I learn your moments", "When I notice a pattern, I suggest a context on a card. Nothing changes without your OK."),
    Triple(Mood.TALKING, "Talk to me", "Type or hold the mic: \"open Spotify\", \"at the library, hide Instagram\", \"put on a hat\"."),
    Triple(Mood.GLASSES, "Two shortcuts", "Swipe sideways for your contexts. Long-press me for my wardrobe."),
)

/** First run: four short pages with the companion, then an offer to make Moment the home screen. */
@Composable
fun Onboarding(vm: MomentViewModel, ui: UiState) {
    if (!ui.showOnboarding) return
    val p = palette(Look.CREAM)
    val ctx = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    val swipeMin = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    Box(Modifier.fillMaxSize().background(p.bg).pointerInput(Unit) {
        // Swipe like a carousel: towards the left for the next page, towards the right to go back.
        // Consuming the drag keeps it from reaching the home screen underneath (which opens Contexts).
        var dx = 0f
        detectHorizontalDragGestures(
            onDragStart = { dx = 0f },
            onDragEnd = {
                if (dx < -swipeMin) { if (page < PAGES.lastIndex) page++ }
                else if (dx > swipeMin && page > 0) page--
            },
        ) { change, d -> dx += d; change.consume() }
    }.systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp)) {
        AnimatedContent(page, transitionSpec = { (fadeIn() + slideInHorizontally { it / 6 }).togetherWith(fadeOut()) }, label = "onboarding") { i ->
            val (mood, title, body) = PAGES[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Companion(mood, 160.dp, speakKey = i)
                Spacer(Modifier.height(28.dp))
                Text(title, 34, p.text, weight = 500, lineHeight = 40)
                Spacer(Modifier.height(12.dp))
                Text(body, 18, p.text2, lineHeight = 26)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PAGES.indices.forEach { Box(Modifier.size(if (it == page) 18.dp else 6.dp, 6.dp).background(if (it == page) p.text else p.stroke, CircleShape)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page < PAGES.lastIndex) {
                    Button("Next", Btn.Primary, p) { page++ }
                    Button("Skip", Btn.Ghost, p) { vm.finishOnboarding() }
                } else {
                    Button("Let's go", Btn.Primary, p) { vm.finishOnboarding() }
                    Button("Set as home screen", Btn.Secondary, p) {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        vm.finishOnboarding()
                    }
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}
