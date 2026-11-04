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

    override fun onResume() {
        super.onResume()
        vm.refreshApps()
        vm.recompute()
    }
}
