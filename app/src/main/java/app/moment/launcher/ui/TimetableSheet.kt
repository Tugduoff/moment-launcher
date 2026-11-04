package app.moment.launcher.ui

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.moment.launcher.MomentViewModel
import app.moment.launcher.UiState
import app.moment.launcher.ai.Timetable
import app.moment.launcher.ai.TimetableGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val IMAGE_ICON = "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z M8.5 10A1.5 1.5 0 1 1 8.5 7A1.5 1.5 0 1 1 8.5 10Z M21 15L16 10L5 21"
private const val CAMERA_ICON = "M23 19A2 2 0 0 1 21 21H3A2 2 0 0 1 1 19V8A2 2 0 0 1 3 6H7L9 3H15L17 6H21A2 2 0 0 1 23 8Z M12 17A4 4 0 1 1 12 9A4 4 0 1 1 12 17Z"
private const val CHECK_ICON = "M5 12.5L10 17.5L19 7"

/** The two ways to bring a schedule in: the photo picker (no permission needed) or the camera. */
class TimetablePickers(val pick: () -> Unit, val camera: () -> Unit)

@Composable
fun rememberTimetablePickers(vm: MomentViewModel): TimetablePickers {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Uri?>(null) }

    fun read(uri: Uri, delete: Boolean) = scope.launch {
        val bytes = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                .also { if (delete) runCatching { ctx.contentResolver.delete(uri, null, null) } }
        }
        if (bytes != null) Timetable.start(vm, bytes)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) read(uri, false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pending?.let { u -> if (ok) read(u, delete = true) else runCatching { ctx.contentResolver.delete(u, null, null) } }
        pending = null
    }
    return remember {
        TimetablePickers(
            pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            camera = {
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "moment_timetable_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { pending = it; camera.launch(it) }
            },
        )
    }
}

/** Search action row: typing "timetable" or "schedule" offers to snap one. */
@Composable
fun TimetableSearchRow(vm: MomentViewModel, query: String, p: Palette) {
    val q = query.trim().lowercase()
    val hit = q.length >= 4 && ("timetable".startsWith(q) || "schedule".startsWith(q) || q.contains("timetable") || q.contains("schedule"))
    if (!hit) return
    val pickers = rememberTimetablePickers(vm)
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(p.pill, shape).border(1.dp, p.stroke, shape)
            .clickable { pickers.pick() }.padding(start = 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).border(1.5.dp, p.stroke, CircleShape), contentAlignment = Alignment.Center) { LineIcon(IMAGE_ICON, p.text) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Snap your timetable", 17, p.text, weight = 500, maxLines = 1)
            Text("A photo becomes your week", 13, p.text2, maxLines = 1)
        }
        Box(
            Modifier.size(44.dp).border(1.dp, p.stroke, CircleShape).clickable { pickers.camera() },
            contentAlignment = Alignment.Center,
        ) { LineIcon(CAMERA_ICON, p.text, 22.dp) }
    }
}

/** The floating card above the companion: reading, review (pick what to add), or a gentle failure. */
@Composable
fun TimetableSheet(vm: MomentViewModel, ui: UiState) {
    val state by Timetable.state.collectAsStateWithLifecycle()
    val ctx = ui.context ?: return
    val p = palette(ctx.look)
    var last by remember { mutableStateOf<Timetable.State>(Timetable.State.Idle) }
    if (state !is Timetable.State.Idle) last = state
    BackHandler(enabled = state is Timetable.State.Review || state is Timetable.State.Failed) { Timetable.close() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            state !is Timetable.State.Idle, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 8 }, exit = fadeOut(tween(160)),
        ) {
            // Sits above the companion row (companion 100 + search 52 + gaps), so the companion stays visible.
            Column(Modifier.systemBarsPadding().padding(horizontal = 28.dp).padding(bottom = 188.dp)) {
                when (val s = last) {
                    is Timetable.State.Reading -> ReadingCard(s, p)
                    is Timetable.State.Review -> ReviewCard(vm, s, p)
                    is Timetable.State.Failed -> FailedCard(vm, s.message, p)
                    else -> Unit
                }
            }
        }
    }
}

private val cardShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 6.dp)

private fun Modifier.card(p: Palette) = this.fillMaxWidth()
    .shadow(24.dp, cardShape, ambientColor = Color(0x1433261A), spotColor = Color(0x1433261A))
    .background(p.card, cardShape).border(1.dp, p.stroke, cardShape).padding(20.dp)

@Composable
private fun ReadingCard(s: Timetable.State.Reading, p: Palette) {
    val pulse = rememberInfiniteTransition(label = "read").animateFloat(.35f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Row(Modifier.card(p), verticalAlignment = Alignment.CenterVertically) {
        if (s.thumb != null) {
            Image(s.thumb, null, Modifier.size(width = 72.dp, height = 56.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, p.stroke, RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Reading your timetable", 19, p.text, weight = 500, lineHeight = 26)
            Text("Muse Spark", 14, p.text2, lineHeight = 20, modifier = Modifier.alpha(pulse.value))
        }
    }
}

@Composable
private fun ReviewCard(vm: MomentViewModel, s: Timetable.State.Review, p: Palette) {
    val groups = s.result.groups
    var sel by remember(s) { mutableStateOf(groups.map { it.name }.toSet()) }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.44f).dp
    Column(Modifier.card(p).heightIn(max = maxH), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your week", 19, p.text, weight = 500, lineHeight = 26)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            groups.forEach { g ->
                GroupRow(g, g.name in sel, p) { sel = if (g.name in sel) sel - g.name else sel + g.name }
            }
        }
        val all = sel.size == groups.size
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            TtButton(if (all) "Add all" else "Add selected (${sel.size})", true, p, enabled = sel.isNotEmpty()) {
                Timetable.add(vm, groups.filter { it.name in sel })
            }
            TtButton("Dismiss", false, p) { Timetable.close() }
        }
    }
}

@Composable
private fun GroupRow(g: TimetableGroup, checked: Boolean, p: Palette, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null, onClick = onToggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val box = RoundedCornerShape(7.dp)
        Box(
            Modifier.padding(top = 2.dp).size(22.dp).then(if (checked) Modifier.background(p.accentFill, box) else Modifier.border(1.5.dp, p.stroke, box)),
            contentAlignment = Alignment.Center,
        ) { if (checked) LineIcon(CHECK_ICON, p.accentOn, 16.dp, stroke = 2.4f) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(if (checked) 1f else .55f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(g.name, 17, p.text, weight = 500, maxLines = 1)
            Text(g.slotsText, 13, p.text2, maxLines = 2)
        }
    }
}

@Composable
private fun FailedCard(vm: MomentViewModel, message: String, p: Palette) {
    val pickers = rememberTimetablePickers(vm)
    Column(Modifier.card(p), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, 19, p.text, weight = 500, lineHeight = 26)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            TtButton("Choose a photo", true, p) { Timetable.close(); pickers.pick() }
            TtButton("Dismiss", false, p) { Timetable.close() }
        }
    }
}

/** Spec buttons: radius 20, padding 18/10, 15 Medium. Primary = accent fill; otherwise ghost. */
@Composable
private fun TtButton(label: String, primary: Boolean, p: Palette, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        (if (primary) Modifier.background(p.accentFill, shape) else Modifier).alpha(if (enabled) 1f else .4f)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(label, 15, if (primary) p.accentOn else p.text2, weight = 500) }
}
