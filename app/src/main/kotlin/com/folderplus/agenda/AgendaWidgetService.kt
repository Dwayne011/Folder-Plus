package com.folderplus.agenda

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.folderplus.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Supplies the day rows for the agenda widget's list. */
class AgendaWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = AgendaRemoteViewsFactory(applicationContext)
}

class AgendaRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var days: List<AgendaDay> = emptyList()
    private lateinit var palette: AgendaPalette

    private val slots = listOf(
        Slot(R.id.ev1, R.id.ev1_bg, R.id.ev1_badge, R.id.ev1_icon, R.id.ev1_when, R.id.ev1_title),
        Slot(R.id.ev2, R.id.ev2_bg, R.id.ev2_badge, R.id.ev2_icon, R.id.ev2_when, R.id.ev2_title),
        Slot(R.id.ev3, R.id.ev3_bg, R.id.ev3_badge, R.id.ev3_icon, R.id.ev3_when, R.id.ev3_title),
    )

    private class Slot(val root: Int, val bg: Int, val badge: Int, val icon: Int, val whenText: Int, val title: Int)

    override fun onCreate() {
        palette = AgendaPalette(context)
    }

    override fun onDataSetChanged() {
        palette = AgendaPalette(context)
        days = CalendarAgenda.load(context)
    }

    override fun onDestroy() {
        days = emptyList()
    }

    override fun getCount(): Int = days.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = days.getOrNull(position)?.date?.toEpochDay() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val day = days[position]
        val zone = ZoneId.systemDefault()
        val locale = Locale.getDefault()
        val views = RemoteViews(context.packageName, R.layout.widget_agenda_day)

        // Date card
        AgendaPalette.tint(views, R.id.day_date_bg, palette.card, AgendaPalette.CARD_ALPHA)
        views.setViewVisibility(R.id.day_today_ring, if (day.isToday) View.VISIBLE else View.GONE)
        if (day.isToday) AgendaPalette.tint(views, R.id.day_today_ring, palette.accent, 255)
        views.setTextViewText(R.id.day_dow, day.date.format(DateTimeFormatter.ofPattern("EEE", locale)))
        views.setTextViewText(R.id.day_num, day.date.dayOfMonth.toString())
        // Rows with one event (or none) are short, so the numeral steps down to fit the card.
        val compact = day.events.size <= 1
        views.setTextViewTextSize(R.id.day_num, TypedValue.COMPLEX_UNIT_SP, if (compact) 34f else 56f)
        views.setTextViewTextSize(R.id.day_dow, TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 16f)
        views.setTextViewTextSize(R.id.day_mon, TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 16f)
        views.setTextViewText(R.id.day_mon, day.date.format(DateTimeFormatter.ofPattern("MMM", locale)))
        views.setTextColor(R.id.day_dow, palette.onCardVar)
        views.setTextColor(R.id.day_num, palette.onCard)
        views.setTextColor(R.id.day_mon, palette.onCardVar)
        val dayStart = day.date.atStartOfDay(zone).toInstant().toEpochMilli()
        views.setOnClickFillInIntent(
            R.id.day_date,
            Intent().setData(Uri.parse("content://com.android.calendar/time/$dayStart")),
        )

        // Event group
        AgendaPalette.tint(views, R.id.day_group_bg, palette.group, AgendaPalette.GROUP_ALPHA)
        val timeFormat = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a", locale)
        val dayLabel = day.date.format(DateTimeFormatter.ofPattern("EEE d", locale)).uppercase(locale)
        slots.forEachIndexed { index, slot ->
            val event = day.events.getOrNull(index)
            if (event == null) {
                views.setViewVisibility(slot.root, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(slot.root, View.VISIBLE)
            AgendaPalette.tint(views, slot.bg, palette.card, AgendaPalette.CARD_ALPHA)
            AgendaPalette.tint(views, slot.badge, palette.badge, 255)
            views.setImageViewResource(slot.icon, CalendarAgenda.iconFor(event.title))
            views.setInt(slot.icon, "setColorFilter", palette.onCard)
            val time = if (event.allDay) context.getString(R.string.agenda_all_day)
            else Instant.ofEpochMilli(event.begin).atZone(zone).format(timeFormat).uppercase(locale)
            views.setTextViewText(slot.whenText, "$dayLabel - $time")
            views.setTextViewText(slot.title, event.title)
            views.setTextColor(slot.whenText, palette.onCardVar)
            views.setTextColor(slot.title, palette.onCard)
            views.setOnClickFillInIntent(
                slot.root,
                Intent()
                    .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
                    .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay),
            )
        }

        val extra = day.events.size - slots.size
        views.setViewVisibility(R.id.day_more, if (extra > 0) View.VISIBLE else View.GONE)
        if (extra > 0) {
            views.setTextViewText(R.id.day_more, context.getString(R.string.agenda_more, extra))
            views.setTextColor(R.id.day_more, palette.onCardVar)
            views.setOnClickFillInIntent(R.id.day_more, Intent().setData(Uri.parse("content://com.android.calendar/time/$dayStart")))
        }

        val free = day.events.isEmpty()
        views.setViewVisibility(R.id.day_free, if (free) View.VISIBLE else View.GONE)
        if (free) {
            AgendaPalette.tint(views, R.id.day_free_outline, palette.onCard, AgendaPalette.OUTLINE_ALPHA)
            views.setTextColor(R.id.day_free_text, palette.onCardVar)
            views.setOnClickFillInIntent(R.id.day_free, Intent().setData(Uri.parse("content://com.android.calendar/time/$dayStart")))
        }
        return views
    }
}
