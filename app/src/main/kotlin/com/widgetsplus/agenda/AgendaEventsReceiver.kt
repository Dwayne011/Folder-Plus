package com.widgetsplus.agenda

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * What the calendar widget listens for, switched on only while one is placed (WidgetListeners):
 * an event added, changed or removed (the calendar's own change broadcast, sent about a second
 * after an edit on the phone and within 30 seconds of a sync), the clock or time zone changing,
 * and the phone starting up, which clears the midnight alarm.
 */
class AgendaEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PROVIDER_CHANGED -> AgendaWidgetProvider.notifyDataChanged(context)
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED -> {
                // "Today" may be a different day now, and the midnight alarm needs setting again.
                AgendaWidgetProvider.scheduleMidnight(context)
                AgendaWidgetProvider.updateAll(context)
                AgendaWidgetProvider.notifyDataChanged(context)
            }
        }
    }
}
