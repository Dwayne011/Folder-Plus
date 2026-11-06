package com.widgetsplus.core

import com.widgetsplus.core.devices.BudDump
import com.widgetsplus.core.devices.BudLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudDumpTest {
    /** A line as AOSP's Metadata.toString writes it, with CustomizedMetadataEntity's fields. */
    private fun line(address: String, left: String?, right: String?, case: String?, model: String = "Pixel Buds Pro") =
        "  $address last_active_time=12 {profile connection policy({1=100, 2=100}), optional codec(support=-1|enabled=-1), " +
            "isActiveHfpDevice (false), custom metadata(manufacturer_name=Google|model_name=$model|software_version=null|" +
            "hardware_version=null|companion_app=null|main_icon=null|is_untethered_headset=true|untethered_left_icon=null|" +
            "untethered_right_icon=null|untethered_case_icon=null|untethered_left_battery=$left|untethered_right_battery=$right|" +
            "untethered_case_battery=$case|untethered_left_charging=false|untethered_right_charging=false|" +
            "untethered_case_charging=true|enhanced_settings_ui_uri=null|device_type=Untethered Headset|main_battery=null|" +
            "main_charging=null|main_low_battery_threshold=null|untethered_left_low_battery_threshold=20|" +
            "untethered_right_low_battery_threshold=20|untethered_case_low_battery_threshold=10|spatial_audio=null), " +
            "hfp client audio policy(null), is_preferred_microphone_for_calls(true)}"

    @Test fun readsLeftRightAndCase() {
        val dump = """
            Bluetooth Status
              enabled: true
            Metadata:
            ${line("XX:XX:XX:XX:1A:2b", "85", "90", "40")}
              XX:XX:XX:XX:77:01 last_active_time=3 {profile connection policy({}), custom metadata(manufacturer_name=null|model_name=null), is_preferred_microphone_for_calls(true)}
        """.trimIndent()
        assertEquals(mapOf("1A:2B" to BudLevels(85, 90, 40)), BudDump.parse(dump))
    }

    @Test fun keyIsTheLastTwoBytesOfTheAddress() {
        assertEquals("1A:2B", BudDump.key("F0:5C:77:12:1a:2b"))
    }

    @Test fun unknownValuesAreLeftOut() {
        val (key, levels) = BudDump.parseLine(line("XX:XX:XX:XX:00:11", "72", "-1", "null"))!!
        assertEquals("00:11", key)
        assertEquals(BudLevels(72, null, null), levels)
        assertEquals(72, levels.lowerBud)
    }

    @Test fun aCaseLevelAloneIsNotEnough() {
        assertNull(BudDump.parseLine(line("XX:XX:XX:XX:00:11", "null", "null", "55")))
        assertNull(BudDump.parseLine(line("XX:XX:XX:XX:00:11", "101", "abc", "55")))
    }

    @Test fun twoDevicesWithTheSameEndingAreBothLeftOut() {
        val dump = listOf(
            line("XX:XX:XX:XX:AA:BB", "50", "60", "70"),
            line("XX:XX:XX:XX:aa:bb", "10", "20", "30", model = "Other buds"),
            line("XX:XX:XX:XX:CC:DD", "15", "9", "88"),
        ).joinToString("\n")
        assertEquals(mapOf("CC:DD" to BudLevels(15, 9, 88)), BudDump.parse(dump))
    }

    @Test fun lowerBud() {
        assertEquals(9, BudLevels(15, 9, 88).lowerBud)
        assertTrue(BudLevels(null, 9, null).hasBuds)
        assertNull(BudLevels(null, null, 40).lowerBud)
    }

    @Test fun emptyDumpWithoutPermission() {
        assertTrue(BudDump.parse("").isEmpty())
    }
}
