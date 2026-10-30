package com.folderplus.core

import com.folderplus.core.security.PinHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test fun roundTrip() {
        val stored = PinHasher.hash("1234")
        assertTrue(stored.startsWith("v1:"))
        assertTrue(PinHasher.verify("1234", stored))
        assertFalse(PinHasher.verify("1235", stored))
        assertFalse(PinHasher.verify("", stored))
    }

    @Test fun saltMakesHashesUnique() {
        assertNotEquals(PinHasher.hash("0000"), PinHasher.hash("0000"))
    }

    @Test fun malformedStoredValueNeverVerifies() {
        assertFalse(PinHasher.verify("1234", ""))
        assertFalse(PinHasher.verify("1234", "v1:abc:def"))
        assertFalse(PinHasher.verify("1234", "v9:1:AAAA:BBBB"))
        assertFalse(PinHasher.verify("1234", "v1:1000:!!!:BBBB"))
    }

    @Test fun validation() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("123456789012"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin("1234567890123"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun hashRejectsInvalidPin() {
        PinHasher.hash("12")
    }
}
