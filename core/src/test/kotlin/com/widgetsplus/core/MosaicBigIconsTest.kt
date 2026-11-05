package com.widgetsplus.core

import com.widgetsplus.core.widget.MosaicLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Big icons (approved option B): the mockup's numbers for a category of 12 apps. */
class MosaicBigIconsTest {
    private fun plan(w: Float, h: Float, n: Int = 12) = MosaicLayout.plan(w, h, n, bigIcons = true)

    @Test fun twoByTwoShowsFiveAndPlusSevenAt49() {
        val p = plan(138f, 201f)
        assertEquals(2, p.columns)
        assertEquals(3, p.rows)
        assertEquals(5, p.shown)
        assertEquals(7, p.more)
        assertEquals(49f, p.iconDp, 0.01f)
        assertEquals(55f, p.cellDp, 0.01f)
    }

    @Test fun threeByTwoShowsEightAndPlusFourAt52() {
        val p = plan(216f, 201f)
        assertEquals(3, p.columns)
        assertEquals(8, p.shown)
        assertEquals(4, p.more)
        assertEquals(51.67f, p.iconDp, 0.01f)
    }

    @Test fun fourByTwoShowsAllTwelveAt47() {
        val p = plan(294f, 201f)
        assertEquals(5, p.columns)
        assertEquals(3, p.rows)
        assertEquals(12, p.shown)
        assertEquals(0, p.more)
        assertEquals(47.2f, p.iconDp, 0.01f)
    }

    @Test fun fourByOneShowsFourAndPlusEight() {
        val p = plan(294f, 95f)
        assertEquals(1, p.rows)
        assertEquals(4, p.shown)
        assertEquals(8, p.more)
    }

    @Test fun iconsNeverPassTheMaximumAndAlwaysKeepTheGap() {
        val p = plan(400f, 400f, 2)
        assertEquals(MosaicLayout.BIG_ICON_MAX_DP, p.iconDp, 0.01f)
        for (w in 130..420 step 7) for (h in 90..420 step 9) {
            val q = plan(w.toFloat(), h.toFloat(), 30)
            assert(q.cellDp - q.iconDp >= MosaicLayout.GAP_DP - 0.01f) { "gap at $w x $h" }
            assert(q.columns * q.cellDp <= MosaicLayout.innerWidth(w.toFloat()) + 0.01f) { "width at $w x $h" }
            assert(q.rows * q.cellDp <= MosaicLayout.innerHeight(h.toFloat()) + 0.01f) { "height at $w x $h" }
        }
    }

    /** One-column widgets (1x2) and single cells keep the old rule. */
    @Test fun narrowWidgetsAreUnchanged() {
        assertEquals(MosaicLayout.plan(62f, 201f, 12), plan(62f, 201f))
        assertEquals(MosaicLayout.plan(62f, 95f, 12), plan(62f, 95f))
        assertFalse(plan(62f, 201f).compact)
    }
}
