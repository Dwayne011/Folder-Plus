package com.folderplus.agenda

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.folderplus.R

/**
 * Agenda widget: a frosted plate holding a StackView with one opaque card per day.
 * The launcher animates the swipe between days; this class only draws.
 */
class AgendaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_PROVIDER_CHANGED || intent.action == ACTION_REFRESH) notifyDataChanged(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.folderplus.agenda.REFRESH"
        const val EXTRA_SLOTS = "slots"

        fun placedIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, AgendaWidgetProvider::class.java))

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            placedIds(context).forEach { render(context, manager, it) }
        }

        fun notifyDataChanged(context: Context) {
            val ids = placedIds(context)
            if (ids.isNotEmpty()) AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(ids, R.id.agenda_stack)
        }

        /** Event rows per card: three on a 3-row widget, five when it is taller. */
        private fun slotsFor(options: Bundle): Int {
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            return if (maxHeight >= 250) 5 else 3
        }

        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val palette = AgendaPalette(context)
            val views = RemoteViews(context.packageName, R.layout.widget_agenda)
            AgendaPalette.tint(views, R.id.agenda_plate, palette.plate, AgendaPalette.PLATE_ALPHA)
            views.setInt(R.id.agenda_hairline, "setImageAlpha", AgendaPalette.HAIRLINE_ALPHA)
            views.setTextColor(R.id.agenda_empty, palette.onPlate)
            val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            if (!CalendarAgenda.hasPermission(context)) {
                views.setViewVisibility(R.id.agenda_stack, View.GONE)
                views.setViewVisibility(R.id.agenda_empty, View.VISIBLE)
                views.setTextViewText(R.id.agenda_empty, context.getString(R.string.agenda_need_permission))
                val setup = Intent(context, AgendaSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(R.id.agenda_root, PendingIntent.getActivity(context, appWidgetId, setup, immutable))
            } else {
                views.setViewVisibility(R.id.agenda_stack, View.VISIBLE)
                views.setTextViewText(R.id.agenda_empty, context.getString(R.string.agenda_empty))
                val adapter = Intent(context, AgendaWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .putExtra(EXTRA_SLOTS, slotsFor(manager.getAppWidgetOptions(appWidgetId)))
                adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
                @Suppress("DEPRECATION")
                views.setRemoteAdapter(R.id.agenda_stack, adapter)
                views.setEmptyView(R.id.agenda_stack, R.id.agenda_empty)
                // Rows fill in what to open. The template must be mutable, and on Android 14 a
                // mutable PendingIntent must be explicit, so it targets our trampoline.
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
                val trampoline = Intent(context, AgendaTapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setPendingIntentTemplate(R.id.agenda_stack, PendingIntent.getActivity(context, appWidgetId, trampoline, flags))
            }
            manager.updateAppWidget(appWidgetId, views)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.agenda_stack)
        }
    }
}
