package com.widgetsplus.core

import com.widgetsplus.core.agenda.AgendaRows
import com.widgetsplus.core.model.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSizeTest {
    @Test fun clampsAndSnapsToFivePercentSteps() {
        assertEquals(100, TextSize.clamp(100))
        assertEquals(100, TextSize.clamp(102))
        assertEquals(105, TextSize.clamp(103))
        assertEquals(80, TextSize.clamp(10))
        assertEquals(140, TextSize.clamp(500))
        assertEquals(11, TextSize.SLIDER_STEPS)
        assertEquals(1.1f, TextSize.scale(110), 0.0001f)
    }

    @Test fun rowsAre44dpAtNormalSizeAndGrowWithText() {
        assertEquals(44f, AgendaRows.baseRowDp(1f))
        assertEquals(46f, AgendaRows.baseRowDp(1.05f))
        assertEquals(48f, AgendaRows.baseRowDp(1.1f))
        assertEquals(40f, AgendaRows.baseRowDp(0.8f))
        // Rows whose title and location take one line each keep the usual height.
        assertEquals(44f, AgendaRows.rowDp(1f, text(1f, 1, 1), hasSecondLine = false))
        assertEquals(52f, AgendaRows.rowDp(1.2f, text(1.2f, 1, 1), hasSecondLine = false))
    }

    /**
     * The row's text as the widget measures it: Google Sans Text lines are about 1.25 times the
     * text size (10dp time, 12.5dp title, 11dp location at 100%), as in the screenshots.
     */
    private fun text(s: Float, titleLines: Int, locationLines: Int) =
        (10f + titleLines * 12.5f + locationLines * 11f) * 1.25f * s

    /** A second line makes the row its text plus 4dp above and below, so nothing is clipped. */
    @Test fun rowsWithASecondLineFitTheirTextEvenly() {
        assertEquals(64f, AgendaRows.rowDp(1f, text(1f, 1, 2), hasSecondLine = true))
        assertEquals(75f, AgendaRows.rowDp(1.2f, text(1.2f, 1, 2), hasSecondLine = true))
        assertEquals(57f, AgendaRows.rowDp(1.1f, text(1.1f, 2, 0), hasSecondLine = true))
        for (s in listOf(0.8f, 1f, 1.2f, 1.4f)) {
            val t = text(s, 1, 2)
            val row = AgendaRows.rowDp(s, t, hasSecondLine = true)
            assertTrue("room at $s", row - t >= 2 * AgendaRows.TALL_ROW_PADDING_DP)
        }
        // Two events with long locations fit easily on a 290dp card; three at 120% too.
        assertEquals(2, AgendaRows.fit(290f, List(2) { AgendaRows.rowDp(1f, text(1f, 1, 2), true) }, eventCount = 2, scale = 1f))
        assertEquals(3, AgendaRows.fit(290f, List(3) { AgendaRows.rowDp(1.2f, text(1.2f, 1, 2), true) }, eventCount = 3, scale = 1.2f))
    }

    @Test fun titlesWrapOnlyAboveNormal() {
        assertFalse(AgendaRows.titlesWrap(1f))
        assertFalse(AgendaRows.titlesWrap(0.9f))
        assertTrue(AgendaRows.titlesWrap(1.05f))
    }

    /** The widget from the mockup: a 290dp card and six events, as on Thu 5 Nov. */
    @Test fun rowsThatFitMatchTheMockup() {
        val card = 290f
        fun rows(s: Float, twoLine: Set<Int> = emptySet()) = listOf(false, false, true, true, true).mapIndexed { i, loc ->
            val titleLines = if (i in twoLine) 2 else 1
            AgendaRows.rowDp(s, text(s, titleLines, if (loc) 1 else 0), hasSecondLine = i in twoLine)
        }
        assertEquals(5, AgendaRows.fit(card, rows(1f), eventCount = 6, scale = 1f))
        assertEquals(4, AgendaRows.fit(card, rows(1.05f), 6, 1.05f))
        assertEquals(4, AgendaRows.fit(card, rows(1.1f, setOf(1)), 6, 1.1f))
        assertEquals(3, AgendaRows.fit(card, rows(1.4f, setOf(1)), 6, 1.4f))
    }

    /** At 100% the rule is the widget's old one: 50dp per row, room kept for the chip. */
    @Test fun normalSizeMatchesTheOldRowCount() {
        for (h in 100..400 step 7) {
            val old = ((h - 12f - 24f + 6f + 0.5f) / 50f).toInt().coerceIn(1, 5)
            assertEquals("height $h", old, AgendaRows.fit(h.toFloat(), List(5) { 44f }, eventCount = 9, scale = 1f))
        }
    }

    @Test fun edgeCases() {
        assertEquals(0, AgendaRows.fit(290f, emptyList(), 0, 1f))
        assertEquals(1, AgendaRows.fit(30f, listOf(44f), 3, 1f))
        // No chip needed when every event fits.
        assertEquals(2, AgendaRows.fit(12f + 44f + 6f + 44f, listOf(44f, 44f), 2, 1f))
    }

    @Test fun allDayStrips() {
        assertEquals(24f, AgendaRows.allDayRowDp(1f))
        assertEquals(34f, AgendaRows.allDayRowDp(1.4f))
        assertEquals(20f, AgendaRows.allDayRowDp(0.8f))
    }

    @Test fun slimAllDayStripsLeaveRoomForTimedEvents() {
        // A 5x2 card (185dp): two all-day strips and two timed rows all fit, where two full
        // all-day rows left room for one timed row and "+1 more".
        assertEquals(4, AgendaRows.fit(185f, listOf(24f, 24f, 44f, 44f), 4, 1f))
        assertEquals(3, AgendaRows.fit(185f, listOf(44f, 44f, 44f, 44f), 4, 1f))
        // Three all-day and three timed: three strips and one timed row, then "+2 more".
        assertEquals(4, AgendaRows.fit(185f, listOf(24f, 24f, 24f, 44f, 44f), 6, 1f))
    }
}
