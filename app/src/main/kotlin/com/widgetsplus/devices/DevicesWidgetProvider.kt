package com.widgetsplus.devices

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.widgetsplus.R
import com.widgetsplus.widget.WidgetListeners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Connected devices widget: the battery of every connected Bluetooth device that reports one
 * (up to three), with this phone beside a single device, or this phone alone when none is.
 *
 * It redraws when a device connects or disconnects, reports a new battery level, or Bluetooth is
 * switched on or off (DevicesEventsReceiver, on only while a widget is placed; a burst of these,
 * as when earbuds connect, makes one redraw), and about every 15 minutes for this phone's own
 * level, which Android doesn't broadcast to apps in the background. Neither wakes the phone.
 */
open class DevicesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
        // Placed (also sent after the phone restarts and after this app updates).
        WidgetListeners.setDevices(context, true)
        scheduleRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        // Resized: the narrow and wide arrangements are chosen by size.
        render(context, appWidgetManager, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        WidgetListeners.setDevices(context, true)
        scheduleRefresh(context)
    }

    override fun onDisabled(context: Context) {
        // The last of this size was removed; the other size may still be on the home screen.
        if (placedIds(context).isNotEmpty()) return
        WidgetListeners.setDevices(context, false)
        cancelRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            // The 15-minute refresh: stop it if no devices widget is placed any more.
            if (placedIds(context).isEmpty()) cancelRefresh(context) else updateAll(context)
        }
    }

    companion object {
        /** The level each device reports; a system broadcast that third-party apps may receive. */
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        const val ACTION_ADAPTER_STATE_CHANGED = "android.bluetooth.adapter.action.STATE_CHANGED"
        const val ACTION_A2DP_STATE = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        const val ACTION_HEADSET_STATE = "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
        const val ACTION_REFRESH = "com.widgetsplus.devices.REFRESH"

        /** Every devices widget placed, of either size. */
        fun placedIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(ComponentName(context, DevicesWidgetProvider::class.java)) +
                manager.getAppWidgetIds(ComponentName(context, DevicesWidgetProvider2x2::class.java))
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            placedIds(context).forEach { render(context, manager, it) }
        }

        /** How long to wait for the rest of a burst of Bluetooth events before redrawing. */
        private const val BURST_MS = 800L
        private val burstPending = AtomicBoolean(false)
        private val burstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Redraws once for a burst of Bluetooth events (earbuds connecting send several within a
         * second). Each event stores what it carries before calling this, and the one redraw
         * reads it all.
         */
        fun updateSoon(receiver: BroadcastReceiver, context: Context) {
            if (!burstPending.compareAndSet(false, true)) return
            val pending = receiver.goAsync()
            val appContext = context.applicationContext
            burstScope.launch {
                try {
                    delay(BURST_MS)
                    burstPending.set(false)
                    updateAll(appContext)
                } finally {
                    pending.finish()
                }
            }
        }

        private fun refreshIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, DevicesWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun cancelRefresh(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(refreshIntent(context))
        }

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

        /** What the widget shows, read once and drawn for every size the launcher may use. */
        private class Snapshot(
            val permitted: Boolean,
            val on: Boolean,
            val devices: List<ConnectedDevice>,
            val phoneLevel: Int,
        )

        private fun snapshot(context: Context): Snapshot {
            if (!BluetoothDevices.hasPermission(context)) return Snapshot(false, false, emptyList(), 0)
            val on = BluetoothDevices.isOn(context)
            val devices = if (on) BluetoothDevices.connectedWithBattery(context).take(3) else emptyList()
            return Snapshot(true, on, devices, BluetoothDevices.phoneLevel(context))
        }

        /** Narrower than this (2×2, or a big widget resized narrow) shows the 2×2 arrangement. */
        private const val SMALL_MAX_WIDTH_DP = 240f

        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val snap = snapshot(context)
            val options = manager.getAppWidgetOptions(appWidgetId)
            // Until the launcher gives a size, the 2×2 entry shows the 2×2 arrangement.
            val addedSmall = manager.getAppWidgetInfo(appWidgetId)?.provider?.className == DevicesWidgetProvider2x2::class.java.name
            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                if (!sizes.isNullOrEmpty()) RemoteViews(sizes.take(16).associateWith { build(context, snap, it.width, addedSmall) })
                else build(context, snap, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat(), addedSmall)
            } else {
                build(context, snap, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat(), addedSmall)
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun build(context: Context, snap: Snapshot, widthDp: Float, addedSmall: Boolean): RemoteViews {
            val small = if (widthDp > 0f) widthDp < SMALL_MAX_WIDTH_DP else addedSmall
            val v = RemoteViews(context.packageName, if (small) R.layout.widget_devices_small else R.layout.widget_devices)

            if (!snap.permitted) {
                v.setTextViewText(R.id.dev_head, context.getString(R.string.devices_header_connected))
                if (small) SMALL_GROUPS.forEach { v.setViewVisibility(it, View.GONE) } else v.setViewVisibility(R.id.dev_row, View.GONE)
                v.setViewVisibility(R.id.dev_message, View.VISIBLE)
                v.setTextViewText(R.id.dev_message, context.getString(R.string.devices_need_permission))
                val setup = Intent(context, DevicesSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(R.id.dev_root, PendingIntent.getActivity(context, 2, setup, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                return v
            }
            v.setViewVisibility(R.id.dev_message, View.GONE)

            // A tap opens Bluetooth settings; with Bluetooth on but nothing connected (only this
            // phone shows), it opens the phone's Battery page instead.
            val phoneOnly = snap.on && snap.devices.isEmpty()
            val tap = if (phoneOnly) {
                PendingIntent.getActivity(
                    context, 3, Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getActivity(
                    context, 1, Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            v.setOnClickPendingIntent(R.id.dev_root, tap)
            v.setTextViewText(
                R.id.dev_head,
                context.getString(
                    when {
                        !snap.on -> R.string.devices_header_off
                        snap.devices.isEmpty() -> R.string.devices_header_none
                        else -> R.string.devices_header_connected
                    },
                ),
            )
            val phone = DeviceColumn(context.getString(R.string.devices_this_phone), snap.phoneLevel, DeviceDrawing.PHONE, 0)
            val devices = snap.devices.map { DeviceColumn(it.name, it.battery, DevicesDraw.drawingFor(it.kind), DevicesDraw.iconFor(it.kind)) }
            if (small) {
                // This phone first, then each device (approved 2×2 mockup).
                DevicesSmall.fill(context, v, listOf(phone) + devices, widthDp)
                return v
            }
            v.setViewVisibility(R.id.dev_row, View.VISIBLE)
            // Three or two devices; one device beside this phone; or this phone alone.
            val columns = if (devices.size <= 1) devices + phone else devices
            SLOTS.forEachIndexed { i, ids -> fill(context, v, ids, columns.getOrNull(i)) }
            return v
        }

        private val SMALL_GROUPS = listOf(R.id.ds1, R.id.ds2, R.id.ds3)

        private class SlotIds(
            val root: Int,
            val drawMain: Int, val drawDetail: Int, val drawTop: Int,
            val ringTrack: Int, val ringFill: Int, val ringLow: Int, val icon: Int,
            val name: Int, val sub: Int, val subLow: Int,
            val bar: Int, val barFill: Int, val barLow: Int,
        )

        private val SLOTS = listOf(
            SlotIds(
                R.id.s1, R.id.s1_draw_main, R.id.s1_draw_detail, R.id.s1_draw_top,
                R.id.s1_ring_track, R.id.s1_ring_fill, R.id.s1_ring_low, R.id.s1_icon,
                R.id.s1_name, R.id.s1_sub, R.id.s1_sub_low, R.id.s1_bar, R.id.s1_bar_fill, R.id.s1_bar_low,
            ),
            SlotIds(
                R.id.s2, R.id.s2_draw_main, R.id.s2_draw_detail, R.id.s2_draw_top,
                R.id.s2_ring_track, R.id.s2_ring_fill, R.id.s2_ring_low, R.id.s2_icon,
                R.id.s2_name, R.id.s2_sub, R.id.s2_sub_low, R.id.s2_bar, R.id.s2_bar_fill, R.id.s2_bar_low,
            ),
            SlotIds(
                R.id.s3, R.id.s3_draw_main, R.id.s3_draw_detail, R.id.s3_draw_top,
                R.id.s3_ring_track, R.id.s3_ring_fill, R.id.s3_ring_low, R.id.s3_icon,
                R.id.s3_name, R.id.s3_sub, R.id.s3_sub_low, R.id.s3_bar, R.id.s3_bar_fill, R.id.s3_bar_low,
            ),
        )

        private fun fill(context: Context, v: RemoteViews, s: SlotIds, c: DeviceColumn?) {
            v.setViewVisibility(s.root, if (c == null) View.GONE else View.VISIBLE)
            if (c == null) return
            val low = DevicesLevels.isLow(c.level)
            v.setTextViewText(s.name, c.name)
            val level = "${c.level}%"
            v.setTextViewText(s.sub, level)
            v.setTextViewText(s.subLow, level)
            v.setViewVisibility(s.sub, if (low) View.GONE else View.VISIBLE)
            v.setViewVisibility(s.subLow, if (low) View.VISIBLE else View.GONE)
            val drawing = c.drawing
            if (drawing != null) {
                listOf(s.drawMain to drawing.main, s.drawDetail to drawing.detail, s.drawTop to drawing.top).forEach { (id, res) ->
                    v.setViewVisibility(id, if (res == 0) View.GONE else View.VISIBLE)
                    if (res != 0) v.setImageViewResource(id, res)
                }
                listOf(s.ringTrack, s.ringFill, s.ringLow, s.icon).forEach { v.setViewVisibility(it, View.GONE) }
                v.setViewVisibility(s.bar, View.VISIBLE)
                val bar = DevicesDraw.bar(context, 72f, 5f, c.level)
                v.setImageViewBitmap(if (low) s.barLow else s.barFill, bar)
                v.setViewVisibility(s.barFill, if (low) View.GONE else View.VISIBLE)
                v.setViewVisibility(s.barLow, if (low) View.VISIBLE else View.GONE)
            } else {
                listOf(s.drawMain, s.drawDetail, s.drawTop).forEach { v.setViewVisibility(it, View.GONE) }
                v.setViewVisibility(s.ringTrack, View.VISIBLE)
                v.setImageViewBitmap(s.ringTrack, DevicesDraw.ring(context, 52f, 5f, 100))
                val arc = DevicesDraw.ring(context, 52f, 5f, c.level)
                v.setImageViewBitmap(if (low) s.ringLow else s.ringFill, arc)
                v.setViewVisibility(s.ringFill, if (low) View.GONE else View.VISIBLE)
                v.setViewVisibility(s.ringLow, if (low) View.VISIBLE else View.GONE)
                v.setViewVisibility(s.icon, View.VISIBLE)
                v.setImageViewResource(s.icon, c.icon)
                // keeps the bar's space so this name lines up with its neighbours'
                v.setViewVisibility(s.bar, View.INVISIBLE)
            }
        }
    }
}

/** The connected devices widget's 2×2 entry in the widget list (approved mockup); same widget. */
class DevicesWidgetProvider2x2 : DevicesWidgetProvider()
