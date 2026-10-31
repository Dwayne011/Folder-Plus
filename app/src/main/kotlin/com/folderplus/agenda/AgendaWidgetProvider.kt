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
 * Calendar & Agenda widget: a frosted plate holding a natively scrolling list with
 * one row per day (date card + events). This class only draws; the launcher scrolls.
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
        when (intent.action) {
            // The calendar provider announces changes; reload every placed agenda.
            Intent.ACTION_PROVIDER_CHANGED, ACTION_REFRESH -> notifyDataChanged(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.folderplus.agenda.REFRESH"

        fun placedIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, AgendaWidgetProvider::class.java))

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            placedIds(context).forEach { render(context, manager, it) }
        }

        fun notifyDataChanged(context: Context) {
            val ids = placedIds(context)
            if (ids.isNotEmpty()) AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(ids, R.id.agenda_list)
        }

        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val palette = AgendaPalette(context)
            val views = RemoteViews(context.packageName, R.layout.widget_agenda)
            AgendaPalette.tint(views, R.id.agenda_plate, palette.plate, AgendaPalette.PLATE_ALPHA)
            views.setInt(R.id.agenda_hairline, "setImageAlpha", AgendaPalette.HAIRLINE_ALPHA)
            views.setTextColor(R.id.agenda_empty, palette.onPlate)

            if (!CalendarAgenda.hasPermission(context)) {
                views.setViewVisibility(R.id.agenda_list, View.GONE)
                views.setViewVisibility(R.id.agenda_empty, View.VISIBLE)
                views.setTextViewText(R.id.agenda_empty, context.getString(R.string.agenda_need_permission))
                val setup = Intent(context, AgendaSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(
                    R.id.agenda_root,
                    PendingIntent.getActivity(context, appWidgetId, setup, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
            } else {
                views.setViewVisibility(R.id.agenda_list, View.VISIBLE)
                views.setTextViewText(R.id.agenda_empty, context.getString(R.string.agenda_empty))
                val adapter = Intent(context, AgendaWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
                @Suppress("DEPRECATION")
                views.setRemoteAdapter(R.id.agenda_list, adapter)
                views.setEmptyView(R.id.agenda_list, R.id.agenda_empty)
                // Items fill in the event or day to open. The template must be mutable for
                // that, and (Android 14) a mutable PendingIntent must name its component, so
                // it targets our trampoline, which forwards to the Calendar app.
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
                val trampoline = Intent(context, AgendaOpenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val template = PendingIntent.getActivity(context, appWidgetId, trampoline, flags)
                views.setPendingIntentTemplate(R.id.agenda_list, template)
            }
            manager.updateAppWidget(appWidgetId, views)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.agenda_list)
        }
    }
}
