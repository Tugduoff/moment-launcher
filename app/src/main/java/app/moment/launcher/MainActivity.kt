package app.moment.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.moment.launcher.ui.DemoPanel
import app.moment.launcher.ui.HomeScreen
import app.moment.launcher.ui.palette

class MainActivity : ComponentActivity() {
    private val vm: MomentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val dark = ui.context?.let { palette(it.look).dark } ?: false
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            HomeScreen(vm, ui)
            DemoPanel(vm, ui)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDemo(intent)
    }

    /**
     * Scriptable demo controls (debug aid for recording the video), e.g.
     * adb shell am start -n app.moment.launcher/.MainActivity --es demo "reset;seed;clock:2026-10-27-11-58;place:JOURDAN"
     */
    private fun handleDemo(intent: android.content.Intent?) {
        val script = intent?.getStringExtra("demo") ?: return
        script.split(";").map { it.trim() }.forEach { cmd ->
            val arg = cmd.substringAfter(":", "")
            when (cmd.substringBefore(":")) {
                "reset" -> vm.resetAll()
                "seed" -> vm.seedVisits()
                "live" -> vm.liveClock()
                "clock" -> arg.split("-").map { it.toInt() }.let { (y, mo, d, h, mi) -> vm.setClock(y, mo - 1, d, h, mi) }
                "place" -> vm.setDemo { it.copy(place = app.moment.launcher.data.Place.valueOf(arg)) }
                "car" -> vm.setDemo { it.copy(carBluetooth = arg == "1") }
                "hp" -> vm.setDemo { it.copy(headphones = arg == "1") }
                "rain" -> vm.setDemo { it.copy(raining = arg == "1") }
                "tap" -> vm.tapCompanion()
                "ask" -> vm.ask(arg)
                "accept" -> vm.acceptProposal()
                "dismiss" -> vm.dismissProposal()
                "query" -> vm.setQuery(arg)
                "openanyway" -> vm.openAnyway()
                "wear" -> vm.wear(arg)
                "undress" -> vm.undress()
                "acceptplan" -> vm.acceptPlan()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshApps()
        vm.recompute()
    }
}
