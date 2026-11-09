package com.widgetsplus.core

import com.widgetsplus.core.devices.DeviceKind
import com.widgetsplus.core.devices.DeviceNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceNamesTest {
    private fun kind(name: String) = DeviceNames.kindOf(name)

    @Test fun speakers() {
        listOf(
            "UE BOOM 2", "MEGABOOM 3", "WONDERBOOM 4", "UE ROLL 2", "JBL Flip 6", "JBL Charge 5", "JBL Go 3",
            "JBL PartyBox 110", "Bose SoundLink Flex", "Sonos Roam", "Emberton II", "Beats Pill", "Soundcore Motion+",
            "StormBox Micro 2", "Beosound A1 2nd Gen", "Harman Kardon Onyx Studio 8",
            "SRS-XB13", "SRS-XE200", "SRS-XG300", "ULT FIELD 1", "ULT TOWER 10", "HT-S2000", "Kitchen speaker",
        ).forEach { assertEquals(it, DeviceKind.SPEAKER, kind(it)) }
    }

    @Test fun sonyHeadphonesAndEarbudsStayThemselves() {
        assertEquals(DeviceKind.HEADPHONES, kind("WH-1000XM5"))
        assertEquals(DeviceKind.HEADPHONES, kind("ULT WEAR"))
        assertEquals(DeviceKind.EARBUDS, kind("WF-1000XM5"))
        assertEquals(DeviceKind.EARBUDS, kind("LinkBuds S"))
    }

    @Test fun otherKinds() {
        assertEquals(DeviceKind.EARBUDS, kind("Pixel Buds Pro"))
        assertEquals(DeviceKind.EARBUDS, kind("Soundcore Liberty 4 NC earbuds"))
        assertEquals(DeviceKind.WATCH, kind("Pixel Watch 3"))
        assertEquals(DeviceKind.WATCH, kind("Galaxy Watch6 (A1B2)"))
        assertEquals(DeviceKind.GAMEPAD, kind("Xbox Wireless Controller"))
        assertEquals(DeviceKind.KEYBOARD, kind("MX Keys"))
        assertEquals(DeviceKind.MOUSE, kind("MX Master 3S"))
        assertNull(kind("Car Multimedia"))
        assertNull(kind("Bose QC Ultra"))
    }
}
