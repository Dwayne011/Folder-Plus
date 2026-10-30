package com.folderplus.core

import com.folderplus.core.gesture.TapResolver
import com.folderplus.core.gesture.TapResolver.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapResolverTest {
    private val resolver = TapResolver(windowMillis = 300)

    @Test fun singleTapWhenDoubleTapDisabled() {
        assertEquals(Decision.SingleTap, resolver.onTap(1, 1000, doubleTapEnabled = false))
        assertFalse(resolver.hasPending(1))
    }

    @Test fun twoQuickTapsAreADoubleTap() {
        assertEquals(Decision.WaitForSecondTap, resolver.onTap(1, 1000, true))
        assertEquals(Decision.DoubleTap, resolver.onTap(1, 1200, true))
        assertFalse(resolver.hasPending(1))
        // timer firing afterwards must not also run the single-tap action
        assertFalse(resolver.resolvePending(1, 1400))
    }

    @Test fun slowSecondTapStartsANewGesture() {
        assertEquals(Decision.WaitForSecondTap, resolver.onTap(1, 1000, true))
        assertTrue(resolver.resolvePending(1, 1300))
        assertEquals(Decision.WaitForSecondTap, resolver.onTap(1, 1400, true))
    }

    @Test fun pendingIsNotResolvedEarly() {
        resolver.onTap(1, 1000, true)
        assertFalse(resolver.resolvePending(1, 1100))
        assertTrue(resolver.hasPending(1))
        assertTrue(resolver.resolvePending(1, 1300))
    }

    @Test fun targetsAreIndependent() {
        resolver.onTap(1, 1000, true)
        assertEquals(Decision.WaitForSecondTap, resolver.onTap(2, 1050, true))
        assertEquals(Decision.DoubleTap, resolver.onTap(1, 1100, true))
        assertTrue(resolver.hasPending(2))
    }
}
