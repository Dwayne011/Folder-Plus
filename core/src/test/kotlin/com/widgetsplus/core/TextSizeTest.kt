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
        // A second title line only adds height when there's also a location, or the text is big.
        assertEquals(44f, AgendaRows.rowDp(1f, twoLineTitle = false, hasLocation = true))
        assertEquals(58f, AgendaRows.rowDp(1f, twoLineTitle = true, hasLocation = true))
        assertEquals(63f, AgendaRows.rowDp(1.4f, twoLineTitle = true, hasLocation = false))
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
            AgendaRows.rowDp(s, i in twoLine, loc)
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
}
