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

/** Long-press a row: swap it for another app, or remove it. Manual, instant, no model call. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRowSheet(vm: MomentViewModel, ui: UiState) {
    val item = ui.rowEdit ?: return
    val p = palette(Look.CREAM)
    var swapping by remember(item) { mutableStateOf(false) }
    var q by remember(item) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { vm.editRow(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SHEET) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.label, 28, p.text, weight = 500)
            Spacer(Modifier.height(6.dp))
            if (!swapping) {
                Choice("Swap", p) { swapping = true }
                Choice("Remove", p) { vm.removeRow(item) }
                Choice("Reset rows", p) { vm.resetRows() }
            } else {
                Field(q, "Search apps", p, { q = it })
                val apps = ui.apps.filter { q.isBlank() || it.label.contains(q.trim(), true) }.filter { a -> ui.items.none { it.label == a.label } }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(apps, key = { it.packageName }) { a -> Choice(a.label, p) { vm.swapRow(item, a) } }
                }
            }
        }
    }
}

/** Long-press the background: paint it again (or something you describe), use your photo, or remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackdropSheet(vm: MomentViewModel, ui: UiState) {
    if (!ui.backdropMenu) return
    val p = palette(Look.CREAM)
    var wish by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.useMyPhoto(uri) }
    ModalBottomSheet(onDismissRequest = { vm.openBackdropMenu(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SHEET) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Background", 28, p.text, weight = 500)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(wish, "Paint… a beach at sunset", p, { wish = it }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text("Paint", 17, p.text, weight = 500, underline = true, modifier = Modifier.clickable { vm.repaint(wish.ifBlank { null }) }.padding(8.dp))
            }
            Choice("Paint again", p) { vm.repaint(null) }
            Choice("Use my photo", p) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (ui.backdrop != null) Choice("Remove", p) { vm.removeBackdrop() }
        }
    }
}
