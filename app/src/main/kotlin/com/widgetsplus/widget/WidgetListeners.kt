package com.widgetsplus.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.widgetsplus.agenda.AgendaEventsReceiver
import com.widgetsplus.agenda.AgendaWidgetProvider
import com.widgetsplus.devices.DevicesEventsReceiver
import com.widgetsplus.devices.DevicesWidgetProvider

/**
 * The calendar and devices widgets hear about changes (calendar edits, the clock, Bluetooth)
 * through receivers of their own, which are switched on only while such a widget is on the home
 * screen. With none placed, those broadcasts never start this app.
 */
object WidgetListeners {

    fun setAgenda(context: Context, on: Boolean) = set(context, AgendaEventsReceiver::class.java, on)

    fun setDevices(context: Context, on: Boolean) = set(context, DevicesEventsReceiver::class.java, on)

    /** Matches the receivers, and the refresh alarms, to the widgets placed now. */
    fun sync(context: Context) {
        val agenda = AgendaWidgetProvider.placedIds(context).isNotEmpty()
        setAgenda(context, agenda)
        if (agenda) AgendaWidgetProvider.scheduleMidnight(context) else AgendaWidgetProvider.cancelMidnight(context)
        val devices = DevicesWidgetProvider.placedIds(context).isNotEmpty()
        setDevices(context, devices)
        if (devices) DevicesWidgetProvider.scheduleRefresh(context) else DevicesWidgetProvider.cancelRefresh(context)
    }

    private fun set(context: Context, receiver: Class<out BroadcastReceiver>, on: Boolean) {
        val pm = context.packageManager
        val component = ComponentName(context, receiver)
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (pm.getComponentEnabledSetting(component) != state) {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}

/**
 * After this app is updated: the receivers above start switched off in a new version, so turn
 * them back on for the widgets already placed, set the refresh alarms again and redraw.
 */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        WidgetListeners.sync(context)
        AgendaWidgetProvider.updateAll(context)
        AgendaWidgetProvider.notifyDataChanged(context)
        DevicesWidgetProvider.updateAll(context)
    }
}
