package com.widgetsplus.core

import com.widgetsplus.core.model.WidgetSize
import com.widgetsplus.core.widget.WidgetLayoutPolicy
import com.widgetsplus.core.widget.WidgetVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetLayoutPolicyTest {
    @Test fun presetsMapToVariants() {
        assertEquals(WidgetVariant.COMPACT, WidgetLayoutPolicy.variantFor(WidgetSize.ONE_BY_ONE, showLabel = true))
        assertEquals(WidgetVariant.WIDE, WidgetLayoutPolicy.variantFor(WidgetSize.TWO_BY_ONE, showLabel = true))
        assertEquals(WidgetVariant.LARGE, WidgetLayoutPolicy.variantFor(WidgetSize.TWO_BY_TWO, showLabel = true))
        assertEquals(WidgetVariant.WIDE, WidgetLayoutPolicy.variantFor(WidgetSize.FOUR_BY_ONE, showLabel = true))
        assertEquals(WidgetVariant.LARGE, WidgetLayoutPolicy.variantFor(WidgetSize.FOUR_BY_TWO, showLabel = false))
    }

    @Test fun tallSingleColumnShowsLabelOnlyWhenWanted() {
        assertEquals(WidgetVariant.LABELED, WidgetLayoutPolicy.variantFor(70, 140, showLabel = true))
        assertEquals(WidgetVariant.COMPACT, WidgetLayoutPolicy.variantFor(70, 140, showLabel = false))
    }

    @Test fun quickLaunchSlotsScaleWithWidth() {
        assertEquals(0, WidgetLayoutPolicy.quickLaunchSlots(70, WidgetVariant.COMPACT))
        assertEquals(0, WidgetLayoutPolicy.quickLaunchSlots(140, WidgetVariant.WIDE))
        assertEquals(3, WidgetLayoutPolicy.quickLaunchSlots(280, WidgetVariant.WIDE))
        assertEquals(6, WidgetLayoutPolicy.quickLaunchSlots(1000, WidgetVariant.LARGE))
    }

    @Test fun widgetSizeParsing() {
        assertEquals(WidgetSize(2, 2), WidgetSize.parse("2x2"))
        assertEquals(WidgetSize(4, 1), WidgetSize.parse("4×1"))
        assertNull(WidgetSize.parse("0x2"))
        assertNull(WidgetSize.parse("abc"))
        assertNull(WidgetSize.parse("7x1"))
    }
}
