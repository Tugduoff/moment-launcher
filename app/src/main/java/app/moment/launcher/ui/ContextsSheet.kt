package app.moment.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.moment.launcher.MomentViewModel
import app.moment.launcher.UiState
import app.moment.launcher.data.ContextEngine
import app.moment.launcher.data.Look
import app.moment.launcher.data.MomentContext

private const val PIN = "M12 21C12 21 5 14.5 5 9.5A7 7 0 0 1 19 9.5C19 14.5 12 21 12 21Z M12 12A2.5 2.5 0 1 0 12 7A2.5 2.5 0 1 0 12 12Z"

/** What each built-in context makes the companion wear (drawn by Muse Image, bundled with the app). */
private val BUILT_IN_OUTFIT = mapOf(
    "study" to "round glasses", "driving" to "a seatbelt", "game" to "neon headphones",
    "rain" to "a raindrop",
)

/** "17 bd Jourdan · weekdays 11–14", "Car Bluetooth connected", "Headphones · 19:00–02:00". */
internal fun triggerSummary(c: MomentContext): String {
    val t = c.trigger
    val parts = mutableListOf<String>()
    when (t.bluetooth) {
        "car" -> parts += "Car Bluetooth connected"
        "headphones" -> parts += "Headphones"
    }
    t.place?.let { parts += if (it.startsWith("Away from home")) "Away from home" else it }
    val hours = if (t.startHour != null && t.endHour != null) {
        if (t.startHour <= t.endHour) "${t.startHour}–${t.endHour}" else "%02d:00–%02d:00".format(t.startHour, t.endHour)
    } else null
    val day = when {
        t.weekdaysOnly -> "weekdays"
        t.place != null && hours == null -> "every day"
        else -> null
    }
    val whenText = listOfNotNull(day, hours).joinToString(" ")
    if (whenText.isNotBlank()) parts += whenText
    if (c.id == "rain") parts += "When it rains"
    return parts.joinToString(" · ").ifBlank { "Anytime" }
}

/** The saved contexts, plus the built-in ones. Opened by typing "contexts" in search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextsSheet(vm: MomentViewModel, ui: UiState) {
    val p = palette(Look.CREAM)
    val all = listOfNotNull(ui.signals?.let { ContextEngine.home(it) }) + ui.contexts + listOf(
        ContextEngine.study(emptyList()), ContextEngine.driving, ContextEngine.game, ContextEngine.rain, ContextEngine.nightOut,
    ).filter { b -> ui.contexts.none { it.id == b.id } }
    val swipeMin = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    // A full page that slides in from the right; swipe it back to the right (or press back) to close.
    androidx.compose.animation.AnimatedVisibility(
        ui.showContexts,
        enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it } + androidx.compose.animation.fadeOut(),
    ) {
        androidx.activity.compose.BackHandler { vm.openContexts(false) }
        Box(
            Modifier.fillMaxSize().background(Color(0xFFF7F4EE)).pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx > swipeMin) vm.openContexts(false) },
                ) { change, d -> dx += d; change.consume() }
            }.statusBarsPadding(),
        ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 28.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Contexts", 34, p.text, weight = 500)
                    Text(
                        if (ui.contexts.isEmpty()) "None saved yet, ${all.size} built in" else "${ui.contexts.size} saved on this phone, ${all.size - ui.contexts.size} built in",
                        14, p.text2,
                    )
                }
                Box(
                    Modifier.size(44.dp).background(p.pill, CircleShape).border(1.dp, p.stroke, CircleShape)
                        .clickable { vm.openContexts(false) },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.CLOSE, p.text, 20.dp) }
            }

            Text(
                if (ui.pinnedId != null) "Picked by hand. Tap it again to go back to automatic." else "Tap one to use it now.",
                14, p.text2,
            )
            all.forEach { c -> ContextCard(vm, c, p, active = ui.context?.id == c.id) }

            val dashed = RoundedCornerShape(22.dp)
            Row(
                Modifier.fillMaxWidth().height(48.dp).drawBehindDashed(p.text2, 22.dp).clickable { vm.openContexts(false) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                LineIcon(Icons.SPARKLE, p.text2, 16.dp)
                Spacer(Modifier.width(8.dp))
                Text("New context: just type a sentence", 14, p.text2)
            }
            Text(
                "Swipe sideways on the home screen to open this list.", 13, p.text2, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        }
    }
}

@Composable
private fun ContextCard(vm: MomentViewModel, c: MomentContext, p: Palette, active: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    val outfit = c.outfit.ifBlank { BUILT_IN_OUTFIT[c.id].orEmpty() }
    val chip = if (active) "now" else if (c.builtIn) "built in" else if (c.chip.contains("·")) c.chip.substringAfter("·").trim() else ""
    Row(
        Modifier.fillMaxWidth().background(p.card, shape).border(if (active) 2.dp else 1.dp, if (active) p.text else p.stroke, shape)
            .clickable { vm.useContext(c.id) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).background(Color(0xFFF7F4EE), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Companion(c.mood.takeIf { it != app.moment.launcher.data.Mood.ASLEEP } ?: app.moment.launcher.data.Mood.AWAKE, 56.dp, outfit = vm.outfitFor(c.id))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, 19, p.text, weight = 500, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (chip.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    val cs = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.border(1.dp, p.stroke, cs).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(5.dp).background(p.text, CircleShape)); Spacer(Modifier.width(5.dp))
                        Text(chip, 12, p.text, weight = 500, maxLines = 1)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(PIN, p.text2, 14.dp, stroke = 2f)
                Spacer(Modifier.width(5.dp))
                Text(triggerSummary(c), 14, p.text2, maxLines = 1)
            }
            if (outfit.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(Icons.SPARKLE, Color(HINT_DOT), 14.dp)
                Spacer(Modifier.width(5.dp))
                Text("Wears $outfit", 14, p.text, maxLines = 1)
            }
        }
        LineIcon(Icons.CHEVRON, p.text, modifier = Modifier.alpha(.5f))
    }
}
