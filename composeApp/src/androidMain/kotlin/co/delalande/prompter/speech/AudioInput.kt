package co.delalande.prompter.speech

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord

internal object AudioInput {

    private const val MAX_16BIT = 32767.0

    fun isExternalDevice(device: AudioDeviceInfo?): Boolean {
        if (device == null) return false
        return when (device.type) {
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            -> true
            else -> false
        }
    }

    fun routeToExternalMic(context: Context, record: AudioRecord): AudioDeviceInfo? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return null
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val external = devices.firstOrNull { isExternalDevice(it) } ?: return null
        record.setPreferredDevice(external)
        return external
    }

    fun levelFromRms(rms: Double): Float = levelFromRms(rms, MAX_16BIT)
}
