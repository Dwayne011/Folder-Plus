package com.widgetsplus.devices

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
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
class DevicesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
        // Placed (also sent after the phone restarts and after this app updates).
        WidgetListeners.setDevices(context, true)
        scheduleRefresh(context)
    }

    override fun onEnabled(context: Context) {
        WidgetListeners.setDevices(context, true)
        scheduleRefresh(context)
    }

    override fun onDisabled(context: Context) {
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

        fun placedIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, DevicesWidgetProvider::class.java))

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

        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            // No colours are set here: the layout's colour resources follow the phone's theme.
            val v = RemoteViews(context.packageName, R.layout.widget_devices)

            if (!BluetoothDevices.hasPermission(context)) {
                v.setTextViewText(R.id.dev_head, context.getString(R.string.devices_header_connected))
                v.setViewVisibility(R.id.dev_row, View.GONE)
                v.setViewVisibility(R.id.dev_message, View.VISIBLE)
                v.setTextViewText(R.id.dev_message, context.getString(R.string.devices_need_permission))
                val setup = Intent(context, DevicesSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(R.id.dev_root, PendingIntent.getActivity(context, 2, setup, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(appWidgetId, v)
                return
            }

            v.setViewVisibility(R.id.dev_row, View.VISIBLE)
            v.setViewVisibility(R.id.dev_message, View.GONE)

            val on = BluetoothDevices.isOn(context)
            val devices = if (on) BluetoothDevices.connectedWithBattery(context).take(3) else emptyList()

            // A tap opens Bluetooth settings; with Bluetooth on but nothing connected (only this
            // phone shows), it opens the phone's Battery page instead.
            val phoneOnly = on && devices.isEmpty()
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
                        !on -> R.string.devices_header_off
                        devices.isEmpty() -> R.string.devices_header_none
                        else -> R.string.devices_header_connected
                    },
                ),
            )
            // Three or two devices; one device beside this phone; or this phone alone.
            val phone = Column(context.getString(R.string.devices_this_phone), BluetoothDevices.phoneLevel(context), DeviceDrawing.PHONE, 0)
            val columns = buildList {
                devices.forEach { add(Column(it.name, it.battery, DevicesDraw.drawingFor(it.kind), DevicesDraw.iconFor(it.kind))) }
                if (devices.size <= 1) add(phone)
            }
            SLOTS.forEachIndexed { i, ids -> fill(context, v, ids, columns.getOrNull(i)) }
            manager.updateAppWidget(appWidgetId, v)
        }

        /** One column of the widget: a drawing with a bar, or a ring with an icon. */
        private class Column(val name: String, val level: Int, val drawing: DeviceDrawing?, val icon: Int)

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

        private fun fill(context: Context, v: RemoteViews, s: SlotIds, c: Column?) {
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
