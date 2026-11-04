package app.moment.launcher.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phone and body signals that stay on the phone: today's steps (the hardware step counter,
 * no Health Connect, no account) and the battery. Only the numbers reach the companion.
 */
class Body(private val ctx: Context) : SensorEventListener {
    data class Battery(val percent: Int, val charging: Boolean)

    private val prefs = ctx.getSharedPreferences("moment_body", Context.MODE_PRIVATE)
    private val _steps = MutableStateFlow<Int?>(null)
    /** Steps since midnight, or null when there is no step counter or no permission yet. */
    val steps: StateFlow<Int?> = _steps
    private var listening = false

    /** Starts listening; call again after the ACTIVITY_RECOGNITION permission is granted. */
    fun start() {
        if (listening) return
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val counter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        listening = runCatching { sm.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL) }.getOrDefault(false)
    }

    override fun onSensorChanged(e: SensorEvent) {
        // The counter counts since boot: keep the first value seen today as the baseline.
        val total = e.values[0].toInt()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        var base = prefs.getInt("base_$today", -1)
        if (base < 0 || total < base) { base = total; prefs.edit().clear().putInt("base_$today", base).apply() }
        _steps.value = total - base
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun battery(): Battery? {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0) return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return Battery(level * 100 / scale, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }
}
