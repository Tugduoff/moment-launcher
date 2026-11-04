package app.moment.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.moment.launcher.MomentViewModel
import app.moment.launcher.UiState
import app.moment.launcher.data.Look
import app.moment.launcher.data.Mood

private const val ARROW = "M5 12H19 M13 6L19 12L13 18"

private val SUGGESTIONS = listOf(
    "Beret" to "a beret",
    "Sunglasses" to "sunglasses",
    "Scarf" to "a cozy scarf",
    "Ukulele" to "a ukulele it is playing",
    "Pirate hat" to "a pirate hat",
    "Bow tie" to "a bow tie",
)

/** "a tiny chef hat" -> "Tiny chef hat", for chips. */
internal fun accessoryLabel(raw: String): String {
    val t = raw.trim().removePrefix("a ").removePrefix("an ").removePrefix("the ").removeSuffix(" it is playing")
    return t.replaceFirstChar { it.uppercase() }
}

/**
 * Long-press the companion: what it wears right now (Muse Image edits stack), things to try on,
 * and a free-text "Dress me up..." that goes through Muse Spark like any other sentence.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WardrobeSheet(vm: MomentViewModel, ui: UiState) {
    if (!ui.showWardrobe) return
    val p = palette(Look.CREAM)
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ModalBottomSheet(
        onDismissRequest = { vm.openWardrobe(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.card, shape = shape,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 12.dp).width(36.dp).height(4.dp).background(p.stroke, CircleShape))
        },
    ) {
        var draft by remember { mutableStateOf("") }
        val busy = ui.dressing || ui.thinking
        fun send() { if (draft.isNotBlank() && !busy) { vm.ask(draft); draft = "" } }

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()
                .padding(horizontal = 28.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The companion, large, on a soft tile.
            Box(
                Modifier.fillMaxWidth().height(190.dp).background(p.bg2(), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Companion(
                    if (busy) Mood.THINKING else Mood.AWAKE, 168.dp,
                    speakKey = ui.speak, hopKey = ui.hop, outfit = ui.outfit,
                )
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).background(p.pill, RoundedCornerShape(16.dp))
                        .border(1.dp, p.stroke, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineIcon(Icons.SPARKLE, p.text, 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Muse Image", 13, p.text, weight = 500)
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Wardrobe", 28, p.text, weight = 500)
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                        LineIcon(Icons.SPARKLE, p.text2, 14.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Dressing…", 14, p.text2)
                    }
                }
                Text("Share", 14, p.text, weight = 500, underline = true, modifier = Modifier.padding(top = 10.dp, end = 14.dp).clickable { vm.openWardrobe(false); vm.sharePostcard() })
                Text("Contexts", 14, p.text2, weight = 500, modifier = Modifier.padding(top = 10.dp).clickable { vm.openContexts(true) })
            }

            // What it wears now.
            Text("Wearing", 13, p.text2)
            if (ui.wardrobe.isEmpty()) {
                Text("Nothing yet. Pick something below.", 14, p.text2)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.wardrobe.forEach { item ->
                        PillChip(p, Modifier.alpha(if (busy) .5f else 1f)) {
                            Text(accessoryLabel(item), 14, p.text, weight = 500, maxLines = 1)
                            Spacer(Modifier.width(6.dp))
                            LineIcon(
                                Icons.CLOSE, p.text2, 14.dp,
                                modifier = Modifier.clickable(enabled = !busy) { vm.removeAccessory(item) },
                            )
                        }
                    }
                    Text(
                        "Take all off", 14, p.text2, weight = 500,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp).clickable(enabled = !busy) { vm.undress() },
                    )
                }
            }

            Text("Try on", 13, p.text2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.forEach { (label, prompt) ->
                    PillChip(p, Modifier.alpha(if (busy) .5f else 1f).clickable(enabled = !busy) { vm.wear(prompt) }) {
                        LineIcon(Icons.SPARKLE, p.text2, 14.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(label, 14, p.text, weight = 500, maxLines = 1)
                    }
                }
            }

            // Free text, handled by Muse Spark.
            val field = RoundedCornerShape(26.dp)
            Row(
                Modifier.fillMaxWidth().height(52.dp).background(p.pill, field).border(1.dp, p.stroke, field).padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) Text("Dress me up...", 16, p.text2)
                    BasicTextField(
                        draft, { draft = it }, singleLine = true,
                        textStyle = TextStyle(fontFamily = DmSans, fontSize = 16.sp, color = p.text),
                        cursorBrush = SolidColor(p.text),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    Modifier.size(40.dp).background(p.accentFill, CircleShape).clickable(enabled = !busy) { send() },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(ARROW, p.accentOn, 20.dp) }
            }
        }
    }
}

/** Soft tile colour behind the companion: the screen cream, a notch darker than the white sheet. */
private fun app.moment.launcher.ui.Palette.bg2() = Color(0xFFF7F4EE)

@Composable
internal fun PillChip(p: Palette, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.background(p.pill, shape).border(1.dp, p.stroke, shape).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
