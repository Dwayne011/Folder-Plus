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
import com.widgetsplus.core.agenda.AgendaCardShape
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
            AgendaCardShape.entries.getOrElse(intent.getIntExtra(AgendaWidgetProvider.EXTRA_SHAPE, 0)) { AgendaCardShape.FULL },
            intent.getBooleanExtra(AgendaWidgetProvider.EXTRA_ONE_ROW, false),
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
    /** Smaller sizes (approved mockup): a narrow date card, or the date above the events. */
    private val shape: AgendaCardShape = AgendaCardShape.FULL,
    /** One row high: every event is one line. */
    private val oneRow: Boolean = false,
) : RemoteViewsService.RemoteViewsFactory {
    private var days: List<AgendaDay> = emptyList()
    private lateinit var palette: AgendaPalette
    private val sized = cardHeightDp > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    private var scale = 1f
    private val density = context.resources.displayMetrics.density
    private val whenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font("google-sans-text", 500) }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font("google-sans-text", 700) }
    private val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font("google-sans-text", 500) }
    private val dayNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font("google-sans", 700); letterSpacing = -0.02f }

    private class Slot(
        val root: Int, val bg: Int, val badge: Int, val icon: Int, val whenText: Int, val title: Int, val loc: Int, val dot: Int,
        /** The full row's content, and the all-day strip shown instead of it. */
        val full: Int, val slim: Int, val slimTitle: Int, val slimTag: Int,
    )

    private val slots = listOf(
        Slot(
            R.id.ev1, R.id.ev1_bg, R.id.ev1_badge, R.id.ev1_icon, R.id.ev1_when, R.id.ev1_title, R.id.ev1_loc, R.id.ev1_dot,
            R.id.ev1_full, R.id.ev1_slim, R.id.ev1_slim_title, R.id.ev1_slim_tag,
        ),
        Slot(
            R.id.ev2, R.id.ev2_bg, R.id.ev2_badge, R.id.ev2_icon, R.id.ev2_when, R.id.ev2_title, R.id.ev2_loc, R.id.ev2_dot,
            R.id.ev2_full, R.id.ev2_slim, R.id.ev2_slim_title, R.id.ev2_slim_tag,
        ),
        Slot(
            R.id.ev3, R.id.ev3_bg, R.id.ev3_badge, R.id.ev3_icon, R.id.ev3_when, R.id.ev3_title, R.id.ev3_loc, R.id.ev3_dot,
            R.id.ev3_full, R.id.ev3_slim, R.id.ev3_slim_title, R.id.ev3_slim_tag,
        ),
        Slot(
            R.id.ev4, R.id.ev4_bg, R.id.ev4_badge, R.id.ev4_icon, R.id.ev4_when, R.id.ev4_title, R.id.ev4_loc, R.id.ev4_dot,
            R.id.ev4_full, R.id.ev4_slim, R.id.ev4_slim_title, R.id.ev4_slim_tag,
        ),
        Slot(
            R.id.ev5, R.id.ev5_bg, R.id.ev5_badge, R.id.ev5_icon, R.id.ev5_when, R.id.ev5_title, R.id.ev5_loc, R.id.ev5_dot,
            R.id.ev5_full, R.id.ev5_slim, R.id.ev5_slim_title, R.id.ev5_slim_tag,
        ),
    )

    override fun onCreate() { palette = AgendaPalette(context) }

    override fun onDataSetChanged() {
        palette = AgendaPalette(context)
        scale = if (sized) AgendaPrefs.textScale(context) else 1f
        days = CalendarAgenda.load(context)
    }

    override fun onDestroy() { days = emptyList() }
    override fun getCount(): Int = days.size
    override fun getViewTypeCount(): Int = 2
    override fun getItemId(position: Int): Long = days.getOrNull(position)?.date?.toEpochDay() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        // Fresh every card, so cards drawn after a theme change pick up its colours.
        palette = AgendaPalette(context)
        val day = days[position]
        if (sized && shape != AgendaCardShape.FULL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return compactCard(day)
        val views = RemoteViews(context.packageName, R.layout.widget_agenda_day)

        // Which rows show: sized to the text (titles on two lines above 100% when they need it,
        // locations on two lines whenever they need it) and fitted to the card; or, on the
        // fallback stack, the fixed number of 44dp rows.
        val candidates = day.events.take(slots.size)
        val twoLines = candidates.map { sized && AgendaRows.titlesWrap(scale) && needsTwoLines(it.title) }
        val locationLines = candidates.map { e ->
            when {
                e.location.isEmpty() -> 0
                // Too wide for one line, or already two lines in the calendar ("The O2" + address).
                sized && ('\n' in e.location || tooWide(e.location, locationPaint, 11f)) -> 2
                else -> 1
            }
        }
        // A row with a second line is as tall as its text, measured from the font (lines are
        // taller than the text size), plus even padding above and below.
        val whenLine = lineDp(whenPaint, 10f)
        val titleLine = lineDp(titlePaint, 12.5f)
        val locationLine = lineDp(locationPaint, 11f)
        // All-day events are slim strips on the sized stack (approved option A).
        val slim = candidates.map { sized && it.allDay }
        val rowHeights = candidates.indices.map { i ->
            if (slim[i]) return@map AgendaRows.allDayRowDp(scale)
            val text = whenLine + (if (twoLines[i]) 2 else 1) * titleLine + locationLines[i] * locationLine
            AgendaRows.rowDp(scale, text, hasSecondLine = twoLines[i] || locationLines[i] == 2)
        }
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
        // Colours go by resource, so the launcher draws them in the phone's light or dark theme.
        val weekend = day.date.dayOfWeek == DayOfWeek.SATURDAY || day.date.dayOfWeek == DayOfWeek.SUNDAY
        val face = if (weekend) R.color.agenda_accent else R.color.agenda_card
        val ring = when {
            !day.isToday -> face
            weekend -> R.color.agenda_card
            else -> R.color.agenda_accent
        }
        palette.tintRes(views, R.id.day_date_bg, face)
        palette.tintRes(views, R.id.day_today_ring, ring)
        views.setTextViewText(R.id.day_dow, AgendaFormat.dow(day.date))
        views.setTextViewText(R.id.day_num, day.date.dayOfMonth.toString())
        // On the blue weekend face the text is the dark on-badge colour, in dark mode too.
        palette.textRes(views, R.id.day_dow, if (weekend) R.color.agenda_on_badge_var else R.color.agenda_on_card_var)
        palette.textRes(views, R.id.day_num, if (weekend) R.color.agenda_on_badge else R.color.agenda_on_card)

        // Pills on the date card: the month (top) opens the next 30 days; Today (bottom, every
        // day but today) shows today's card again. On a blue weekend face they are pale.
        val pill = if (weekend) R.color.agenda_card else R.color.agenda_badge
        val onPill = if (weekend) R.color.agenda_on_card else R.color.agenda_on_badge
        views.setTextViewText(R.id.day_month_text, AgendaFormat.mon(day.date))
        palette.textRes(views, R.id.day_month_text, onPill)
        palette.tintRes(views, R.id.day_month_bg, pill)
        // The whole 48dp strip around the pill, not just the pill.
        views.setOnClickFillInIntent(R.id.day_month_tap, Intent().setData(AgendaFormat.monthUri(day.date)))
        views.setViewVisibility(R.id.day_today_tap, if (day.isToday) View.GONE else View.VISIBLE)
        if (!day.isToday) {
            palette.textRes(views, R.id.day_today_text, onPill)
            palette.tintRes(views, R.id.day_today_bg, pill)
            // The whole 48dp strip around the pill, not just the pill.
            views.setOnClickFillInIntent(R.id.day_today_tap, Intent().setData(AgendaFormat.todayUri()))
        }
        views.setOnClickFillInIntent(R.id.day_date, Intent().setData(AgendaFormat.dayIntent(day.date).data))

        // Event group
        palette.tintRes(views, R.id.day_group_bg, R.color.agenda_group)
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
            // All-day events: the row takes on the event's calendar colour, and the dot that
            // otherwise shows that colour is left off (approved option B).
            val tinted = event.allDay && event.color != 0
            if (tinted) palette.tintCalendar(views, slot.bg, event.color) else palette.tintRes(views, slot.bg, R.color.agenda_card)
            val fill = AgendaFormat.eventIntent(event)
            views.setOnClickFillInIntent(slot.root, Intent().setData(fill.data).putExtras(fill.extras ?: android.os.Bundle()))
            // On the sized stack they are slim strips instead (approved option A): the title and
            // "ALL DAY", no badge or place. The fallback stack's rows can't change height.
            views.setViewVisibility(slot.full, if (slim[index]) View.GONE else View.VISIBLE)
            views.setViewVisibility(slot.slim, if (slim[index]) View.VISIBLE else View.GONE)
            if (slim[index]) {
                views.setTextViewText(slot.slimTitle, event.title)
                palette.textRes(views, slot.slimTitle, R.color.agenda_on_card)
                palette.textRes(views, slot.slimTag, R.color.agenda_on_card_var)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setTextViewTextSize(slot.slimTitle, TypedValue.COMPLEX_UNIT_DIP, 12f * scale)
                    views.setTextViewTextSize(slot.slimTag, TypedValue.COMPLEX_UNIT_DIP, 9.5f * scale)
                    val pad = (10f * scale * density).toInt()
                    views.setViewPadding(slot.slim, pad, 0, pad, 0)
                }
                return@forEachIndexed
            }
            palette.tintRes(views, slot.badge, R.color.agenda_badge)
            views.setImageViewResource(slot.icon, CalendarAgenda.iconFor(event.title))
            palette.tintRes(views, slot.icon, R.color.agenda_on_badge)
            views.setTextViewText(slot.whenText, AgendaFormat.whenLabel(context, day.date, event))
            views.setTextViewText(slot.title, event.title)
            palette.textRes(views, slot.whenText, R.color.agenda_on_card_var)
            // Calendar colour: a small dot before the time, in the colour Calendar shows the event in.
            if (event.color != 0 && !tinted) {
                views.setViewVisibility(slot.dot, View.VISIBLE)
                AgendaPalette.tint(views, slot.dot, event.color, 255)
            } else {
                views.setViewVisibility(slot.dot, View.GONE)
            }
            palette.textRes(views, slot.title, R.color.agenda_on_card)
            palette.textRes(views, slot.loc, R.color.agenda_on_card_var)
            if (event.location.isNotEmpty()) {
                views.setViewVisibility(slot.loc, View.VISIBLE)
                views.setTextViewText(slot.loc, event.location)
            } else {
                views.setViewVisibility(slot.loc, View.GONE)
            }
        }

        val extra = day.events.size - minOf(visibleSlots, day.events.size)
        views.setViewVisibility(R.id.day_more_wrap, if (extra > 0) View.VISIBLE else View.GONE)
        if (extra > 0) {
            views.setViewVisibility(R.id.day_more, View.VISIBLE)
            views.setTextViewText(R.id.day_more, context.getString(R.string.agenda_more, extra))
            palette.textRes(views, R.id.day_more, R.color.agenda_on_card_var)
            palette.tintRes(views, R.id.day_more_bg, R.color.agenda_card)
            views.setOnClickFillInIntent(R.id.day_more_wrap, Intent().setData(AgendaFormat.moreUri(day.date)))
        }

        val free = day.events.isEmpty()
        views.setViewVisibility(R.id.day_free, if (free) View.VISIBLE else View.GONE)
        if (free) {
            palette.tintRes(views, R.id.day_free_outline, R.color.agenda_on_card, AgendaPalette.OUTLINE_ALPHA)
            palette.textRes(views, R.id.day_free_text, R.color.agenda_on_card_var)
            views.setOnClickFillInIntent(R.id.day_free, Intent().setData(AgendaFormat.dayIntent(day.date).data))
        }
        return views
    }

    /** The rows of the smaller card (widget_agenda_day_compact.xml). */
    private class CompactSlot(
        val root: Int, val bg: Int, val two: Int, val dot: Int, val whenText: Int, val title: Int,
        val one: Int, val oneDot: Int, val oneTime: Int, val oneTitle: Int, val more: Int, val moreBg: Int, val moreText: Int,
    )

    private val compactSlots = listOf(
        CompactSlot(R.id.ev1, R.id.ev1_bg, R.id.ev1_two, R.id.ev1_dot, R.id.ev1_when, R.id.ev1_title, R.id.ev1_one, R.id.ev1_odot, R.id.ev1_otime, R.id.ev1_otitle, R.id.ev1_more, R.id.ev1_more_bg, R.id.ev1_more_text),
        CompactSlot(R.id.ev2, R.id.ev2_bg, R.id.ev2_two, R.id.ev2_dot, R.id.ev2_when, R.id.ev2_title, R.id.ev2_one, R.id.ev2_odot, R.id.ev2_otime, R.id.ev2_otitle, R.id.ev2_more, R.id.ev2_more_bg, R.id.ev2_more_text),
        CompactSlot(R.id.ev3, R.id.ev3_bg, R.id.ev3_two, R.id.ev3_dot, R.id.ev3_when, R.id.ev3_title, R.id.ev3_one, R.id.ev3_odot, R.id.ev3_otime, R.id.ev3_otitle, R.id.ev3_more, R.id.ev3_more_bg, R.id.ev3_more_text),
        CompactSlot(R.id.ev4, R.id.ev4_bg, R.id.ev4_two, R.id.ev4_dot, R.id.ev4_when, R.id.ev4_title, R.id.ev4_one, R.id.ev4_odot, R.id.ev4_otime, R.id.ev4_otitle, R.id.ev4_more, R.id.ev4_more_bg, R.id.ev4_more_text),
        CompactSlot(R.id.ev5, R.id.ev5_bg, R.id.ev5_two, R.id.ev5_dot, R.id.ev5_when, R.id.ev5_title, R.id.ev5_one, R.id.ev5_odot, R.id.ev5_otime, R.id.ev5_otitle, R.id.ev5_more, R.id.ev5_more_bg, R.id.ev5_more_text),
    )

    /**
     * The smaller sizes' day card (approved mockup). From 3 columns a 60dp date card sits beside
     * the events; at 2 columns the date is one line above them ("THU 5 NOV" on today's card,
     * "MON 9" with a "Today" pill on the others; tapping the date opens the next 30 days, as the
     * month pill does). Timed events are two-line rows (time, then title) and all-day events
     * slim strips in their calendar's colour, with "+N more" below, as on the bigger card. One
     * row high every event is one line, and "+N" sits at the end of the last line. Everything
     * grows with the text size; the taps are the same as on the bigger card.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun compactCard(day: AgendaDay): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_agenda_day_compact)
        val dip = TypedValue.COMPLEX_UNIT_DIP
        val top = shape == AgendaCardShape.TOP
        views.setViewLayoutHeight(R.id.day_root, cardHeightDp, dip)

        // Colours as on the bigger card: a blue face at weekends, the accent ring on today only.
        val weekend = day.date.dayOfWeek == DayOfWeek.SATURDAY || day.date.dayOfWeek == DayOfWeek.SUNDAY
        val face = if (weekend) R.color.agenda_accent else R.color.agenda_card
        val ring = when {
            !day.isToday -> face
            weekend -> R.color.agenda_card
            else -> R.color.agenda_accent
        }
        val onFace = if (weekend) R.color.agenda_on_badge else R.color.agenda_on_card
        val onFaceVar = if (weekend) R.color.agenda_on_badge_var else R.color.agenda_on_card_var
        val pill = if (weekend) R.color.agenda_card else R.color.agenda_badge
        val onPill = if (weekend) R.color.agenda_on_card else R.color.agenda_on_badge
        val pillDp = AgendaRows.compactChipDp(scale)
        val pillText = 9.5f * scale

        var groupDp = cardHeightDp
        if (top) {
            val headDp = AgendaRows.headDp(scale)
            groupDp -= headDp + AgendaRows.HEAD_GAP_DP
            views.setViewVisibility(R.id.day_date, View.GONE)
            views.setViewLayoutMargin(R.id.day_group, RemoteViews.MARGIN_START, 0f, dip)
            views.setViewVisibility(R.id.day_head, View.VISIBLE)
            views.setViewLayoutHeight(R.id.day_head, headDp, dip)
            palette.tintRes(views, R.id.day_head_ring, ring)
            palette.tintRes(views, R.id.day_head_bg, face)
            views.setTextViewText(R.id.day_head_text, AgendaFormat.dow(day.date) + " " + day.date.dayOfMonth)
            views.setTextViewTextSize(R.id.day_head_text, dip, 12f * scale)
            palette.textRes(views, R.id.day_head_text, onFace)
            // The month on today's line; other days have the "Today" pill there instead.
            views.setTextViewText(R.id.day_head_month, if (day.isToday) AgendaFormat.mon(day.date) else "")
            views.setTextViewTextSize(R.id.day_head_month, dip, 12f * scale)
            palette.textRes(views, R.id.day_head_month, onFaceVar)
            views.setOnClickFillInIntent(R.id.day_head, Intent().setData(AgendaFormat.monthUri(day.date)))
            views.setViewVisibility(R.id.day_head_today, if (day.isToday) View.GONE else View.VISIBLE)
            if (!day.isToday) {
                views.setViewLayoutHeight(R.id.day_head_today, pillDp, dip)
                views.setTextViewTextSize(R.id.day_head_today_text, dip, pillText)
                palette.textRes(views, R.id.day_head_today_text, onPill)
                palette.tintRes(views, R.id.day_head_today_bg, pill)
                views.setOnClickFillInIntent(R.id.day_head_today, Intent().setData(AgendaFormat.todayUri()))
            }
        } else {
            views.setViewVisibility(R.id.day_head, View.GONE)
            palette.tintRes(views, R.id.day_date_bg, face)
            palette.tintRes(views, R.id.day_today_ring, ring)
            views.setTextViewText(R.id.day_dow, AgendaFormat.dow(day.date))
            views.setTextViewText(R.id.day_num, day.date.dayOfMonth.toString())
            views.setTextViewTextSize(R.id.day_dow, dip, (if (oneRow) 11f else 12f) * scale)
            views.setTextViewTextSize(R.id.day_num, dip, fitNumber(day.date.dayOfMonth.toString(), (if (oneRow) 26f else 34f) * scale, NARROW_NUMBER_MAX_DP))
            palette.textRes(views, R.id.day_dow, onFaceVar)
            palette.textRes(views, R.id.day_num, onFace)
            views.setOnClickFillInIntent(R.id.day_date, Intent().setData(AgendaFormat.dayIntent(day.date).data))
            // Month pill at the top (next 30 days); "Today" at the bottom on other days, when
            // the card is two rows high. The weekday and date sit centred between them.
            val pillMargin = if (oneRow) 6f else 8f
            views.setTextViewText(R.id.day_month_text, AgendaFormat.mon(day.date))
            views.setTextViewTextSize(R.id.day_month_text, dip, pillText)
            palette.textRes(views, R.id.day_month_text, onPill)
            palette.tintRes(views, R.id.day_month_bg, pill)
            views.setViewLayoutHeight(R.id.day_month_pill, pillDp, dip)
            views.setViewLayoutMargin(R.id.day_month_pill, RemoteViews.MARGIN_TOP, pillMargin, dip)
            views.setViewLayoutHeight(R.id.day_month_tap, pillMargin + pillDp + 8f, dip)
            views.setOnClickFillInIntent(R.id.day_month_tap, Intent().setData(AgendaFormat.monthUri(day.date)))
            val todayPill = !day.isToday && !oneRow
            views.setViewVisibility(R.id.day_today_tap, if (todayPill) View.VISIBLE else View.GONE)
            if (todayPill) {
                views.setTextViewTextSize(R.id.day_today_text, dip, pillText)
                palette.textRes(views, R.id.day_today_text, onPill)
                palette.tintRes(views, R.id.day_today_bg, pill)
                views.setViewLayoutHeight(R.id.day_today_pill, pillDp, dip)
                views.setViewLayoutHeight(R.id.day_today_tap, pillMargin + pillDp + 8f, dip)
                views.setOnClickFillInIntent(R.id.day_today_tap, Intent().setData(AgendaFormat.todayUri()))
            }
            val pad = { dp: Float -> (dp * density).toInt() }
            val above = pillMargin + pillDp + 2f
            val below = when {
                todayPill -> above
                oneRow -> 6f
                else -> above
            }
            views.setViewPadding(R.id.day_date_mid, pad(3f), pad(above), pad(3f), pad(below))
        }

        palette.tintRes(views, R.id.day_group_bg, R.color.agenda_group)
        val events = day.events
        val shown: Int
        val oneLine: List<Boolean>
        val heights: List<Float>
        if (oneRow) {
            shown = minOf(events.size, AgendaRows.linesThatFit(groupDp, scale), compactSlots.size)
            oneLine = events.map { true }
            heights = events.map { AgendaRows.lineDp(scale) }
            views.setInt(R.id.day_group_list, "setGravity", android.view.Gravity.CENTER_VERTICAL)
        } else {
            oneLine = events.map { it.allDay }
            heights = events.take(compactSlots.size).map { if (it.allDay) AgendaRows.lineDp(scale) else AgendaRows.compactRowDp(scale) }
            shown = AgendaRows.fit(
                groupDp, heights, events.size, scale,
                AgendaRows.COMPACT_PADDING_DP, AgendaRows.COMPACT_GAP_DP, AgendaRows.compactChipDp(scale),
            )
        }
        val extra = events.size - minOf(shown, events.size)

        compactSlots.forEachIndexed { index, slot ->
            val event = events.getOrNull(index)
            if (event == null || index >= shown) {
                views.setViewVisibility(slot.root, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(slot.root, View.VISIBLE)
            views.setViewLayoutHeight(slot.root, heights[index], dip)
            val line = oneLine[index]
            // One-line strips are pills; two-line rows have the card's corners. Shape, then tint.
            views.setImageViewResource(slot.bg, if (line) R.drawable.agenda_shape_chip else R.drawable.agenda_shape_card_c)
            val tinted = event.allDay && event.color != 0
            if (tinted) palette.tintCalendar(views, slot.bg, event.color) else palette.tintRes(views, slot.bg, R.color.agenda_card)
            val fill = AgendaFormat.eventIntent(event)
            views.setOnClickFillInIntent(slot.root, Intent().setData(fill.data).putExtras(fill.extras ?: android.os.Bundle()))
            views.setViewVisibility(slot.two, if (line) View.GONE else View.VISIBLE)
            views.setViewVisibility(slot.one, if (line) View.VISIBLE else View.GONE)
            val dotColour = if (event.color != 0 && !tinted) event.color else 0
            if (line) {
                // One line: an all-day strip (the title), or a timed event (dot, time, title).
                val timed = !event.allDay
                views.setViewVisibility(slot.oneDot, if (timed && dotColour != 0) View.VISIBLE else View.GONE)
                if (timed && dotColour != 0) AgendaPalette.tint(views, slot.oneDot, dotColour, 255)
                views.setViewVisibility(slot.oneTime, if (timed) View.VISIBLE else View.GONE)
                if (timed) {
                    views.setTextViewText(slot.oneTime, AgendaFormat.time(context, event.begin))
                    views.setTextViewTextSize(slot.oneTime, dip, 10f * scale)
                    palette.textRes(views, slot.oneTime, R.color.agenda_on_card_var)
                }
                views.setTextViewText(slot.oneTitle, event.title)
                views.setTextViewTextSize(slot.oneTitle, dip, 11.5f * scale)
                palette.textRes(views, slot.oneTitle, R.color.agenda_on_card)
                // One row high: what doesn't fit is "+N" at the end of the last line.
                val moreHere = oneRow && extra > 0 && index == shown - 1
                views.setViewVisibility(slot.more, if (moreHere) View.VISIBLE else View.GONE)
                views.setViewPadding(slot.one, (9f * scale * density).toInt(), 0, ((if (moreHere) 4f else 9f) * scale * density).toInt(), 0)
                if (moreHere) {
                    views.setViewLayoutHeight(slot.more, pillDp, dip)
                    views.setTextViewText(slot.moreText, context.getString(R.string.agenda_more_short, extra))
                    views.setTextViewTextSize(slot.moreText, dip, pillText)
                    palette.textRes(views, slot.moreText, R.color.agenda_on_card)
                    palette.tintRes(views, slot.moreBg, R.color.agenda_group)
                    views.setOnClickFillInIntent(slot.more, Intent().setData(AgendaFormat.moreUri(day.date)))
                }
            } else {
                // Two lines: the dot and time, then the title.
                views.setViewVisibility(slot.dot, if (dotColour != 0) View.VISIBLE else View.GONE)
                if (dotColour != 0) AgendaPalette.tint(views, slot.dot, dotColour, 255)
                views.setTextViewText(slot.whenText, AgendaFormat.time(context, event.begin))
                views.setTextViewTextSize(slot.whenText, dip, 10f * scale)
                palette.textRes(views, slot.whenText, R.color.agenda_on_card_var)
                views.setTextViewText(slot.title, event.title)
                views.setTextViewTextSize(slot.title, dip, 12f * scale)
                palette.textRes(views, slot.title, R.color.agenda_on_card)
            }
        }

        val moreBelow = !oneRow && extra > 0
        views.setViewVisibility(R.id.day_more_wrap, if (moreBelow) View.VISIBLE else View.GONE)
        if (moreBelow) {
            views.setViewLayoutHeight(R.id.day_more_wrap, pillDp, dip)
            views.setTextViewText(R.id.day_more, context.getString(R.string.agenda_more, extra))
            views.setTextViewTextSize(R.id.day_more, dip, pillText)
            palette.textRes(views, R.id.day_more, R.color.agenda_on_card_var)
            palette.tintRes(views, R.id.day_more_bg, R.color.agenda_card)
            views.setOnClickFillInIntent(R.id.day_more_wrap, Intent().setData(AgendaFormat.moreUri(day.date)))
        }

        val free = events.isEmpty()
        views.setViewVisibility(R.id.day_free, if (free) View.VISIBLE else View.GONE)
        if (free) {
            views.setViewLayoutHeight(R.id.day_free, if (oneRow) AgendaRows.lineDp(scale) else AgendaRows.compactRowDp(scale), dip)
            views.setTextViewTextSize(R.id.day_free_text, dip, 11f * scale)
            palette.tintRes(views, R.id.day_free_outline, R.color.agenda_on_card, AgendaPalette.OUTLINE_ALPHA)
            palette.textRes(views, R.id.day_free_text, R.color.agenda_on_card_var)
            views.setOnClickFillInIntent(R.id.day_free, Intent().setData(AgendaFormat.dayIntent(day.date).data))
        }
        return views
    }

    /** [size] for the date number, made smaller if the number would be wider than [maxDp]. */
    private fun fitNumber(text: String, size: Float, maxDp: Float): Float {
        dayNumberPaint.textSize = size * density
        val width = dayNumberPaint.measureText(text) / density
        return if (width > maxDp) size * maxDp / width else size
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

    /** One line's height (no font padding, as in the layout) for [sizeDp] text at this size. */
    private fun lineDp(paint: Paint, sizeDp: Float): Float {
        paint.textSize = sizeDp * scale * density
        val metrics = paint.fontMetrics
        return (metrics.descent - metrics.ascent) / density
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
        /** Inside the narrow date card: 60dp less its 2dp ring and 3dp padding on each side. */
        const val NARROW_NUMBER_MAX_DP = 50f
    }
}
