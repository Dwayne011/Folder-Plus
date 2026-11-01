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
import com.widgetsplus.core.devices.DeviceKind
import com.widgetsplus.core.devices.DeviceNames
import java.util.Locale

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
            ConnectedDevice(d.address, displayName(d), kindOf(context, d), batteryLevel(context, d))
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

    /**
     * What a device is: your own choice (set in the widget's settings) first, then its name
     * (common speakers, Sony's WH/WF ranges and so on), then the type it announces over Bluetooth.
     */
    @SuppressLint("MissingPermission") // only called after hasPermission()
    fun kindOf(context: Context, device: BluetoothDevice): DeviceKind =
        DevicePrefs.kindChoice(context, device.address) ?: guessedKind(device)

    /** The kind the widget works out on its own, shown as "Automatic" in the settings. */
    @SuppressLint("MissingPermission") // only called after hasPermission(); a refusal is caught
    fun guessedKind(device: BluetoothDevice): DeviceKind {
        val byName = DeviceNames.kindOf(displayName(device))
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

    /** A paired device, for the settings' "Device types" list. */
    data class Paired(val address: String, val name: String, val guessed: DeviceKind)

    /** Every paired device, by name (empty without the Nearby devices permission or with Bluetooth off). */
    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun paired(context: Context): List<Paired> {
        if (!hasPermission(context)) return emptyList()
        val bonded = try {
            adapter(context)?.bondedDevices.orEmpty()
        } catch (e: SecurityException) {
            return emptyList()
        }
        return bonded.map { Paired(it.address, displayName(it), guessedKind(it)) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    /** This phone's battery, 0..100. */
    fun phoneLevel(context: Context): Int =
        context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: 0
}

/** Battery levels from broadcasts (a fallback for devices that don't answer directly), and the kinds you chose. */
object DevicePrefs {
    private const val FILE = "widgetsplus_devices"
    private const val LEVEL = "level_"
    private const val KIND = "kind_"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun cachedLevel(context: Context, address: String): Int = prefs(context).getInt(LEVEL + address, -1)

    fun setCachedLevel(context: Context, address: String, level: Int) {
        val edit = prefs(context).edit()
        if (level in 0..100) edit.putInt(LEVEL + address, level) else edit.remove(LEVEL + address)
        edit.apply()
    }

    /** The kind you chose for a device (by its Bluetooth address), or null for automatic. */
    fun kindChoice(context: Context, address: String): DeviceKind? =
        prefs(context).getString(KIND + address, null)?.let { name -> DeviceKind.entries.firstOrNull { it.name == name } }

    fun setKindChoice(context: Context, address: String, kind: DeviceKind?) {
        val edit = prefs(context).edit()
        if (kind == null) edit.remove(KIND + address) else edit.putString(KIND + address, kind.name)
        edit.apply()
    }
}
