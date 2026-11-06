package com.widgetsplus.devices

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * What the devices widget listens for, switched on only while one is placed (WidgetListeners):
 * a Bluetooth device connecting, disconnecting or reporting a new battery level, Bluetooth
 * turning on or off, and the phone starting up, which clears the 15-minute refresh. A burst of
 * these, as when earbuds connect, makes one redraw.
 */
class DevicesEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DevicesWidgetProvider.ACTION_BATTERY_LEVEL_CHANGED -> deviceOf(intent)?.let { d ->
                DevicePrefs.setCachedLevel(context, d.address, intent.getIntExtra(DevicesWidgetProvider.EXTRA_BATTERY_LEVEL, -1))
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> deviceOf(intent)?.let { DevicePrefs.setCachedLevel(context, it.address, -1) }
            Intent.ACTION_BOOT_COMPLETED -> DevicesWidgetProvider.scheduleRefresh(context)
            BluetoothDevice.ACTION_ACL_CONNECTED,
            DevicesWidgetProvider.ACTION_ADAPTER_STATE_CHANGED,
            DevicesWidgetProvider.ACTION_A2DP_STATE,
            DevicesWidgetProvider.ACTION_HEADSET_STATE -> Unit
            else -> return
        }
        DevicesWidgetProvider.updateSoon(this, context)
    }

    private fun deviceOf(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
