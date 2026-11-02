package com.widgetsplus.agenda

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.RequiresApi
import com.widgetsplus.R
import com.widgetsplus.core.agenda.AgendaRows
import java.time.DayOfWeek

/** Supplies the day cards for the agenda widget's stack. */
class AgendaWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        AgendaRemoteViewsFactory(
            applicationContext,
            intent.getIntExtra(AgendaWidgetProvider.EXTRA_SLOTS, 3),
            intent.getFloatExtra(AgendaWidgetProvider.EXTRA_CARD_HEIGHT, 0f),
            intent.getFloatExtra(AgendaWidgetProvider.EXTRA_CARD_WIDTH, 0f),
        )
}

/**
 * [cardHeightDp] > 0: Android 12+ "parked" stack; each card is set to exactly the frame's height
 * so the cards behind it wait out of sight. 0: fallback stack, where every card is opaque.
 *
 * Text size (the calendar settings' slider) applies to the parked stack, where rows can be sized:
 * text, pills and rows grow or shrink with it, titles too long for one line get a second line
 * above 100%, locations too long for one line get a second line at any size, and each card shows
 * as many rows as fit ([AgendaRows]). The fallback stack on Android 8 to 11 keeps the normal size,
 * one-line text and [slotCount] rows.
 */
class AgendaRemoteViewsFactory(
    private val context: Context,
    private val slotCount: Int,
    private val cardHeightDp: Float,
    private val cardWidthDp: Float,
) : RemoteViewsService.RemoteViewsFactory {
    private var days: List<AgendaDay> = emptyList()
    private lateinit var palette: AgendaPalette
    private val sized = cardHeightDp > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    private var scale = 1f
    private val density = context.resources.displayMetrics.density
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font("google-sans-text", 700) }
    private val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font("google-sans-text", 500) }
    private val dayNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font("google-sans", 700); letterSpacing = -0.02f }

    private class Slot(val root: Int, val bg: Int, val badge: Int, val icon: Int, val whenText: Int, val title: Int, val loc: Int, val dot: Int)

    private val slots = listOf(
        Slot(R.id.ev1, R.id.ev1_bg, R.id.ev1_badge, R.id.ev1_icon, R.id.ev1_when, R.id.ev1_title, R.id.ev1_loc, R.id.ev1_dot),
        Slot(R.id.ev2, R.id.ev2_bg, R.id.ev2_badge, R.id.ev2_icon, R.id.ev2_when, R.id.ev2_title, R.id.ev2_loc, R.id.ev2_dot),
        Slot(R.id.ev3, R.id.ev3_bg, R.id.ev3_badge, R.id.ev3_icon, R.id.ev3_when, R.id.ev3_title, R.id.ev3_loc, R.id.ev3_dot),
        Slot(R.id.ev4, R.id.ev4_bg, R.id.ev4_badge, R.id.ev4_icon, R.id.ev4_when, R.id.ev4_title, R.id.ev4_loc, R.id.ev4_dot),
        Slot(R.id.ev5, R.id.ev5_bg, R.id.ev5_badge, R.id.ev5_icon, R.id.ev5_when, R.id.ev5_title, R.id.ev5_loc, R.id.ev5_dot),
    )

    override fun onCreate() { palette = AgendaPalette(context) }

    override fun onDataSetChanged() {
        palette = AgendaPalette(context)
        scale = if (sized) AgendaPrefs.textScale(context) else 1f
        days = CalendarAgenda.load(context)
    }

    override fun onDestroy() { days = emptyList() }
    override fun getCount(): Int = days.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = days.getOrNull(position)?.date?.toEpochDay() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        // Fresh every card, so cards drawn after a theme change pick up its colours.
        palette = AgendaPalette(context)
        val day = days[position]
        val views = RemoteViews(context.packageName, R.layout.widget_agenda_day)

        // Which rows show: sized to the text (titles on two lines above 100% when they need it,
        // locations on two lines whenever they need it) and fitted to the card; or, on the
        // fallback stack, the fixed number of 44dp rows.
        val candidates = day.events.take(slots.size)
        val twoLines = candidates.map { sized && AgendaRows.titlesWrap(scale) && needsTwoLines(it.title) }
        val locationLines = candidates.map { e ->
            when {
                e.location.isEmpty() -> 0
                sized && tooWide(e.location, locationPaint, 11f) -> 2
                else -> 1
            }
        }
        val rowHeights = candidates.mapIndexed { i, _ -> AgendaRows.rowDp(scale, twoLines[i], locationLines[i]) }
        val visibleSlots = if (sized) AgendaRows.fit(cardHeightDp, rowHeights, day.events.size, scale)
        else slotCount.coerceIn(1, slots.size)
        if (sized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) sizeDayCard(views, day.date.dayOfMonth.toString())

        if (cardHeightDp > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(R.id.day_root, cardHeightDp, TypedValue.COMPLEX_UNIT_DIP)
        } else {
            // Fallback stack: the card is opaque (plate-coloured backing around the content),
            // so the next day's card behind it can never show through.
            val pad = (2 * context.resources.displayMetrics.density).toInt()
            views.setInt(R.id.day_root, "setBackgroundColor", palette.plateSolid)
            views.setViewPadding(R.id.day_root, pad, pad, pad, pad)
        }

        // Date card as in the mockup: a 2dp accent ring on today only. Weekends have a blue
        // (accent) face; today's ring on a blue face is pale so it still shows. The ring is a
        // plate behind the face, so on other days it takes the face colour and disappears.
        val weekend = day.date.dayOfWeek == DayOfWeek.SATURDAY || day.date.dayOfWeek == DayOfWeek.SUNDAY
        val face = if (weekend) palette.accent else palette.card
        val ring = when {
            !day.isToday -> face
            weekend -> palette.card
            else -> palette.accent
        }
        AgendaPalette.tint(views, R.id.day_date_bg, face, 255)
        AgendaPalette.tint(views, R.id.day_today_ring, ring, 255)
        views.setTextViewText(R.id.day_dow, AgendaFormat.dow(day.date))
        views.setTextViewText(R.id.day_num, day.date.dayOfMonth.toString())
        views.setTextColor(R.id.day_dow, palette.onCardVar)
        views.setTextColor(R.id.day_num, palette.onCard)

        // Pills on the date card: the month (top) opens the next 30 days; Today (bottom, every
        // day but today) shows today's card again. On a blue weekend face they are pale.
        val pill = if (weekend) palette.card else palette.badge
        views.setTextViewText(R.id.day_month_text, AgendaFormat.mon(day.date))
        views.setTextColor(R.id.day_month_text, palette.onCard)
        AgendaPalette.tint(views, R.id.day_month_bg, pill, 255)
        // The whole 48dp strip around the pill, not just the pill.
        views.setOnClickFillInIntent(R.id.day_month_tap, Intent().setData(AgendaFormat.monthUri(day.date)))
        views.setViewVisibility(R.id.day_today_tap, if (day.isToday) View.GONE else View.VISIBLE)
        if (!day.isToday) {
            views.setTextColor(R.id.day_today_text, palette.onCard)
            AgendaPalette.tint(views, R.id.day_today_bg, pill, 255)
            // The whole 48dp strip around the pill, not just the pill.
            views.setOnClickFillInIntent(R.id.day_today_tap, Intent().setData(AgendaFormat.todayUri()))
        }
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
            if (sized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setViewLayoutHeight(slot.root, rowHeights[index], TypedValue.COMPLEX_UNIT_DIP)
                views.setTextViewTextSize(slot.whenText, TypedValue.COMPLEX_UNIT_DIP, 10f * scale)
                views.setTextViewTextSize(slot.title, TypedValue.COMPLEX_UNIT_DIP, 12.5f * scale)
                views.setTextViewTextSize(slot.loc, TypedValue.COMPLEX_UNIT_DIP, 11f * scale)
                views.setInt(slot.title, "setMaxLines", if (twoLines[index]) 2 else 1)
                views.setInt(slot.loc, "setMaxLines", if (locationLines[index] == 2) 2 else 1)
            }
            AgendaPalette.tint(views, slot.bg, palette.card, 255)
            AgendaPalette.tint(views, slot.badge, palette.badge, 255)
            views.setImageViewResource(slot.icon, CalendarAgenda.iconFor(event.title))
            views.setInt(slot.icon, "setColorFilter", palette.onCard)
            views.setTextViewText(slot.whenText, AgendaFormat.whenLabel(context, day.date, event))
            views.setTextViewText(slot.title, event.title)
            views.setTextColor(slot.whenText, palette.onCardVar)
            // Calendar colour: a small dot before the time, in the colour Calendar shows the event in.
            if (event.color != 0) {
                views.setViewVisibility(slot.dot, View.VISIBLE)
                AgendaPalette.tint(views, slot.dot, event.color, 255)
            } else {
                views.setViewVisibility(slot.dot, View.GONE)
            }
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

        val extra = day.events.size - minOf(visibleSlots, day.events.size)
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

    /** Text sizes on the date card, the pills and "Nothing planned", for [scale]. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun sizeDayCard(views: RemoteViews, dayNumber: String) {
        val dip = TypedValue.COMPLEX_UNIT_DIP
        views.setTextViewTextSize(R.id.day_dow, dip, 16f * scale)
        views.setTextViewTextSize(R.id.day_num, dip, dayNumberSize(dayNumber))
        listOf(R.id.day_month_text, R.id.day_today_text, R.id.day_more).forEach { views.setTextViewTextSize(it, dip, 10.5f * scale) }
        listOf(R.id.day_month_pill, R.id.day_today_pill, R.id.day_more_wrap).forEach { views.setViewLayoutHeight(it, AgendaRows.chipDp(scale), dip) }
        views.setTextViewTextSize(R.id.day_free_text, dip, 11f * scale)
        views.setViewLayoutHeight(R.id.day_free, AgendaRows.baseRowDp(scale), dip)
    }

    /** The date number grows with the text, but never wider than the date card's 92dp inside. */
    private fun dayNumberSize(text: String): Float {
        val size = 56f * scale
        dayNumberPaint.textSize = size * density
        val width = dayNumberPaint.measureText(text) / density
        return if (width > DAY_NUMBER_MAX_DP) size * DAY_NUMBER_MAX_DP / width else size
    }

    /** Whether [title] is too long for one line of the row at this size. */
    private fun needsTwoLines(title: String): Boolean = tooWide(title, titlePaint, 12.5f)

    /** Whether [text], at [sizeDp] scaled to this size, is wider than a row's text column. */
    private fun tooWide(text: String, paint: Paint, sizeDp: Float): Boolean {
        val widthDp = cardWidthDp - ROW_CHROME_DP
        if (widthDp <= 0f) return false
        paint.textSize = sizeDp * scale * density
        return paint.measureText(text) / density > widthDp
    }

    private fun font(family: String, weight: Int): Typeface {
        val base = Typeface.create(family, Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, weight, false)
        else Typeface.create(base, Typeface.BOLD)
    }

    private companion object {
        /** Everything beside a row's title: date card 104, gap 12, group padding 12, row padding 16, badge 32, gap 8. */
        const val ROW_CHROME_DP = 184f
        const val DAY_NUMBER_MAX_DP = 90f
    }
}
