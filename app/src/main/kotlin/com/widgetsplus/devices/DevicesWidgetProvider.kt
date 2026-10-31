package com.widgetsplus.devices

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import com.widgetsplus.R

/**
 * Connected devices widget: the battery of every connected Bluetooth device that reports one,
 * or this phone's battery and recent-device shortcuts when none is connected.
 *
 * It redraws when a device connects or disconnects, reports a new battery level, or Bluetooth is
 * switched on or off (all delivered to this manifest receiver), and every 15 minutes for this
 * phone's own level, which Android doesn't broadcast to apps in the background.
 */
class DevicesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
        scheduleRefresh(context)
    }

    override fun onEnabled(context: Context) = scheduleRefresh(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(refreshIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_BATTERY_LEVEL_CHANGED -> {
                deviceOf(intent)?.let { d ->
                    DevicePrefs.setCachedLevel(context, d.address, intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1))
                }
                updateAll(context)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                deviceOf(intent)?.let { DevicePrefs.setCachedLevel(context, it.address, -1) }
                updateAll(context)
            }
            BluetoothDevice.ACTION_ACL_CONNECTED, ACTION_ADAPTER_STATE_CHANGED, ACTION_A2DP_STATE, ACTION_HEADSET_STATE, ACTION_REFRESH -> updateAll(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> { scheduleRefresh(context); updateAll(context) }
        }
    }

    private fun deviceOf(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        /** The level each device reports; a system broadcast that third-party apps may receive. */
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        const val ACTION_ADAPTER_STATE_CHANGED = "android.bluetooth.adapter.action.STATE_CHANGED"
        const val ACTION_A2DP_STATE = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        const val ACTION_HEADSET_STATE = "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
        const val ACTION_REFRESH = "com.widgetsplus.devices.REFRESH"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, DevicesWidgetProvider::class.java)).forEach { render(context, manager, it) }
        }

        private fun refreshIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, DevicesWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** This phone's level every ~15 minutes (inexact, so it costs no extra wake-ups). */
        fun scheduleRefresh(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            alarms.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                refreshIntent(context),
            )
        }

        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val pal = DevicesPalette(context)
            val v = RemoteViews(context.packageName, R.layout.widget_devices)
            tint(v, R.id.dev_frame, pal.frame, DevicesPalette.FRAME_ALPHA)
            v.setInt(R.id.dev_hairline, "setImageAlpha", DevicesPalette.HAIRLINE_ALPHA)
            tint(v, R.id.dev_well, pal.well, DevicesPalette.WELL_ALPHA)
            v.setInt(R.id.dev_well_line, "setImageAlpha", DevicesPalette.WELL_LINE_ALPHA)
            v.setTextColor(R.id.dev_head, pal.onWell)
            v.setTextColor(R.id.dev_message, pal.onWellVar)
            listOf(R.id.n3, R.id.n2, R.id.n1, R.id.n0, R.id.dev_message).forEach { v.setViewVisibility(it, View.GONE) }

            val bluetooth = PendingIntent.getActivity(
                context, 1, Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            if (!BluetoothDevices.hasPermission(context)) {
                v.setTextViewText(R.id.dev_head, context.getString(R.string.devices_header_connected))
                v.setViewVisibility(R.id.dev_message, View.VISIBLE)
                v.setTextViewText(R.id.dev_message, context.getString(R.string.devices_need_permission))
                val setup = Intent(context, DevicesSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(R.id.dev_root, PendingIntent.getActivity(context, 2, setup, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(appWidgetId, v)
                return
            }

            v.setOnClickPendingIntent(R.id.dev_root, bluetooth)
            val on = BluetoothDevices.isOn(context)
            val devices = if (on) BluetoothDevices.connectedWithBattery(context).take(3) else emptyList()
            v.setTextViewText(
                R.id.dev_head,
                context.getString(
                    when {
                        !on -> R.string.devices_header_off
                        devices.isEmpty() -> R.string.devices_header_none
                        else -> R.string.devices_header_connected
                    },
                ),
            )
            when (devices.size) {
                3 -> three(context, v, pal, devices)
                2 -> two(context, v, pal, devices)
                1 -> one(context, v, pal, devices[0])
                else -> none(context, v, pal, bluetooth)
            }
            manager.updateAppWidget(appWidgetId, v)
        }

        private fun tint(v: RemoteViews, id: Int, color: Int, alpha: Int) {
            v.setInt(id, "setColorFilter", color or -0x1000000)
            v.setInt(id, "setImageAlpha", alpha)
        }

        private fun pct(level: Int) = "$level%"

        /** Three columns: earbuds get the illustration and a bar; others a ring with their icon. */
        private fun three(context: Context, v: RemoteViews, pal: DevicesPalette, devices: List<ConnectedDevice>) {
            v.setViewVisibility(R.id.n3, View.VISIBLE)
            data class Ids(val ring: Int, val icon: Int, val pctIn: Int, val name: Int, val sub: Int)
            val slots = listOf(
                Ids(R.id.sa_ring, R.id.sa_icon, R.id.sa_pct_in, R.id.sa_name, R.id.sa_sub),
                Ids(R.id.sb_ring, R.id.sb_icon, R.id.sb_pct_in, R.id.sb_name, R.id.sb_sub),
                Ids(R.id.sc_ring, R.id.sc_icon, R.id.sc_pct_in, R.id.sc_name, R.id.sc_sub),
            )
            devices.forEachIndexed { i, d ->
                val s = slots[i]
                v.setTextViewText(s.name, d.name)
                v.setTextColor(s.name, pal.onWell)
                v.setTextViewText(s.sub, pct(d.battery))
                v.setTextColor(s.sub, pal.textFor(d.battery, pal.onWellVar))
                val illustrated = i == 0 && d.kind == DeviceKind.EARBUDS
                if (i == 0) {
                    v.setViewVisibility(R.id.sa_buds_main, if (illustrated) View.VISIBLE else View.GONE)
                    v.setViewVisibility(R.id.sa_buds_detail, if (illustrated) View.VISIBLE else View.GONE)
                    v.setViewVisibility(R.id.sa_bar, if (illustrated) View.VISIBLE else View.GONE)
                    if (illustrated) {
                        tint(v, R.id.sa_buds_main, pal.frame, 255)
                        tint(v, R.id.sa_buds_detail, pal.tone2, 255)
                        v.setImageViewBitmap(R.id.sa_bar, DevicesDraw.bar(context, 72f, 5f, d.battery, pal.track, pal.fillFor(d.battery)))
                    }
                }
                if (illustrated) {
                    listOf(s.ring, s.icon, s.pctIn).forEach { v.setViewVisibility(it, View.GONE) }
                } else {
                    v.setViewVisibility(s.ring, View.VISIBLE)
                    v.setImageViewBitmap(s.ring, DevicesDraw.ring(context, 52f, 5f, d.battery, pal.track, pal.fillFor(d.battery)))
                    // a watch shows its level inside the ring, as in the design; everything else its icon
                    val watch = d.kind == DeviceKind.WATCH
                    v.setViewVisibility(s.pctIn, if (watch) View.VISIBLE else View.GONE)
                    v.setViewVisibility(s.icon, if (watch) View.GONE else View.VISIBLE)
                    if (watch) {
                        v.setTextViewText(s.pctIn, pct(d.battery))
                        v.setTextColor(s.pctIn, pal.textFor(d.battery, pal.onWell))
                    } else {
                        v.setImageViewResource(s.icon, DevicesDraw.iconFor(d.kind))
                        v.setInt(s.icon, "setColorFilter", pal.frame or -0x1000000)
                    }
                }
            }
        }

        /** Two half-width cards: icon, name, big level, bar. */
        private fun two(context: Context, v: RemoteViews, pal: DevicesPalette, devices: List<ConnectedDevice>) {
            v.setViewVisibility(R.id.n2, View.VISIBLE)
            val ids = listOf(
                intArrayOf(R.id.c1_bg, R.id.c1_glyph, R.id.c1_icon, R.id.c1_name, R.id.c1_pct, R.id.c1_bar),
                intArrayOf(R.id.c2_bg, R.id.c2_glyph, R.id.c2_icon, R.id.c2_name, R.id.c2_pct, R.id.c2_bar),
            )
            devices.forEachIndexed { i, d ->
                val r = ids[i]
                val bg = r[0]; val glyph = r[1]; val icon = r[2]; val name = r[3]; val level = r[4]; val bar = r[5]
                tint(v, bg, pal.frame, DevicesPalette.CARD_ALPHA)
                tint(v, glyph, pal.track, 255)
                v.setImageViewResource(icon, DevicesDraw.iconFor(d.kind))
                v.setInt(icon, "setColorFilter", pal.frame or -0x1000000)
                v.setTextViewText(name, d.name)
                v.setTextColor(name, pal.onWell)
                v.setTextViewText(level, pct(d.battery))
                v.setTextColor(level, pal.textFor(d.battery, pal.onWell))
                v.setImageViewBitmap(bar, DevicesDraw.bar(context, 70f, 5f, d.battery, pal.track, pal.fillFor(d.battery)))
            }
        }

        /** One device: its gauge beside this phone's. */
        private fun one(context: Context, v: RemoteViews, pal: DevicesPalette, d: ConnectedDevice) {
            v.setViewVisibility(R.id.n1, View.VISIBLE)
            v.setTextViewText(R.id.n1_name, d.name)
            v.setTextColor(R.id.n1_name, pal.onWell)
            val phone = BluetoothDevices.phoneLevel(context)
            gauge(context, v, pal, R.id.g1_ring, R.id.g1_num, R.id.g1_label, d.battery, context.getString(DevicesDraw.labelFor(d.kind)))
            gauge(context, v, pal, R.id.g2_ring, R.id.g2_num, R.id.g2_label, phone, context.getString(R.string.devices_this_phone))
        }

        private fun gauge(context: Context, v: RemoteViews, pal: DevicesPalette, ring: Int, num: Int, label: Int, level: Int, text: String) {
            v.setImageViewBitmap(ring, DevicesDraw.ring(context, 56f, 5f, level, pal.track, pal.fillFor(level)))
            v.setTextViewText(num, level.toString())
            v.setTextColor(num, pal.textFor(level, pal.onWell))
            v.setTextViewText(label, text)
            v.setTextColor(label, pal.onWellVar)
        }

        /** Nothing connected: this phone's battery and up to two recent devices (tap opens Bluetooth). */
        private fun none(context: Context, v: RemoteViews, pal: DevicesPalette, bluetooth: PendingIntent) {
            v.setViewVisibility(R.id.n0, View.VISIBLE)
            val phone = BluetoothDevices.phoneLevel(context)
            v.setImageViewBitmap(R.id.n0_ring, DevicesDraw.ring(context, 64f, 6f, phone, pal.track, pal.fillFor(phone)))
            v.setTextViewText(R.id.n0_pct, pct(phone))
            v.setTextColor(R.id.n0_pct, pal.textFor(phone, pal.onWell))
            v.setTextColor(R.id.n0_label, pal.onWellVar)
            val recent = DevicePrefs.recent(context).take(2)
            v.setViewVisibility(R.id.n0_shortcuts, if (recent.isEmpty()) View.GONE else View.VISIBLE)
            val rows = listOf(
                intArrayOf(R.id.sc1, R.id.sc1_bg, R.id.sc1_glyph, R.id.sc1_icon, R.id.sc1_name, R.id.sc1_sub),
                intArrayOf(R.id.sc2, R.id.sc2_bg, R.id.sc2_glyph, R.id.sc2_icon, R.id.sc2_name, R.id.sc2_sub),
            )
            rows.forEachIndexed { i, r ->
                val d = recent.getOrNull(i)
                v.setViewVisibility(r[0], if (d == null) View.GONE else View.VISIBLE)
                if (d == null) return@forEachIndexed
                tint(v, r[1], pal.frame, DevicesPalette.PILL_ALPHA)
                tint(v, r[2], pal.track, 255)
                v.setImageViewResource(r[3], DevicesDraw.iconFor(d.kind))
                v.setInt(r[3], "setColorFilter", pal.frame or -0x1000000)
                v.setTextViewText(r[4], d.name)
                v.setTextColor(r[4], pal.onWell)
                v.setTextColor(r[5], pal.onWellVar)
                v.setOnClickPendingIntent(r[0], bluetooth)
            }
        }
    }
}
