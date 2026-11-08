package com.widgetsplus.core.agenda

import kotlin.math.ceil

/**
 * How tall the calendar widget's event rows are at a text size, and how many fit on a day card.
 * Mirrors widget_agenda_day.xml: at 100% a row is 44dp (a 32dp badge beside 10dp time, 12.5dp
 * title and 11dp location text), rows sit 6dp apart in a group with 6dp padding, and the
 * "+N more" chip is 18dp, 6dp below the last row. Text, and so rows and the chip, grow with the
 * size. Above 100% a title too long for one line gets a second line; at any size a location too
 * long for one line gets a second line. A row with a second line is as tall as its text (measured
 * from the font) plus [TALL_ROW_PADDING_DP] above and below, so the text sits evenly inside it.
 * All-day events are slim 24dp strips ([allDayRowDp]), so more of the day's timed events fit.
 */
object AgendaRows {
    const val MAX_ROWS = 5
    const val GROUP_PADDING_DP = 12f
    const val GAP_DP = 6f
    const val CHIP_DP = 18f

    /** Never shorter than the 32dp badge allows. */
    private const val MIN_ROW_DP = 40f
    /** Room around the text block inside a row. */
    private const val SLACK_DP = 8f
    private const val WHEN_LINE_DP = 11f
    private const val TITLE_LINE_DP = 14f
    private const val LOCATION_LINE_DP = 11f

    /** Titles may wrap onto a second line only when the text is bigger than normal. */
    fun titlesWrap(scale: Float): Boolean = scale > 1.001f

    /** A row's height at [scale] with a one-line title and a location (44dp at 100%). */
    fun baseRowDp(scale: Float): Float =
        maxOf(MIN_ROW_DP, up(SLACK_DP + (WHEN_LINE_DP + TITLE_LINE_DP + LOCATION_LINE_DP) * scale))

    /** Space above and below the text in a row whose title or location takes a second line. */
    const val TALL_ROW_PADDING_DP = 4f

    /**
     * A row's height: the usual [baseRowDp] when the title and location take one line each; when
     * either takes a second line, the row's text ([textDp], measured from the font) with
     * [TALL_ROW_PADDING_DP] above and below, and never shorter than the usual row.
     */
    fun rowDp(scale: Float, textDp: Float, hasSecondLine: Boolean): Float =
        if (!hasSecondLine) baseRowDp(scale) else maxOf(baseRowDp(scale), up(textDp + 2 * TALL_ROW_PADDING_DP))

    fun chipDp(scale: Float): Float = CHIP_DP * scale

    /** An all-day event's slim strip (approved option A): 24dp at 100%, growing with the text. */
    const val ALL_DAY_ROW_DP = 24f

    fun allDayRowDp(scale: Float): Float = up(ALL_DAY_ROW_DP * scale)

    /**
     * How many of the rows (heights in order) fit on a card [cardHeightDp] tall, keeping room for
     * the "+N more" chip while events are left over. At least one when there are any events.
     */
    fun fit(cardHeightDp: Float, rowHeightsDp: List<Float>, eventCount: Int, scale: Float): Int {
        if (eventCount <= 0) return 0
        val available = cardHeightDp - GROUP_PADDING_DP + 0.5f
        val chip = chipDp(scale) + GAP_DP
        var used = 0f
        var shown = 0
        for (i in rowHeightsDp.indices) {
            if (i >= MAX_ROWS) break
            val next = used + (if (shown > 0) GAP_DP else 0f) + rowHeightsDp[i]
            val left = eventCount - (i + 1)
            if (next + (if (left > 0) chip else 0f) > available) break
            used = next
            shown++
        }
        return shown.coerceAtLeast(1).coerceAtMost(minOf(eventCount, MAX_ROWS))
    }

    /** Rounds up to whole dp, ignoring float noise (44.000004 stays 44). */
    private fun up(dp: Float): Float = ceil(dp - 0.001f)
}
