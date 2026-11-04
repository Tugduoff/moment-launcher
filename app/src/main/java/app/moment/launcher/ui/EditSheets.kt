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
import androidx.compose.foundation.layout.imePadding
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.moment.launcher.MomentViewModel
import app.moment.launcher.UiState
import app.moment.launcher.data.Look
import app.moment.launcher.data.Mood

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
    var mode by remember(item) { mutableStateOf(if (item.label.isEmpty()) "add" else "menu") } // menu | swap | add
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
            Text("This screen", 28, p.text, weight = 500)
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
            if (ui.items.size < 4) Option(PLUS, "Add an app", p) { vm.addApp() }
            Option(Icons.SPARKLE, "Paint again", p) { vm.repaint(null) }
            Option(PHOTO, "Use my photo", p) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (ui.backdrop != null) Option(Icons.CLOSE, "Remove", p) { vm.removeBackdrop() }
            Option(Icons.SPARKLE, "Style: font and clock", p) { vm.openStyle(true) }
            if (ui.widgetIds.size < app.moment.launcher.system.Widgets.MAX) Option(PLUS, "Add a widget", p) { vm.openWidgetPicker(true) }
            ui.widgetIds.forEach { id -> Option(Icons.CLOSE, "Remove widget: ${vm.widgets.label(id)}", p) { vm.removeWidget(id) } }
        }
    }
}

/** Opening an app hidden for focus: a gentle warning, and a 10-second wait if you still want it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusGuardSheet(vm: MomentViewModel, ui: UiState) {
    val app = ui.guard ?: return
    val p = palette(Look.CREAM)
    ModalBottomSheet(onDismissRequest = { vm.guardCancel() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SHEET) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.label, 28, p.text, weight = 500)
                    Text("Hidden while you focus.", 16, p.text2)
                }
                Companion(Mood.GLASSES, 64.dp)
            }
            Spacer(Modifier.height(12.dp))
            Option(Icons.LOCK, "Stay focused", p) { vm.guardCancel() }
            val sec = ui.guardSeconds
            Option(Icons.CHEVRON, if (sec == null) "Open in 10 s" else "Opening in $sec s…", p, dim = sec != null) { if (sec == null) vm.guardOpen() }
        }
    }
}

/** A widget's preview picture (or its app icon when it has none), drawn off the main thread. */
@Composable
private fun WidgetPreview(info: android.appwidget.AppWidgetProviderInfo, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, info.provider) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 256; val h = d.intrinsicHeight.takeIf { it > 0 } ?: 256
                val scale = minOf(1f, 720f / maxOf(w, h))
                d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.background(Color.White, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        img?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxWidth().padding(8.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
    }
}

/** Every widget the installed apps offer: searchable, with previews; tap one to see it big, then add it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(vm: MomentViewModel, ui: UiState, onPick: (android.appwidget.AppWidgetProviderInfo) -> Unit) {
    if (!ui.widgetPicker) return
    val p = palette(Look.CREAM)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val providers = remember { vm.widgets.providers().map { it to (it.loadLabel(ctx.packageManager) to vm.widgets.appLabel(it)) } }
    var q by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<android.appwidget.AppWidgetProviderInfo?>(null) }
    ModalBottomSheet(onDismissRequest = { vm.openWidgetPicker(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SHEET) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).imePadding().navigationBarsPadding()) {
            val pick = chosen
            if (pick != null) {
                // Preview before adding: what it looks like, how big it is.
                androidx.activity.compose.BackHandler { chosen = null }
                Text(pick.loadLabel(ctx.packageManager), 28, p.text, weight = 500, maxLines = 2)
                Text(vm.widgets.appLabel(pick) + " · " + sizeText(ctx, pick), 15, p.text2)
                Spacer(Modifier.height(16.dp))
                WidgetPreview(pick, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp))
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("Add", Btn.Primary, p) { onPick(pick) }
                    Button("Back", Btn.Ghost, p) { chosen = null }
                }
            } else {
                Text("Add a widget", 28, p.text, weight = 500)
                Spacer(Modifier.height(10.dp))
                Field(q, "Search widgets or apps", p, { q = it })
                Spacer(Modifier.height(6.dp))
                val shown = providers.filter { (_, l) -> q.isBlank() || l.first.contains(q.trim(), true) || l.second.contains(q.trim(), true) }
                if (shown.isEmpty()) Text(if (providers.isEmpty()) "No app on this phone offers a widget." else "No widget matches.", 16, p.text2, modifier = Modifier.padding(vertical = 12.dp))
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(shown, key = { it.first.provider.flattenToString() }) { (info, labels) ->
                        Row(Modifier.fillMaxWidth().clickable { chosen = info }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            WidgetPreview(info, Modifier.width(76.dp).height(56.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(labels.first, 17, p.text, weight = 500, maxLines = 1)
                                Text(labels.second, 14, p.text2, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** "4 × 2 cells" from the widget's declared size. */
private fun sizeText(ctx: android.content.Context, info: android.appwidget.AppWidgetProviderInfo): String {
    if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) return "${info.targetCellWidth} × ${info.targetCellHeight}"
    val d = ctx.resources.displayMetrics.density
    val w = ((info.minWidth / d + 30) / 70).toInt().coerceAtLeast(1); val h = ((info.minHeight / d + 30) / 70).toInt().coerceAtLeast(1)
    return "$w × $h"
}

/** Font for the whole home screen, clock weight and size, 12/24 h: all live, nothing leaves the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSheet(vm: MomentViewModel, ui: UiState) {
    if (!ui.showStyle) return
    val p = palette(Look.CREAM)
    val st = Style.current
    ModalBottomSheet(onDismissRequest = { vm.openStyle(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SHEET) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Style", 28, p.text, weight = 500)
            Text(ui.time.ifBlank { "12:34" }, Style.SIZES[st.clockSize].second / 2 + 20, p.text, weight = Style.WEIGHTS[st.clockWeight].second, font = Style.clockFamily(st))
            ThemesSection(vm, ui, p)
            Text("Effect", 13, p.text2)
            EffectCarousel(st.effect) { vm.setStyle(st.copy(effect = it)) }
            Text("Clock font", 13, p.text2)
            ChoiceRow(Style.CLOCK_FONTS, st.clockFont, p, fontOf = { if (it == 0) Style.family(st.font) else Style.family(it - 1) }) { vm.setStyle(st.copy(clockFont = it)) }
            Text("App font", 13, p.text2)
            ChoiceRow(Style.FONTS, st.font, p, fontOf = { Style.family(it) }) { vm.setStyle(st.copy(font = it)) }
            Text("Clock weight", 13, p.text2)
            ChoiceRow(Style.WEIGHTS.map { it.first }, st.clockWeight, p) { vm.setStyle(st.copy(clockWeight = it)) }
            Text("Clock size", 13, p.text2)
            ChoiceRow(Style.SIZES.map { it.first }, st.clockSize, p) { vm.setStyle(st.copy(clockSize = it)) }
            Text("Auto follows the context (rain, stars, glow). The sparkle by the clock pauses any effect.", 12, p.text2)
            Text("Time format", 13, p.text2)
            ChoiceRow(listOf("24 h", "12 h"), if (st.h12) 1 else 0, p) { vm.setStyle(st.copy(h12 = it == 1)) }
        }
    }
}

/** Effects as a swipeable row of live previews; the chosen one is ringed and centred. */
@Composable
private fun EffectCarousel(selected: Int, onPick: (Int) -> Unit) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
    androidx.compose.foundation.lazy.LazyRow(
        state = state, horizontalArrangement = Arrangement.spacedBy(10.dp),
        flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state),
        modifier = Modifier.fillMaxWidth().height(192.dp),
    ) {
        items(Effects.NAMES.size) { i ->
            val on = i == selected
            val shape = RoundedCornerShape(20.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The chosen card: an orange ring outside the preview (a dark ring vanished on the night ground) and a check.
                val ring = RoundedCornerShape(24.dp)
                Box(
                    Modifier.size(112.dp, 158.dp).border(3.dp, if (on) Color(HINT_DOT) else Color.Transparent, ring).padding(4.dp)
                        .clip(shape)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1C2546), Color(0xFF3A2A5C))))
                        .clickable { onPick(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(Color(HINT_DOT), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center) { LineIcon("M5 12L10 17L19 7", Color.White, 14.dp) }
                    when (i) {
                        0 -> Text("Follows\nthe context", 12, Color.White.copy(alpha = .8f))
                        1 -> Text("Calm", 13, Color.White.copy(alpha = .6f))
                        else -> EffectLayer(i)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(Effects.NAMES[i], 13, if (on) Color(HINT_DOT) else Color(0xFF2A2622), weight = if (on) 600 else 400, maxLines = 1)
            }
        }
    }
}

/** Saved looks: tap to wear one, tie one to this context, share it as a code. */
@Composable
private fun ThemesSection(vm: MomentViewModel, ui: UiState, p: Palette) {
    var name by remember { mutableStateOf("") }
    val ctx = ui.context
    Text("Themes", 13, p.text2)
    ui.themes.forEach { t ->
        val here = ctx != null && ui.contextThemes[ctx.id] == t.name
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(t.name, 17, p.text, weight = 500, maxLines = 1, font = Style.family(Style.decode(t.style).font),
                modifier = Modifier.weight(1f).clickable { vm.applyTheme(t) }.padding(vertical = 8.dp))
            if (ctx != null) Text(if (here) "In ${ctx.name} ✓" else "Use in ${ctx.name}", 13, if (here) Color(HINT_DOT) else p.text2,
                modifier = Modifier.clickable { vm.toggleThemeForContext(t) }.padding(8.dp), maxLines = 1)
            Text("Share", 13, p.text2, underline = true, modifier = Modifier.clickable { vm.shareTheme(t) }.padding(8.dp))
            LineIcon(Icons.CLOSE, p.text2, 16.dp, modifier = Modifier.clickable { vm.deleteTheme(t) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(name, "Name this look…", p, { name = it }, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text("Save", 17, p.text, weight = 500, underline = true, modifier = Modifier.clickable { vm.saveTheme(name); name = "" }.padding(8.dp))
    }
    Text("Saves fonts, clock, effect and the companion's colours and face. Paste a shared code in search to import one.", 12, p.text2)
}
