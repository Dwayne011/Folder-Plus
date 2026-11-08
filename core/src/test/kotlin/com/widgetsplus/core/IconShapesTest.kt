package com.widgetsplus.core

import com.widgetsplus.core.model.IconShapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconShapesTest {
    @Test fun squircleTouchesTheMiddleOfEachSide() {
        val p = IconShapes.squirclePoints(96)
        assertEquals(1f, p[0].first, 1e-4f)   // right
        assertEquals(0.5f, p[0].second, 1e-4f)
        assertEquals(0.5f, p[24].first, 1e-4f) // bottom
        assertEquals(1f, p[24].second, 1e-4f)
    }

    @Test fun squircleStaysInsideTheSquareAndOutsideTheCircle() {
        IconShapes.squirclePoints(96).forEach { (x, y) ->
            assertTrue(x in 0f..1f && y in 0f..1f)
            val dx = x - 0.5f
            val dy = y - 0.5f
            // At least as far out as the inscribed circle (radius 0.5), corners further.
            assertTrue(dx * dx + dy * dy >= 0.25f - 1e-4f)
        }
        // The corner (45 degrees) is well outside the circle but inside the square.
        val (cx, cy) = IconShapes.squirclePoints(96)[12]
        assertTrue(cx > 0.5f + 0.3535f && cx < 1f && cy > 0.5f + 0.3535f && cy < 1f)
    }
}
