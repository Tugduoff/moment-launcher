package app.moment.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.moment.launcher.MomentViewModel
import app.moment.launcher.UiState
import app.moment.launcher.data.Look

private val SHEET = Color(0xFFF7F4EE)

@Composable
private fun Field(value: String, hint: String, p: Palette, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.fillMaxWidth().background(p.card, shape).border(1.dp, p.stroke, shape).padding(horizontal = 18.dp, vertical = 13.dp)) {
        if (value.isEmpty()) Text(hint, 16, p.text2)
        BasicTextField(value, onChange, singleLine = true, cursorBrush = SolidColor(p.text),
            textStyle = TextStyle(fontFamily = DmSans, fontSize = 16.sp, color = p.text), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Choice(label: String, p: Palette, onClick: () -> Unit) =
    Text(label, 20, p.text, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))

/** Long-press a row: swap it, add another app, move it up, or remove it. Manual, instant, no model call. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRowSheet(vm: MomentViewModel, ui: UiState) {
    val item = ui.rowEdit ?: return
    val p = palette(Look.CREAM)
    var mode by remember(item) { mutableStateOf("menu") } // menu | swap | add
    var q by remember(item) { mutableStateOf("") }
    val index = ui.items.indexOfFirst { it.label == item.label }
    ModalBottomSheet(onDismissRequest = { vm.editRow(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SHEET) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (mode == "add") "Add an app" else item.label, 28, p.text, weight = 500)
            Text(ui.context?.name.orEmpty(), 14, p.text2)
            Spacer(Modifier.height(10.dp))
            if (mode == "menu") {
                Option(Icons.SPARKLE, "Swap", p) { mode = "swap" }
                // Four rows at most keeps the screen calm: when full, adding means swapping one.
                if (ui.items.size < 4) Option(PLUS, "Add an app", p) { mode = "add" }
                else Option(PLUS, "Add an app · 4 max, swap one", p, dim = true) { mode = "swap" }
                if (index > 0) Option(UP, "Move up", p) { vm.moveUp(item) }
                Option(Icons.CLOSE, "Remove", p) { vm.removeRow(item) }
                Option(RESET, "Reset rows", p) { vm.resetRows() }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(mode) { runCatching { focus.requestFocus() } }
                Field(q, "Search apps", p, { q = it }, Modifier.focusRequester(focus))
                Spacer(Modifier.height(6.dp))
                val apps = ui.apps.filter { q.isBlank() || it.label.contains(q.trim(), true) }
                    .filter { a -> ui.items.none { it.label == a.label } }.sortedBy { it.label.lowercase() }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(apps, key = { it.packageName }) { a ->
                        Choice(a.label, p) { if (mode == "swap") vm.swapRow(item, a) else vm.addRow(a) }
                    }
                }
            }
        }
    }
}

private const val PHOTO = "M5 5H19A2 2 0 0 1 21 7V17A2 2 0 0 1 19 19H5A2 2 0 0 1 3 17V7A2 2 0 0 1 5 5Z M3 15L8 10L13 15 M13 13L16 10L21 15 M15.5 8.5H15.6"
private const val PLUS = "M12 5V19 M5 12H19"
private const val UP = "M12 19V5 M6 11L12 5L18 11"
private const val RESET = "M4 4V9H9 M4.5 15A8 8 0 1 0 6 6.3L4 9"

@Composable
private fun Option(icon: String, label: String, p: Palette, dim: Boolean = false, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LineIcon(icon, if (dim) p.text2 else p.text, 22.dp)
        Spacer(Modifier.width(16.dp))
        Text(label, 20, if (dim) p.text2 else p.text)
    }

/** Long-press the background: paint it again (or something you describe), use your photo, or remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackdropSheet(vm: MomentViewModel, ui: UiState) {
    // Registered before the early return: the sheet closes while the photo picker is open,
    // and the result must still arrive.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.useMyPhoto(uri) }
    if (!ui.backdropMenu) return
    val p = palette(Look.CREAM)
    var wish by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { vm.openBackdropMenu(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SHEET) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Background", 28, p.text, weight = 500)
            Spacer(Modifier.height(6.dp))
            ui.backdrop?.let { bg ->
                androidx.compose.foundation.Image(bg, null, Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(22.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(wish, "Paint… a beach at sunset", p, { wish = it }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text("Paint", 17, p.text, weight = 500, underline = true, modifier = Modifier.clickable { vm.repaint(wish.ifBlank { null }) }.padding(8.dp))
            }
            Option(Icons.SPARKLE, "Paint again", p) { vm.repaint(null) }
            Option(PHOTO, "Use my photo", p) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (ui.backdrop != null) Option(Icons.CLOSE, "Remove", p) { vm.removeBackdrop() }
        }
    }
}
