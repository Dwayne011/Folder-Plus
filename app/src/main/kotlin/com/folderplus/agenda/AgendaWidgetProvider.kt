package com.folderplus.agenda

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.folderplus.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Agenda widget. Draws the resting frame of the agenda design (pinned date card for
 * today, the first days of the feed) and opens the interactive agenda popup on tap.
 */
class AgendaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { updateIds(context, appWidgetManager, appWidgetIds) } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val pending = goAsync()
        scope.launch {
            try { updateIds(context, appWidgetManager, intArrayOf(appWidgetId)) } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_PROVIDER_CHANGED || intent.action == ACTION_REFRESH) {
            val pending = goAsync()
            scope.launch {
                try { updateAll(context) } finally { pending.finish() }
            }
        }
    }

    private class Group(
        val root: Int, val bg: Int, val more: Int, val free: Int, val freeOutline: Int, val freeText: Int,
        val slots: List<Slot>,
    )

    private class Slot(val root: Int, val bg: Int, val badge: Int, val icon: Int, val whenText: Int, val title: Int)

    companion object {
        const val ACTION_REFRESH = "com.folderplus.agenda.REFRESH"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val groups = listOf(
            Group(
                R.id.g1, R.id.g1_bg, R.id.g1_more, R.id.g1_free, R.id.g1_free_outline, R.id.g1_free_text,
                listOf(
                    Slot(R.id.g1_ev1, R.id.g1_ev1_bg, R.id.g1_ev1_badge, R.id.g1_ev1_icon, R.id.g1_ev1_when, R.id.g1_ev1_title),
                    Slot(R.id.g1_ev2, R.id.g1_ev2_bg, R.id.g1_ev2_badge, R.id.g1_ev2_icon, R.id.g1_ev2_when, R.id.g1_ev2_title),
                ),
            ),
            Group(
                R.id.g2, R.id.g2_bg, R.id.g2_more, R.id.g2_free, R.id.g2_free_outline, R.id.g2_free_text,
                listOf(
                    Slot(R.id.g2_ev1, R.id.g2_ev1_bg, R.id.g2_ev1_badge, R.id.g2_ev1_icon, R.id.g2_ev1_when, R.id.g2_ev1_title),
                    Slot(R.id.g2_ev2, R.id.g2_ev2_bg, R.id.g2_ev2_badge, R.id.g2_ev2_icon, R.id.g2_ev2_when, R.id.g2_ev2_title),
                ),
            ),
        )

        fun placedIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, AgendaWidgetProvider::class.java))

        suspend fun updateAll(context: Context) {
            updateIds(context, AppWidgetManager.getInstance(context), placedIds(context))
        }

        fun updateAllAsync(context: Context) {
            val appContext = context.applicationContext
            scope.launch { updateAll(appContext) }
        }

        private suspend fun updateIds(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val days = withContext(Dispatchers.IO) { CalendarAgenda.load(context) }
            ids.forEach { manager.updateAppWidget(it, build(context, days, it)) }
        }

        private fun build(context: Context, days: List<AgendaDay>, appWidgetId: Int): RemoteViews {
            val palette = AgendaPalette(context)
            val views = RemoteViews(context.packageName, R.layout.widget_agenda)
            AgendaPalette.tint(views, R.id.agenda_plate, palette.plate, AgendaPalette.PLATE_ALPHA)
            views.setInt(R.id.agenda_hairline, "setImageAlpha", AgendaPalette.HAIRLINE_ALPHA)
            views.setTextColor(R.id.agenda_empty, palette.onPlate)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            if (!CalendarAgenda.hasPermission(context)) {
                views.setViewVisibility(R.id.agenda_content, View.GONE)
                views.setViewVisibility(R.id.agenda_empty, View.VISIBLE)
                views.setTextViewText(R.id.agenda_empty, context.getString(R.string.agenda_need_permission))
                val setup = Intent(context, AgendaSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(R.id.agenda_root, PendingIntent.getActivity(context, appWidgetId, setup, flags))
                return views
            }

            // Any tap opens the interactive agenda popup.
            val open = Intent(context, AgendaPopupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(R.id.agenda_root, PendingIntent.getActivity(context, appWidgetId, open, flags))

            if (days.isEmpty()) {
                views.setViewVisibility(R.id.agenda_content, View.GONE)
                views.setViewVisibility(R.id.agenda_empty, View.VISIBLE)
                views.setTextViewText(R.id.agenda_empty, context.getString(R.string.agenda_empty))
                return views
            }
            views.setViewVisibility(R.id.agenda_empty, View.GONE)
            views.setViewVisibility(R.id.agenda_content, View.VISIBLE)

            // Pinned date card: the first day of the feed (today).
            val first = days[0]
            AgendaPalette.tint(views, R.id.date_bg, palette.card, AgendaPalette.CARD_ALPHA)
            views.setViewVisibility(R.id.date_ring, if (first.isToday) View.VISIBLE else View.GONE)
            if (first.isToday) AgendaPalette.tint(views, R.id.date_ring, palette.accent, 255)
            views.setTextViewText(R.id.date_dow, AgendaFormat.dow(first.date))
            views.setTextViewText(R.id.date_num, first.date.dayOfMonth.toString())
            views.setTextViewText(R.id.date_mon, AgendaFormat.mon(first.date))
            views.setTextColor(R.id.date_dow, palette.onCardVar)
            views.setTextColor(R.id.date_num, palette.onCard)
            views.setTextColor(R.id.date_mon, palette.onCardVar)

            groups.forEachIndexed { index, g ->
                val day = days.getOrNull(index)
                if (day == null) {
                    views.setViewVisibility(g.root, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(g.root, View.VISIBLE)
                AgendaPalette.tint(views, g.bg, palette.group, AgendaPalette.GROUP_ALPHA)
                g.slots.forEachIndexed { i, slot ->
                    val event = day.events.getOrNull(i)
                    if (event == null) {
                        views.setViewVisibility(slot.root, View.GONE)
                        return@forEachIndexed
                    }
                    views.setViewVisibility(slot.root, View.VISIBLE)
                    AgendaPalette.tint(views, slot.bg, palette.card, AgendaPalette.CARD_ALPHA)
                    AgendaPalette.tint(views, slot.badge, palette.badge, 255)
                    views.setImageViewResource(slot.icon, CalendarAgenda.iconFor(event.title))
                    views.setInt(slot.icon, "setColorFilter", palette.onCard)
                    views.setTextViewText(slot.whenText, AgendaFormat.whenLabel(context, day.date, event))
                    views.setTextViewText(slot.title, event.title)
                    views.setTextColor(slot.whenText, palette.onCardVar)
                    views.setTextColor(slot.title, palette.onCard)
                }
                val extra = day.events.size - g.slots.size
                views.setViewVisibility(g.more, if (extra > 0) View.VISIBLE else View.GONE)
                if (extra > 0) {
                    views.setTextViewText(g.more, context.getString(R.string.agenda_more, extra))
                    views.setTextColor(g.more, palette.onCardVar)
                }
                val free = day.events.isEmpty()
                views.setViewVisibility(g.free, if (free) View.VISIBLE else View.GONE)
                if (free) {
                    AgendaPalette.tint(views, g.freeOutline, palette.onCard, AgendaPalette.OUTLINE_ALPHA)
                    views.setTextColor(g.freeText, palette.onCardVar)
                }
            }
            return views
        }
    }
}
