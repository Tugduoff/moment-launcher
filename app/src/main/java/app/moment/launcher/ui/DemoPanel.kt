package app.moment.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.moment.launcher.MomentViewModel
import app.moment.launcher.UiState
import app.moment.launcher.ai.MuseClient
import app.moment.launcher.data.Place

fun Modifier.drawBehindDashed(color: Color, radius: Dp) = drawBehind {
    val r = radius.toPx()
    drawRoundRect(
        color, cornerRadius = CornerRadius(r, r),
        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

private data class ClockPreset(val label: String, val y: Int, val m: Int, val d: Int, val h: Int, val min: Int)

private val PRESETS = listOf(
    ClockPreset("Tue 08:47", 2026, 9, 27, 8, 47),
    ClockPreset("Tue 11:58", 2026, 9, 27, 11, 58),
    ClockPreset("Tue 15:30", 2026, 9, 27, 15, 30),
    ClockPreset("Tue 18:05", 2026, 9, 27, 18, 5),
    ClockPreset("Fri 21:40", 2026, 9, 30, 21, 40),
    ClockPreset("Fri 23:30", 2026, 9, 30, 23, 30),
    ClockPreset("Sun 16:20", 2026, 10, 1, 16, 20),
)

/** Hidden panel to fake time, place and Bluetooth for the demo video. Long-press the clock. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DemoPanel(vm: MomentViewModel, ui: UiState) {
    if (!ui.demoOpen) return
    ModalBottomSheet(onDismissRequest = { vm.openDemo(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val ink = Color(0xFF2A2622); val ink2 = Color(0xFF8A837A)
            Text("Demo panel", 20, ink, weight = 500)
            Text("${ui.museStatus} · ${if (MuseClient.hasKey) "key present" else "no key"} · context: ${ui.context?.name}", 12, ink2)

            Text("Time", 14, ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(ui.demo.clockOffsetMs == 0L, { vm.liveClock() }, { Text("Live", 13, ink) })
                PRESETS.forEach { pr -> FilterChip(false, { vm.setClock(pr.y, pr.m, pr.d, pr.h, pr.min) }, { Text(pr.label, 13, ink) }) }
            }
            Text("Place (labels only, never coordinates)", 14, ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Place.entries.forEach { pl -> FilterChip(ui.demo.place == pl, { vm.setDemo { it.copy(place = pl) } }, { Text(pl.label, 13, ink) }) }
            }
            Toggle("Car Bluetooth connected", ui.demo.carBluetooth) { v -> vm.setDemo { it.copy(carBluetooth = v) } }
            Toggle("Headphones connected", ui.demo.headphones) { v -> vm.setDemo { it.copy(headphones = v) } }
            Toggle("Raining", ui.demo.raining) { v -> vm.setDemo { it.copy(raining = v) } }

            Text("Data", 14, ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.seedVisits() }) { Text("Seed 3 lunches at 17 bd Jourdan", 13, ink) }
                OutlinedButton({ vm.classifyAppsIfNeeded(force = true) }) { Text("Sort my apps with Muse", 13, ink) }
                OutlinedButton({ vm.resetAll() }) { Text("Reset everything", 13, ink) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, 15, Color(0xFF2A2622), modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}
