package app.moment.launcher.system

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Devices(val headphones: Boolean = false, val car: Boolean = false, val carName: String? = null)

/**
 * Real device signals, no permission needed: audio output devices tell us when headphones are
 * plugged or paired, and when a car's Bluetooth audio connects (matched by device name).
 */
class RealSignals(ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val _devices = MutableStateFlow(Devices())
    val devices: StateFlow<Devices> = _devices

    private val carWords = listOf(
        "car", "auto", "kit", "sync", "uconnect", "mylink", "tesla", "renault", "peugeot", "citroen", "toyota", "volkswagen",
        "vw ", "bmw", "audi", "mercedes", "ford", "kia", "hyundai", "nissan", "honda", "mazda", "skoda", "seat", "fiat", "opel", "volvo", "dacia",
    )
    private val headphoneTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
    )

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
    }

    fun start() { audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper())); refresh() }
    fun stop() = audio.unregisterAudioDeviceCallback(callback)

    private fun refresh() {
        val outs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outs.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER }
        val car = bt.firstOrNull { d -> val n = d.productName.toString().lowercase() + " "; carWords.any { it in n } }
        val phones = outs.any { it.type in headphoneTypes } || bt.any { it != car && it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        _devices.value = Devices(headphones = phones, car = car != null, carName = car?.productName?.toString())
    }
}
