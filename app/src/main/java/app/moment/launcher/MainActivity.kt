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
import app.moment.launcher.ui.ContextsSheet
import app.moment.launcher.ui.DemoPanel
import app.moment.launcher.ui.HomeScreen
import app.moment.launcher.ui.WardrobeSheet
import app.moment.launcher.ui.palette

class MainActivity : ComponentActivity() {
    private val vm: MomentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSharedPreferences("moment_ui", MODE_PRIVATE).getInt("ground", 0).takeIf { it != 0 }
            ?.let { window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(it)) }
        enableEdgeToEdge()
        // Steps come from the phone's own step counter; it needs the activity permission (asked once).
        val activity = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.body.start() }
        if (android.os.Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) activity.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
        setContent {
            // Widgets: bind (asking the user if Android requires it), then the widget's own setup screen.
            val pending = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }
            val configureWidget = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
                if (r.resultCode == RESULT_OK) vm.widgetAdded(pending.intValue) else vm.widgets.discard(pending.intValue)
            }
            fun finishWidget(id: Int) {
                val info = vm.widgets.manager.getAppWidgetInfo(id)
                if (info?.configure != null) configureWidget.launch(android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(info.configure).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                else vm.widgetAdded(id)
            }
            val bind = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
                if (r.resultCode == RESULT_OK) finishWidget(pending.intValue) else vm.widgets.discard(pending.intValue)
            }
            val ui by vm.ui.collectAsStateWithLifecycle()
            val dark = ui.context?.let { palette(it.look).dark } ?: false
            // The window behind Compose takes the context's ground colour, so a transition back home
            // (from recents, from an app) shows the right colour instead of a flash of the system wallpaper.
            val ground = when (ui.context?.look) {
                app.moment.launcher.data.Look.CAR -> 0xFF121317.toInt(); app.moment.launcher.data.Look.NIGHT -> 0xFF0E1428.toInt()
                app.moment.launcher.data.Look.GAME -> 0xFF6A2BFF.toInt(); app.moment.launcher.data.Look.RAIN -> 0xFF7E8CA2.toInt()
                app.moment.launcher.data.Look.STUDY -> 0xFFECEFF4.toInt(); else -> 0xFFF7F4EE.toInt()
            }
            LaunchedEffect(ground) {
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ground))
                getSharedPreferences("moment_ui", MODE_PRIVATE).edit().putInt("ground", ground).apply()
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            HomeScreen(vm, ui)
            DemoPanel(vm, ui)
            WardrobeSheet(vm, ui)
            ContextsSheet(vm, ui)
            app.moment.launcher.ui.TimetableSheet(vm, ui)
            app.moment.launcher.ui.EditRowSheet(vm, ui)
            app.moment.launcher.ui.BackdropSheet(vm, ui)
            app.moment.launcher.ui.SensesSheet(vm, ui)
            app.moment.launcher.ui.ContactPickerSheet(vm, ui)
            app.moment.launcher.ui.StyleSheet(vm, ui)
            app.moment.launcher.ui.FocusGuardSheet(vm, ui)
            app.moment.launcher.ui.Onboarding(vm, ui)
            app.moment.launcher.ui.WidgetPickerSheet(vm, ui) { info ->
                vm.openWidgetPicker(false)
                val id = vm.widgets.allocate(); pending.intValue = id
                if (vm.widgets.bind(id, info)) finishWidget(id)
                else bind.launch(android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider))
            }
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
                "postcard" -> vm.sharePostcard()
                "weather" -> vm.refreshWeather()
                "timetable" -> app.moment.launcher.ai.Timetable.startFromFile(vm, arg)
                "wardrobe" -> vm.openWardrobe(arg == "1")
                "use" -> vm.useContext(arg)
                "senses" -> vm.openSenses(arg != "0")
                "shake" -> vm.shaken()
                "paint" -> vm.ui.value.context?.let { vm.paintBackdrop(it, arg.ifBlank { null }) }
                "steps" -> vm.setDemo { it.copy(steps = arg.toIntOrNull()) }
                "battery" -> vm.setDemo { it.copy(battery = arg.toIntOrNull()) }
                "contexts" -> vm.openContexts(arg == "1")
                "effect" -> arg.toIntOrNull()?.let { vm.setStyle(app.moment.launcher.ui.Style.current.copy(effect = it)) }
                "style" -> vm.openStyle(arg != "0")
                "brief" -> vm.briefNow(arg == "pm")
                "samples" -> vm.setDemoContexts(arg != "0")
                "music" -> { vm.demoMusic = arg == "1"; vm.recompute() }
                "held" -> vm.demoHeldSummary()
                "widgets" -> vm.openWidgetPicker(arg != "0")
            }
        }
    }

    override fun onStart() { super.onStart(); vm.widgets.startListening(); vm.refreshWidgets() }
    override fun onStop() { super.onStop(); vm.widgets.stopListening() }

    override fun onResume() {
        super.onResume()
        vm.refreshApps()
        vm.recompute()
    }
}
