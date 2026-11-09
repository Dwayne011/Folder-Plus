package com.widgetsplus.core

import com.widgetsplus.core.agenda.AgendaCardShape
import com.widgetsplus.core.agenda.AgendaRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calendar widget's smaller sizes (approved mockup), with the sizes a Pixel home screen gives
 * each grid size: widths 138 / 216 / 294 / 373dp, heights 95 / 201dp. A card is the widget less
 * its 8dp border on each side.
 */
class AgendaSmallSizesTest {

    @Test
    fun cardShapeForEachGridSize() {
        assertEquals(AgendaCardShape.FULL, AgendaRows.shapeFor(373f, 201f))
        assertEquals(AgendaCardShape.FULL, AgendaRows.shapeFor(294f, 201f))
        assertEquals(AgendaCardShape.NARROW, AgendaRows.shapeFor(216f, 201f))
        assertEquals(AgendaCardShape.TOP, AgendaRows.shapeFor(138f, 201f))
        // One row high: the narrow date card from 3 columns, the date line at 2.
        assertEquals(AgendaCardShape.NARROW, AgendaRows.shapeFor(373f, 95f))
        assertEquals(AgendaCardShape.NARROW, AgendaRows.shapeFor(294f, 95f))
        assertEquals(AgendaCardShape.NARROW, AgendaRows.shapeFor(216f, 95f))
        assertEquals(AgendaCardShape.TOP, AgendaRows.shapeFor(138f, 95f))
        assertTrue(AgendaRows.isOneRow(95f))
        assertFalse(AgendaRows.isOneRow(201f))
        assertFalse(AgendaRows.isOneRow(307f))
    }

    private fun compactFit(groupDp: Float, rows: List<Float>) =
        AgendaRows.fit(groupDp, rows, rows.size, 1f, AgendaRows.COMPACT_PADDING_DP, AgendaRows.COMPACT_GAP_DP, AgendaRows.compactChipDp(1f))

    @Test
    fun narrowTwoRowsMatchesTheMockup() {
        val strip = AgendaRows.lineDp(1f)
        val row = AgendaRows.compactRowDp(1f)
        assertEquals(22f, strip)
        assertEquals(38f, row)
        // 3×2: card 185dp. Thu: two all-day strips and two timed events all fit.
        assertEquals(4, compactFit(185f, listOf(strip, strip, row, row)))
        // Mon: two strips, two timed events, then "+1 more".
        assertEquals(4, compactFit(185f, listOf(strip, strip, row, row, row)))
        // 2×2: the date line (26dp + 6dp) leaves 153dp: two strips, one event, "+2 more".
        val group = 185f - AgendaRows.headDp(1f) - AgendaRows.HEAD_GAP_DP
        assertEquals(3, compactFit(group, listOf(strip, strip, row, row, row)))
    }

    @Test
    fun oneRowHighShowsOneLineEvents() {
        // 5×1 to 3×1: card 79dp, two lines; 2×1: the date line leaves room for one.
        assertEquals(2, AgendaRows.linesThatFit(79f, 1f))
        assertEquals(1, AgendaRows.linesThatFit(79f - AgendaRows.headDp(1f) - AgendaRows.HEAD_GAP_DP, 1f))
        // Bigger text: still two lines at 140%, never fewer than one.
        assertEquals(2, AgendaRows.linesThatFit(79f, 1.4f))
        assertEquals(1, AgendaRows.linesThatFit(20f, 1.4f))
    }
}
