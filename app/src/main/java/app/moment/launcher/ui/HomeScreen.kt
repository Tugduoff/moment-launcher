package app.moment.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.moment.launcher.MomentViewModel
import app.moment.launcher.UiState
import app.moment.launcher.data.Look
import app.moment.launcher.data.NowItem
import kotlin.math.cos
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

// ---- Icons: stroke paths from the Figma file, 24-unit viewBox -------------------------------------

object Icons {
    const val SEARCH = "M18 11A7 7 0 1 1 4 11A7 7 0 1 1 18 11Z M20 20L16.5 16.5"
    const val NAVIGATE = "M3 11L21 3L13 21L11 13L3 11Z"
    const val CHEVRON = "M9 18L15 12L9 6"
    const val CARD = "M4 5H20A2 2 0 0 1 22 7V17A2 2 0 0 1 20 19H4A2 2 0 0 1 2 17V7A2 2 0 0 1 4 5Z M2 10H22"
    const val LOCK = "M6 11H18A2 2 0 0 1 20 13V19A2 2 0 0 1 18 21H6A2 2 0 0 1 4 19V13A2 2 0 0 1 6 11Z M8 11V7A4 4 0 0 1 16 7V11"
    const val MUSIC = "M9 18V5L21 3V16 M9 18A3 3 0 1 1 3 18A3 3 0 1 1 9 18Z M21 16A3 3 0 1 1 15 16A3 3 0 1 1 21 16Z"
    const val PHONE = "M22 16.92V19.92A2 2 0 0 1 19.82 21.92A19.79 19.79 0 0 1 11.19 18.85A19.5 19.5 0 0 1 5.19 12.85A19.79 19.79 0 0 1 2.12 4.18A2 2 0 0 1 4.11 2H7.11A2 2 0 0 1 9.11 3.72A12.84 12.84 0 0 0 9.81 6.53A2 2 0 0 1 9.36 8.64L8.09 9.91A16 16 0 0 0 14.09 15.91L15.36 14.64A2 2 0 0 1 17.47 14.19A12.84 12.84 0 0 0 20.28 14.89A2 2 0 0 1 22 16.92Z"
    const val SHARE = "M18 8A3 3 0 1 0 18 2A3 3 0 1 0 18 8Z M6 15A3 3 0 1 0 6 9A3 3 0 1 0 6 15Z M18 22A3 3 0 1 0 18 16A3 3 0 1 0 18 22Z M8.59 13.51L15.42 17.49 M15.41 6.51L8.59 10.49"
    const val CAR = "M3 13L5.5 7H18.5L21 13V18H3Z M3 13H21 M7 18V20 M17 18V20 M7 15.5H8 M16 15.5H17"
    const val GLOBE = "M12 22A10 10 0 1 1 12 2A10 10 0 1 1 12 22Z M2 12H22 M12 2C15 5 15 19 12 22C9 19 9 5 12 2Z"
    const val SPARKLE = "M12 3L13.8 10.2L21 12L13.8 13.8L12 21L10.2 13.8L3 12L10.2 10.2Z"
    const val CLOSE = "M6 6L18 18 M18 6L6 18"
    const val MIC = "M12 2A3 3 0 0 1 15 5V12A3 3 0 0 1 9 12V5A3 3 0 0 1 12 2Z M19 10V12A7 7 0 0 1 5 12V10 M12 19V22 M8 22H16"
}

@Composable
fun LineIcon(d: String, color: Color, size: Dp = 24.dp, stroke: Float = 2f, modifier: Modifier = Modifier) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private const val CALENDAR_ICON = "M5 5H19A2 2 0 0 1 21 7V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V7A2 2 0 0 1 5 5Z M3 10H21 M8 3V7 M16 3V7"

private fun iconFor(item: NowItem) = when {
    item.kind == "calendar" -> CALENDAR_ICON
    item.kind == "navigate" -> Icons.NAVIGATE
    item.kind == "call" -> Icons.PHONE
    item.kind == "share_location" -> Icons.SHARE
    item.kind == "ride" -> Icons.CAR
    item.kind == "music" -> Icons.MUSIC
    item.kind == "web" -> Icons.GLOBE
    item.kind == "moment" -> Icons.SPARKLE
    else -> Icons.CARD
}

/** Actions (and payments) get a card; plain apps stay as quiet text rows. */
private fun isAction(item: NowItem) =
    item.kind in setOf("navigate", "call", "share_location", "ride", "calendar") ||
        item.label.startsWith("Pay", true) || item.target.equals("Izly", true)

// ---- Screen ------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: MomentViewModel, ui: UiState) {
    val ctx = ui.context ?: return
    val p = palette(ctx.look)
    // A launcher never closes on Back: this lowest-priority handler swallows it.
    BackHandler(enabled = true) { }
    val focus = LocalFocusManager.current
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun buzz() = haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    val searching = (focused || ui.query.isNotBlank()) && !ui.chatting
    BackHandler(enabled = searching || ui.chatting || ui.proposal != null || ui.plan != null) {
        if (ui.chatting) { vm.setQuery(""); focus.clearFocus(); vm.endChat() }
        else if (searching) { vm.setQuery(""); focus.clearFocus() } else if (ui.plan != null) vm.dismissPlan() else vm.dismissProposal()
    }

    val swipeMin = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    Box(Modifier.fillMaxSize().background(p.bg).pointerInput(Unit) {
        // Swipe sideways (either way) to see every context.
        var dx = 0f
        detectHorizontalDragGestures(
            onDragStart = { dx = 0f },
            onDragEnd = { if (kotlin.math.abs(dx) > swipeMin && !vm.ui.value.showOnboarding) vm.openContexts(true) },
        ) { change, d -> dx += d; change.consume() }
    }.pointerInput(Unit) {
        // Long-press on empty space: change the background (paint again, your photo, remove).
        detectTapGestures(onLongPress = { buzz(); vm.openBackdropMenu(true) })
    }) {
        ui.backdrop?.let { bg ->
            androidx.compose.foundation.Image(bg, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            // A veil of the palette's own ground keeps every row readable on any painting.
            // Fog at the top (behind the clock) and the bottom (behind the rows); the middle stays clear.
            val veil = when { p.dark -> Color(0xFF121317); ctx.look == Look.STUDY -> Color(0xFFECEFF4); else -> Color(0xFFF7F4EE) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to veil.copy(alpha = .82f), .28f to veil.copy(alpha = .35f), .45f to veil.copy(alpha = .12f),
                .62f to veil.copy(alpha = .55f), .78f to veil.copy(alpha = .86f), 1f to veil.copy(alpha = .94f),
            )))
        }
        if (!ui.effectsOff) when (ctx.look) {
            Look.GAME -> GameGlow()
            Look.RAIN -> RainOverlay()
            Look.NIGHT -> NightStars()
            else -> Unit
        }
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 28.dp).padding(bottom = 20.dp)
        ) {
            if (!searching) {
                // Figma: the clock's text box starts 86 dp from the top of the screen, whatever the status bar height.
                val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars
                    .asPaddingValues().calculateTopPadding()
                Spacer(Modifier.height((86.dp - statusTop).coerceAtLeast(12.dp)))
                // Clock: long-press opens the hidden demo panel.
                Column(Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = {}, onLongClick = { vm.openDemo(true) },
                )) {
                    Style.current.let { st ->
                        val size = Style.SIZES[st.clockSize].second
                        Text(ui.time, size, p.text, weight = Style.WEIGHTS[st.clockWeight].second, letterSpacing = -size * 0.03, lineHeight = (size * 1.09).toInt())
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(ui.date, 17, p.text2)
                        StatusGlyphs(ui, p)
                        // Rain, stars or glow on screen: one small button turns them off (and back on).
                        if (ctx.look == Look.RAIN || ctx.look == Look.NIGHT || ctx.look == Look.GAME) {
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(32.dp).clickable { vm.toggleEffects() }, contentAlignment = Alignment.Center) {
                                LineIcon(Icons.SPARKLE, p.text2.copy(alpha = if (ui.effectsOff) .35f else .8f), 18.dp)
                            }
                        }
                    }
                }
            }
            if (searching) {
                // Searching: results take the whole top of the screen; clock and companion step aside.
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
                    SearchResults(vm, ui, p)
                }
            } else if (ui.chatting) {
                // Talking: the conversation takes the space of the rows, newest at the bottom.
                ChatThread(ui, p, Modifier.weight(1f)) { vm.endChat() }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = { vm.openWardrobe(true) }) { vm.tapCompanion() }) {
                        Companion(ui.mood, 96.dp, speakKey = ui.speak, hopKey = ui.hop, layers = ui.layers, dizzyKey = ui.dizzy)
                    }
                }
            } else {
                // Widgets sit under the clock; the free space stays above the rows.
                Column(Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ui.widgetIds.forEach { id -> WidgetSlot(vm, id) }
                }
                // The Now block swaps between: search results, a proposal card, or this moment's items.
                AnimatedContent(
                    targetState = when {
                        ui.query.isNotBlank() -> "search"
                        ui.plan != null -> "plan"
                        ui.proposal != null -> "proposal"
                        else -> "now:" + ctx.id
                    },
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 8 }).togetherWith(fadeOut(tween(120))) },
                    label = "now",
                ) { key ->
                    when {
                        key == "search" -> SearchResults(vm, ui, p)
                        key == "proposal" -> ProposalCard(vm, ui, p)
                        key == "plan" -> PlanCard(vm, ui, p)
                        ctx.bigButtons -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ui.items.forEach { BigTile(it, p, onLong = { buzz(); vm.editRow(it) }) { vm.runItem(it) } }
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ui.items.forEach { item ->
                                if (isAction(item)) ActionCard(item, p, onLong = { buzz(); vm.editRow(item) }) { vm.runItem(item) }
                                else AppRow(item, p, onLong = { buzz(); vm.editRow(item) }) { vm.runItem(item) }
                            }
                            // Nothing left on this moment: a quiet way back.
                        if (ui.items.isEmpty()) Row(
                            Modifier.fillMaxWidth().height(44.dp).clickable { vm.addApp() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LineIcon("M12 5V19 M5 12H19", p.text2, 20.dp); Spacer(Modifier.width(10.dp)); Text("Add an app", 20, p.text2)
                        }
                        }
                    }
                }
                Spacer(Modifier.height(if (ctx.bigButtons) 20.dp else 16.dp))
                // Companion row.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = { vm.openWardrobe(true) }) { vm.tapCompanion() }, contentAlignment = Alignment.Center) {
                        if (ui.listening) ListeningRings(ui.voiceLevel, p.text)
                        Companion(ui.mood, 96.dp, speakKey = ui.speak, hopKey = ui.hop, layers = ui.layers, dizzyKey = ui.dizzy)
                    }
                    Spacer(Modifier.width(4.dp))
                    AnimatedContent(
                        targetState = ui.bubble ?: if (ctx.hideSearch) "#caption" else "",
                        transitionSpec = { (fadeIn() + slideInHorizontally { -it / 6 }).togetherWith(fadeOut()) },
                        label = "bubble",
                    ) { b ->
                        when (b) {
                            "" -> Spacer(Modifier.width(1.dp))
                            "#caption" -> Spacer(Modifier.width(1.dp))
                            else -> Bubble(b, p, dot = ui.bubbleIsIdea) { vm.tapCompanion() }
                        }
                    }
                }
            }
            if (!ctx.hideSearch) {
                Spacer(Modifier.height(12.dp))
                SearchBar(vm, ui, p, onFocus = { focused = it })
            }
        }
    }
}

// ---- Pieces ------------------------------------------------------------------------------------

/** The conversation with the companion: your lines on the right, its lines on the left. */
@Composable
private fun ChatThread(ui: UiState, p: Palette, modifier: Modifier, onClose: () -> Unit) {
    val scroll = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(ui.chat.size, ui.thinking) { scroll.animateScrollTo(scroll.maxValue) }
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).verticalScroll(scroll).padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.chat.forEach { (me, text) ->
                val shape = if (me) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (me) Arrangement.End else Arrangement.Start) {
                    Box(Modifier.fillMaxWidth(.82f), contentAlignment = if (me) Alignment.CenterEnd else Alignment.CenterStart) {
                        Text(text, 16, if (me) p.accentOn else p.text, lineHeight = 22,
                            modifier = Modifier.background(if (me) p.accentFill else p.card, shape).border(if (me) 0.dp else 1.dp, p.stroke, shape)
                                .padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
            if (ui.thinking) Text("…", 22, p.text2, modifier = Modifier.padding(start = 12.dp))
        }
        Box(Modifier.align(Alignment.TopEnd).size(36.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            LineIcon(Icons.CLOSE, p.text2, 18.dp)
        }
    }
}

private object Glyph {
    const val SUN = "M12 8A4 4 0 1 1 12 16A4 4 0 1 1 12 8Z M12 2V4 M12 20V22 M4.9 4.9L6.3 6.3 M17.7 17.7L19.1 19.1 M2 12H4 M20 12H22 M4.9 19.1L6.3 17.7 M17.7 6.3L19.1 4.9"
    const val CLOUD = "M7 18H17A4 4 0 0 0 17 10A5.5 5.5 0 0 0 6.5 11A3.5 3.5 0 0 0 7 18Z"
    const val RAIN = "M7 14H17A4 4 0 0 0 17 6A5.5 5.5 0 0 0 6.5 7A3.5 3.5 0 0 0 7 14Z M8 17L7 20 M12 17L11 20 M16 17L15 20"
    const val SNOW = "M7 14H17A4 4 0 0 0 17 6A5.5 5.5 0 0 0 6.5 7A3.5 3.5 0 0 0 7 14Z M8 18V18.5 M12 19V19.5 M16 18V18.5"
    // Two footprints.
    const val STEPS = "M7 3C9 3 10 5 10 8C10 11 9 13 7 13C5 13 4 11 4 8C4 5 5 3 7 3Z M5 16H9 M17 8C19 8 20 10 20 13C20 16 19 18 17 18C15 18 14 16 14 13C14 10 15 8 17 8Z M15 21H19"
    const val BATTERY = "M3 8H17A1 1 0 0 1 18 9V15A1 1 0 0 1 17 16H3A1 1 0 0 1 2 15V9A1 1 0 0 1 3 8Z M21 11V13 M5 11V13"
}

/** Weather, steps and a low battery as small glyphs beside the date: no sentences. */
@Composable
private fun StatusGlyphs(ui: UiState, p: Palette) {
    val w = ui.weather?.takeIf { ui.demo.clockOffsetMs == 0L }
    val s = ui.signals
    @Composable fun item(icon: String, value: String) = Row(verticalAlignment = Alignment.CenterVertically) {
        LineIcon(icon, p.text2, 18.dp, stroke = 2.2f); Spacer(Modifier.width(5.dp)); Text(value, 17, p.text2, maxLines = 1)
    }
    if (w != null) {
        val sum = w.summary.lowercase()
        val icon = when {
            w.rainingNow || "rain" in sum || "drizzle" in sum || "shower" in sum -> Glyph.RAIN
            "snow" in sum -> Glyph.SNOW
            "sun" in sum || "clear" in sum -> Glyph.SUN
            else -> Glyph.CLOUD
        }
        item(icon, "${w.tempC}°")
    }
    s?.steps?.takeIf { it > 0 }?.let { item(Glyph.STEPS, if (it >= 1000) "%.1fk".format(Locale.ENGLISH, it / 1000f).replace(".0k", "k") else "$it") }
    s?.battery?.takeIf { it <= 20 && !s.charging }?.let { item(Glyph.BATTERY, "$it%") }
}

@Composable
fun Text(
    text: String, size: Int, color: Color, modifier: Modifier = Modifier, weight: Int = 400,
    letterSpacing: Double = 0.0, lineHeight: Int? = null, maxLines: Int = Int.MAX_VALUE, underline: Boolean = false,
) = androidx.compose.foundation.text.BasicText(
    text, modifier,
    style = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight(weight), fontSize = size.sp, color = color,
        letterSpacing = letterSpacing.sp, lineHeight = lineHeight?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
        textDecoration = if (underline) TextDecoration.Underline else null,
    ),
    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
)

@Composable
private fun Chip(text: String, p: Palette) {
    Row(
        Modifier.background(p.pill, RoundedCornerShape(14.dp)).border(1.dp, p.stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(p.text, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, 13, p.text, weight = 500)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(item: NowItem, p: Palette, onLong: () -> Unit = {}, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).combinedClickable(onLongClick = onLong, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(item.label, 24, p.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        if (item.detail.isNotBlank()) Text(item.detail, 14, p.text2, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionCard(item: NowItem, p: Palette, onLong: () -> Unit = {}, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(p.pill, shape).border(1.dp, p.stroke, shape)
            .combinedClickable(onLongClick = onLong, onClick = onClick).padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).border(1.5.dp, p.stroke, CircleShape), contentAlignment = Alignment.Center) {
            LineIcon(iconFor(item), p.text)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.label, 17, p.text, weight = 500, maxLines = 1)
            if (item.detail.isNotBlank()) Text(item.detail, 13, p.text2, maxLines = 1)
        }
        LineIcon(Icons.CHEVRON, p.text, modifier = Modifier.alpha(.5f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigTile(item: NowItem, p: Palette, onLong: () -> Unit = {}, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().height(116.dp).background(p.pill, shape).border(1.dp, p.stroke, shape)
            .combinedClickable(onLongClick = onLong, onClick = onClick).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(iconFor(item), p.text, 36.dp, stroke = 1.6f)
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.label, 28, p.text, weight = 500)
            if (item.detail.isNotBlank()) Text(item.detail, 15, p.text2)
        }
    }
}

@Composable
private fun HiddenRow(ui: UiState, p: Palette, onOpen: () -> Unit) {
    val names = ui.hiddenApps.take(2).joinToString(", ") + if (ui.hiddenApps.size > 2) " +${ui.hiddenApps.size - 2}" else ""
    Row(
        Modifier.fillMaxWidth().alpha(.9f).dashedBorder(p.text2, 16.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(Icons.LOCK, p.text, 16.dp, modifier = Modifier.alpha(.6f))
        Spacer(Modifier.width(8.dp))
        Text("$names hidden", 14, p.text2, maxLines = 1, modifier = Modifier.weight(1f))
        val label = ui.openAnywaySeconds?.let { "Opening in $it s" } ?: "Open anyway (10 s)"
        Text(label, 14, p.text, weight = 500, underline = ui.openAnywaySeconds == null, modifier = Modifier.clickable(onClick = onOpen))
    }
}

private fun Modifier.dashedBorder(color: Color, radius: Dp) = this.then(Modifier.drawBehindDashed(color, radius))

@Composable
private fun Bubble(text: String, p: Palette, dot: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    Row(
        Modifier.background(p.card, shape).border(1.dp, p.stroke, shape).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp).animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) { Box(Modifier.size(8.dp).background(Color(HINT_DOT), CircleShape)); Spacer(Modifier.width(8.dp)) }
        Text(text, 14, p.text, weight = 500, maxLines = 3)
    }
}

@Composable
private fun ProposalCard(vm: MomentViewModel, ui: UiState, p: Palette) {
    val prop = ui.proposal ?: return
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 6.dp)
    Column(
        Modifier.fillMaxWidth().shadow(24.dp, shape, ambientColor = Color(0x1433261A), spotColor = Color(0x1433261A))
            .background(p.card, shape).border(1.dp, p.stroke, shape).padding(20.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!ui.editing) {
            Text(prop.question, 19, p.text, weight = 500, lineHeight = 26)
            Text(prop.explanation, 14, p.text2, lineHeight = 20)
        } else {
            Text("Name", 13, p.text2)
            BasicTextField(
                prop.context.name, { vm.editProposal(name = it) }, singleLine = true,
                textStyle = TextStyle(fontFamily = DmSans, fontSize = 19.sp, fontWeight = FontWeight(500), color = p.text),
                cursorBrush = SolidColor(p.text),
                modifier = Modifier.fillMaxWidth().border(1.dp, p.stroke, RoundedCornerShape(12.dp)).padding(12.dp),
            )
            prop.context.items.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(iconFor(item), p.text2, 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(item.label, 15, p.text, modifier = Modifier.weight(1f), maxLines = 1)
                    LineIcon(Icons.CLOSE, p.text2, 18.dp, modifier = Modifier.clickable { vm.editProposal(removeItem = i) })
                }
            }
            if (prop.context.hide.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(Icons.LOCK, p.text2, 14.dp); Spacer(Modifier.width(6.dp))
                Text(prop.context.hide.joinToString(", "), 13, p.text2, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            Button("Accept", Btn.Primary, p) { vm.acceptProposal() }
            Button(if (ui.editing) "Done" else "Edit", Btn.Secondary, p) { vm.toggleEdit() }
            Button("Dismiss", Btn.Ghost, p) { vm.dismissProposal() }
        }
    }
}

/** The agent's staged changes: one card, one decision. */
@Composable
private fun PlanCard(vm: MomentViewModel, ui: UiState, p: Palette) {
    val plan = ui.plan ?: return
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 6.dp)
    Column(
        Modifier.fillMaxWidth().shadow(24.dp, shape, ambientColor = Color(0x1433261A), spotColor = Color(0x1433261A))
            .background(p.card, shape).border(1.dp, p.stroke, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(plan.reply, 18, p.text, weight = 500, lineHeight = 25, maxLines = 4)
        plan.steps.forEachIndexed { i, st ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(22.dp).border(1.5.dp, p.stroke, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", 12, p.text, weight = 500)
                }
                Spacer(Modifier.width(10.dp))
                Text(st.label, 14, p.text2, lineHeight = 19, maxLines = 3, modifier = Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            Button(if (plan.steps.size > 1) "Accept all" else "Accept", Btn.Primary, p) { vm.acceptPlan() }
            Button("Dismiss", Btn.Ghost, p) { vm.dismissPlan() }
        }
    }
}

enum class Btn { Primary, Secondary, Ghost }

@Composable
internal fun Button(label: String, style: Btn, p: Palette, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val m = when (style) {
        Btn.Primary -> Modifier.background(p.accentFill, shape)
        Btn.Secondary -> Modifier.background(p.pill, shape).border(BorderStroke(1.dp, p.stroke), shape)
        Btn.Ghost -> Modifier
    }
    Box(m.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(label, 15, when (style) { Btn.Primary -> p.accentOn; Btn.Secondary -> p.text; Btn.Ghost -> p.text2 }, weight = 500)
    }
}

@Composable
private fun SearchResults(vm: MomentViewModel, ui: UiState, p: Palette) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TimetableSearchRow(vm, ui.query, p)
        // Moment's own screens are searchable too.
        val q = ui.query.trim()
        if (q.length >= 2) listOf(
            Triple("Wardrobe", "What your companion wears", { vm.setQuery(""); vm.openWardrobe(true) }),
            Triple("Contexts", "Every moment Moment knows", { vm.setQuery(""); vm.openContexts(true) }),
        ).filter { it.first.startsWith(q, ignoreCase = true) }.forEach { (label, detail, open) ->
            ActionCard(NowItem(label, detail, "moment"), p, onClick = open)
        }
        val list = if (q.isEmpty()) ui.apps.sortedBy { it.label.lowercase() } else ui.results
        list.forEach { app ->
            Row(Modifier.fillMaxWidth().height(48.dp).clickable { vm.launch(app) }, verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, 24, p.text, maxLines = 1)
            }
        }
        if (q.isEmpty()) return@Column
        val shape = RoundedCornerShape(22.dp)
        Row(
            Modifier.fillMaxWidth().background(p.pill, shape).border(1.dp, p.stroke, shape)
                .clickable { vm.ask(ui.query) }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(Icons.SPARKLE, p.text, 20.dp)
            Spacer(Modifier.width(10.dp))
            Text("Ask Moment: “${ui.query.trim()}”", 15, p.text, weight = 500, maxLines = 2)
        }
    }
}

@Composable
private fun SearchBar(vm: MomentViewModel, ui: UiState, p: Palette, onFocus: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(p.pill, shape).border(1.dp, p.stroke, shape).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(Icons.SEARCH, p.text, 20.dp, modifier = Modifier.alpha(.6f))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (ui.query.isEmpty()) Text(if (ui.chatting) "Talk to me…" else "Search apps, or ask", 16, p.text2)
            BasicTextField(
                ui.query, vm::setQuery, singleLine = true,
                textStyle = TextStyle(fontFamily = DmSans, fontSize = 16.sp, color = p.text),
                cursorBrush = SolidColor(p.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val q = ui.query.trim()
                    val exact = ui.results.firstOrNull { it.label.equals(q, true) }
                    val quick = if (ui.chatting) null else exact ?: ui.results.firstOrNull()?.takeIf { q.split(' ').size <= 2 }
                    if (quick != null) vm.launch(quick) else vm.ask(ui.query)
                    focus.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
            )
        }
        if (ui.query.isNotEmpty()) LineIcon(Icons.CLOSE, p.text2, 18.dp, modifier = Modifier.clickable { vm.setQuery(""); focus.clearFocus() })
        else MicButton(vm, ui, p)
    }
}

/** Hold to talk to the companion (Muse Voice Transcribe). */
@Composable
private fun MicButton(vm: MomentViewModel, ui: UiState, p: Palette) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    Box(
        Modifier.size(36.dp).background(if (ui.listening) p.text else Color.Transparent, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) { ask.launch(android.Manifest.permission.RECORD_AUDIO); return@detectTapGestures }
                    vm.startListening()
                    tryAwaitRelease()
                    vm.stopListening()
                })
            },
        contentAlignment = Alignment.Center,
    ) { LineIcon(Icons.MIC, if (ui.listening) p.pill else p.text, 20.dp, modifier = Modifier.alpha(if (ui.listening) 1f else .6f)) }
}

/** Sound-wave rings around the companion while it listens. */
@Composable
private fun ListeningRings(level: Float, color: Color) {
    val t = rememberInfiniteTransition(label = "rings")
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ph")
    Canvas(Modifier.size(96.dp)) {
        val c = Offset(size.width / 2, size.height * 0.62f)
        for (k in 0 until 3) {
            val q = (ph + k / 3f) % 1f
            val r = size.width * (0.42f + 0.25f * q + 0.15f * level)
            drawCircle(color.copy(alpha = (1f - q) * (0.45f + 0.5f * level)), r, c, style = Stroke(2.dp.toPx()))
        }
    }
}

// ---- Backgrounds -------------------------------------------------------------------------------

@Composable
private fun GameGlow() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0x1A000000), Color(0x660A051F), Color(0xE00A031F))))
        val sx = size.width / 412f; val sy = size.height / 915f
        drawCircle(Brush.radialGradient(listOf(Color(0x7322D3FF), Color.Transparent), Offset(380 * sx, 580 * sy), 260 * sx), 260 * sx, Offset(380 * sx, 580 * sy))
        drawCircle(Brush.radialGradient(listOf(Color(0x73FF3FD2), Color.Transparent), Offset(70 * sx, 410 * sy), 240 * sx), 240 * sx, Offset(70 * sx, 410 * sy))
    }
}

@Composable
private fun RainOverlay() {
    // Continuous time (not a looping 0..1 value): drops never jump back when an animation restarts.
    val time by androidx.compose.runtime.produceState(0f) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { value = (it - start) / 1e9f }
    }
    val drops = remember {
        val r = Random(7)
        List(42) { Drop(r.nextFloat(), r.nextFloat(), 25f + r.nextFloat() * 36f, 0.19f + r.nextFloat() * 0.29f, 0.55f + r.nextFloat() * 0.35f) }
    }
    val beads = remember { val r = Random(11); List(9) { Triple(r.nextFloat(), r.nextFloat(), 5f + r.nextFloat() * 7f) } }
    Canvas(Modifier.fillMaxSize()) {
        run { val c = Offset(size.width * .46f, size.height * .28f); val r = size.width * .7f; drawCircle(Brush.radialGradient(listOf(Color(0x40FFFFFF), Color.Transparent), c, r), r, c) }
        val a = Math.toRadians(12.0); val sa = sin(a).toFloat(); val ca = cos(a).toFloat()
        drops.forEach { d ->
            val len = d.h * density
            // Each drop falls a little past the bottom edge, then re-enters above the top, off screen.
            val span = size.height + len * 2
            val y = ((d.y + time * d.speed) % 1f) * span - len
            val x = (((d.x * size.width - y * sa * 0.3f) % size.width) + size.width) % size.width
            drawLine(Color.White.copy(alpha = d.alpha), Offset(x, y), Offset(x - len * sa, y + len * ca), 1.5f * density, StrokeCap.Round)
        }
        beads.forEach { (fx, fy, w) ->
            val c = Offset(fx * size.width, fy * size.height); val rr = w * density / 2
            drawOval(Color.White.copy(alpha = .3f), c - Offset(rr, rr * 1.15f), androidx.compose.ui.geometry.Size(rr * 2, rr * 2.3f))
            drawOval(Color.White.copy(alpha = .5f), c - Offset(rr, rr * 1.15f), androidx.compose.ui.geometry.Size(rr * 2, rr * 2.3f), style = Stroke(1.5f * density))
        }
    }
}

private class Drop(val x: Float, val y: Float, val h: Float, val alpha: Float, val speed: Float)

@Composable
private fun NightStars() {
    val stars = remember { val r = Random(3); List(40) { Triple(r.nextFloat(), r.nextFloat() * .6f, r.nextFloat()) } }
    val t = rememberInfiniteTransition(label = "stars")
    val tw by t.animateFloat(.3f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "tw")
    Canvas(Modifier.fillMaxSize()) {
        stars.forEach { (x, y, k) -> drawCircle(Color.White.copy(alpha = (.15f + .5f * k) * (if (k > .5f) tw else 1f)), (0.8f + k * 1.2f) * density, Offset(x * size.width, y * size.height)) }
    }
}

/** Another app's widget, hosted by Moment. Remove it from "This screen" (long-press the background). */
@Composable
private fun WidgetSlot(vm: MomentViewModel, id: Int) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c -> vm.widgets.view(c, id).also { v -> (v.parent as? android.view.ViewGroup)?.removeView(v) } },
        modifier = Modifier.fillMaxWidth().height(vm.widgets.heightDp(id).dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)),
    )
}
