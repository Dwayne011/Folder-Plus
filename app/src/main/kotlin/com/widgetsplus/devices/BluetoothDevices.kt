package com.widgetsplus.devices

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Locale

/** What a device is, for its icon and label. Order is the order devices appear in the widget. */
enum class DeviceKind { EARBUDS, HEADPHONES, WATCH, KEYBOARD, MOUSE, GAMEPAD, SPEAKER, CAR, OTHER }

/** A connected Bluetooth device and the battery level it reports (0..100, or -1 if unknown). */
data class ConnectedDevice(val address: String, val name: String, val kind: DeviceKind, val battery: Int)

/**
 * Reads connected Bluetooth devices and their battery levels.
 *
 * Android reports one battery level per device (the level the device itself sends, the same
 * number Bluetooth settings shows). It is read with BluetoothDevice.getBatteryLevel(), which is a
 * system API: hidden from the SDK but allowed for every app on Android 12 to 16 (hidden-API flag
 * "sdk,system-api" from Android 13, "unsupported" on 12) and guarded only by BLUETOOTH_CONNECT.
 * The same level also arrives with the BATTERY_LEVEL_CHANGED broadcast, which is cached as a
 * fallback. Separate earbud left/right/case levels need a privileged permission, so they are not
 * available to this app.
 */
object BluetoothDevices {

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isOn(context: Context): Boolean = try {
        adapter(context)?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    /** Connected devices that report a battery level, in display order. */
    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun connectedWithBattery(context: Context): List<ConnectedDevice> {
        if (!hasPermission(context)) return emptyList()
        val adapter = adapter(context) ?: return emptyList()
        val bonded = try {
            if (!adapter.isEnabled) return emptyList()
            adapter.bondedDevices.orEmpty()
        } catch (e: SecurityException) {
            return emptyList()
        }
        val devices = bonded.filter { isConnected(it) }.map { d ->
            ConnectedDevice(d.address, displayName(d), kindOf(d), batteryLevel(context, d))
        }.filter { it.battery in 0..100 }
        return devices.sortedWith(compareBy<ConnectedDevice> { it.kind.ordinal }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    private fun isConnected(device: BluetoothDevice): Boolean = try {
        BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as Boolean
    } catch (e: Exception) {
        false
    }

    private fun batteryLevel(context: Context, device: BluetoothDevice): Int {
        val level = try {
            BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as Int
        } catch (e: Exception) {
            -1
        }
        return if (level in 0..100) level else DevicePrefs.cachedLevel(context, device.address)
    }

    @SuppressLint("MissingPermission") // only called after hasPermission()
    private fun displayName(device: BluetoothDevice): String {
        val alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else null
        return alias?.takeIf { it.isNotBlank() } ?: device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth device"
    }

    @SuppressLint("MissingPermission")
    private fun kindOf(device: BluetoothDevice): DeviceKind {
        val name = displayName(device).lowercase(Locale.ROOT)
        val byName = when {
            Regex("""\b(buds?|pods?|earbuds?|earphones?)\b""").containsMatchIn(name) -> DeviceKind.EARBUDS
            "watch" in name -> DeviceKind.WATCH
            Regex("""\b(controller|gamepad|joy-?con|dualsense|dualshock)\b""").containsMatchIn(name) ||
                "xbox" in name || "8bitdo" in name -> DeviceKind.GAMEPAD
            "keyboard" in name || Regex("""\bkeys\b""").containsMatchIn(name) -> DeviceKind.KEYBOARD
            "mouse" in name || "mx master" in name || "trackpad" in name -> DeviceKind.MOUSE
            else -> null
        }
        val cls: BluetoothClass? = try { device.bluetoothClass } catch (e: SecurityException) { null }
        val deviceClass = cls?.deviceClass ?: 0
        val byClass = when (cls?.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> when (deviceClass) {
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER, BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> DeviceKind.SPEAKER
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> DeviceKind.CAR
                else -> DeviceKind.HEADPHONES
            }
            BluetoothClass.Device.Major.PERIPHERAL -> when {
                deviceClass and 0x40 != 0 -> DeviceKind.KEYBOARD
                deviceClass and 0x80 != 0 -> DeviceKind.MOUSE
                // peripheral subtype bits 5..2: 0001 joystick, 0010 gamepad
                (deviceClass and 0x3C).let { it == 0x04 || it == 0x08 } -> DeviceKind.GAMEPAD
                else -> DeviceKind.OTHER
            }
            BluetoothClass.Device.Major.WEARABLE -> DeviceKind.WATCH
            else -> null
        }
        return byName ?: byClass ?: DeviceKind.OTHER
    }

    /** This phone's battery, 0..100. */
    fun phoneLevel(context: Context): Int =
        context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: 0
}

/** Battery levels from broadcasts, kept as a fallback for devices that don't answer directly. */
object DevicePrefs {
    private const val FILE = "widgetsplus_devices"
    private const val LEVEL = "level_"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun cachedLevel(context: Context, address: String): Int = prefs(context).getInt(LEVEL + address, -1)

    fun setCachedLevel(context: Context, address: String, level: Int) {
        val edit = prefs(context).edit()
        if (level in 0..100) edit.putInt(LEVEL + address, level) else edit.remove(LEVEL + address)
        edit.apply()
    }
}
