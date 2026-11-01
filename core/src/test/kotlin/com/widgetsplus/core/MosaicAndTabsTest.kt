package com.widgetsplus.core

import com.widgetsplus.core.model.Category
import com.widgetsplus.core.model.FolderTab
import com.widgetsplus.core.model.FolderTabs
import com.widgetsplus.core.widget.MosaicLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicAndTabsTest {
    // Sizes from the approved mockup: 75.2dp columns, 80dp rows, widgets inset 4dp in their cells.
    private fun w(cols: Int) = cols * 75.2f - 8f
    private fun h(rows: Int) = rows * 80f - 8f

    @Test fun singleCellIsA2x2MiniMosaic() {
        val p = MosaicLayout.plan(w(1), h(1), 16)
        assertTrue(p.compact)
        assertEquals(2, p.columns); assertEquals(2, p.rows)
        assertEquals(3, p.shown); assertEquals(13, p.more)
        val few = MosaicLayout.plan(w(1), h(1), 4)
        assertEquals(4, few.shown); assertEquals(0, few.more)
    }

    @Test fun biggerWidgetsFitAsManyIconsAsTheSpaceHolds() {
        fun grid(c: Int, r: Int) = MosaicLayout.plan(w(c), h(r), 16).let { Triple(it.columns, it.rows, it.more) }
        assertEquals(Triple(1, 3, 14), grid(1, 2))   // 1x2: a column of 3 (2 apps + "+14")
        assertEquals(Triple(3, 1, 14), grid(2, 1))   // 2x1: a row of 3
        assertEquals(Triple(6, 1, 11), grid(4, 1))   // 4x1: a row of 6
        assertEquals(Triple(3, 3, 8), grid(2, 2))    // 2x2: three rows of 3
        assertEquals(Triple(6, 3, 0), grid(4, 2))    // 4x2: three rows of 6, all 16 fit
        assertFalse(MosaicLayout.plan(w(1), h(2), 16).compact)
    }

    @Test fun fewAppsUseOnlyTheRowsTheyNeed() {
        val p = MosaicLayout.plan(w(4), h(2), 5)
        assertTrue(p.sparse)
        assertEquals(5, p.columns); assertEquals(1, p.rows); assertEquals(5, p.shown); assertEquals(0, p.more)
        val q = MosaicLayout.plan(w(2), h(2), 5)
        assertEquals(3, q.columns); assertEquals(2, q.rows)
    }

    @Test fun allTabCanSitAnywhere() {
        val g = Category(id = 1, folderId = 1, name = "Google", position = 0)
        val u = Category(id = 2, folderId = 1, name = "Utility", position = 1)
        assertEquals(listOf(FolderTab.All, FolderTab.OfCategory(g), FolderTab.OfCategory(u)), FolderTabs.ordered(listOf(u, g), 0))
        assertEquals(listOf(FolderTab.OfCategory(g), FolderTab.OfCategory(u), FolderTab.All), FolderTabs.ordered(listOf(g, u), 9))
        val order = listOf(FolderTab.OfCategory(u), FolderTab.All, FolderTab.OfCategory(g))
        assertEquals(1 to listOf(2L to 0, 1L to 1), FolderTabs.positionsOf(order))
    }
}
