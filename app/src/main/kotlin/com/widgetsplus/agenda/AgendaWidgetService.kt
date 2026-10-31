package com.widgetsplus.agenda

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.widgetsplus.R

/** Supplies the day cards for the agenda widget's stack. */
class AgendaWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        AgendaRemoteViewsFactory(applicationContext, intent.getIntExtra(AgendaWidgetProvider.EXTRA_SLOTS, 3))
}

class AgendaRemoteViewsFactory(private val context: Context, private val slotCount: Int) : RemoteViewsService.RemoteViewsFactory {
    private var days: List<AgendaDay> = emptyList()
    private lateinit var palette: AgendaPalette

    private class Slot(val root: Int, val bg: Int, val badge: Int, val icon: Int, val whenText: Int, val title: Int, val loc: Int)

    private val slots = listOf(
        Slot(R.id.ev1, R.id.ev1_bg, R.id.ev1_badge, R.id.ev1_icon, R.id.ev1_when, R.id.ev1_title, R.id.ev1_loc),
        Slot(R.id.ev2, R.id.ev2_bg, R.id.ev2_badge, R.id.ev2_icon, R.id.ev2_when, R.id.ev2_title, R.id.ev2_loc),
        Slot(R.id.ev3, R.id.ev3_bg, R.id.ev3_badge, R.id.ev3_icon, R.id.ev3_when, R.id.ev3_title, R.id.ev3_loc),
        Slot(R.id.ev4, R.id.ev4_bg, R.id.ev4_badge, R.id.ev4_icon, R.id.ev4_when, R.id.ev4_title, R.id.ev4_loc),
        Slot(R.id.ev5, R.id.ev5_bg, R.id.ev5_badge, R.id.ev5_icon, R.id.ev5_when, R.id.ev5_title, R.id.ev5_loc),
    )

    override fun onCreate() { palette = AgendaPalette(context) }

    override fun onDataSetChanged() {
        palette = AgendaPalette(context)
        days = CalendarAgenda.load(context)
    }

    override fun onDestroy() { days = emptyList() }
    override fun getCount(): Int = days.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = days.getOrNull(position)?.date?.toEpochDay() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val day = days[position]
        val views = RemoteViews(context.packageName, R.layout.widget_agenda_day)
        val visibleSlots = slotCount.coerceIn(1, slots.size)

        // Date card (opaque: cards in a stack must not show the next day through)
        AgendaPalette.tint(views, R.id.day_date_bg, palette.card, 255)
        views.setViewVisibility(R.id.day_today_ring, if (day.isToday) View.VISIBLE else View.GONE)
        if (day.isToday) AgendaPalette.tint(views, R.id.day_today_ring, palette.accent, 255)
        views.setTextViewText(R.id.day_dow, AgendaFormat.dow(day.date))
        views.setTextViewText(R.id.day_num, day.date.dayOfMonth.toString())
        views.setTextViewText(R.id.day_mon, AgendaFormat.mon(day.date))
        views.setTextColor(R.id.day_dow, palette.onCardVar)
        views.setTextColor(R.id.day_num, palette.onCard)
        views.setTextColor(R.id.day_mon, palette.onCardVar)
        views.setOnClickFillInIntent(R.id.day_date, Intent().setData(AgendaFormat.dayIntent(day.date).data))

        // Event group
        AgendaPalette.tint(views, R.id.day_group_bg, palette.group, 255)
        slots.forEachIndexed { index, slot ->
            val event = day.events.getOrNull(index)
            if (event == null || index >= visibleSlots) {
                views.setViewVisibility(slot.root, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(slot.root, View.VISIBLE)
            AgendaPalette.tint(views, slot.bg, palette.card, 255)
            AgendaPalette.tint(views, slot.badge, palette.badge, 255)
            views.setImageViewResource(slot.icon, CalendarAgenda.iconFor(event.title))
            views.setInt(slot.icon, "setColorFilter", palette.onCard)
            views.setTextViewText(slot.whenText, AgendaFormat.whenLabel(context, day.date, event))
            views.setTextViewText(slot.title, event.title)
            views.setTextColor(slot.whenText, palette.onCardVar)
            views.setTextColor(slot.title, palette.onCard)
            views.setTextColor(slot.loc, palette.onCardVar)
            if (event.location.isNotEmpty()) {
                views.setViewVisibility(slot.loc, View.VISIBLE)
                views.setTextViewText(slot.loc, event.location)
            } else {
                views.setViewVisibility(slot.loc, View.GONE)
            }
            val fill = AgendaFormat.eventIntent(event)
            views.setOnClickFillInIntent(slot.root, Intent().setData(fill.data).putExtras(fill.extras ?: android.os.Bundle()))
        }

        val extra = day.events.size - visibleSlots
        views.setViewVisibility(R.id.day_more_wrap, if (extra > 0) View.VISIBLE else View.GONE)
        if (extra > 0) {
            views.setViewVisibility(R.id.day_more, View.VISIBLE)
            views.setTextViewText(R.id.day_more, context.getString(R.string.agenda_more, extra))
            views.setTextColor(R.id.day_more, palette.onCardVar)
            AgendaPalette.tint(views, R.id.day_more_bg, palette.card, 255)
            views.setOnClickFillInIntent(R.id.day_more_wrap, Intent().setData(AgendaFormat.moreUri(day.date)))
        }

        val free = day.events.isEmpty()
        views.setViewVisibility(R.id.day_free, if (free) View.VISIBLE else View.GONE)
        if (free) {
            AgendaPalette.tint(views, R.id.day_free_outline, palette.onCard, AgendaPalette.OUTLINE_ALPHA)
            views.setTextColor(R.id.day_free_text, palette.onCardVar)
            views.setOnClickFillInIntent(R.id.day_free, Intent().setData(AgendaFormat.dayIntent(day.date).data))
        }
        return views
    }
}
