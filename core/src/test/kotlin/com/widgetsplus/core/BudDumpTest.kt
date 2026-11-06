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

    /**
     * Android 17's form, laid out as printed on a Pixel 11 Pro (DP11.261016.004). The earbuds'
     * rows are examples, numbered as AOSP's METADATA_* keys (0 maker, 1 model, 6 untethered,
     * 10 to 12 battery, 13 to 15 charging).
     */
    private val android17 = """
        Bluetooth Status
          enabled: true
        ScanMode: SCAN_MODE_CONNECTABLE
        Enabled Profile Services:
          A2DP
          11-06 19:54:43.379 XX:XX:XX:XX:C3:2B: Custom metadata changed for 12
          Device: XX:XX:XX:XX:71:57 [Persist on Disk] {
            connection_counter: 6
            Permissions:
              phonebook: true
            Custom Metadata:
              Key  Value
              ---- ----------
              25   <DEVICE_SETTINGS_CONFIG_PACKAGE_NAME>com.google.android.gms</DEVICE_SETTINGS_CONFIG_PACKAGE_NAME>
          }
          Device: XX:XX:XX:XX:C3:2B [Persist on Disk] {
            connection_counter: 436
            Profile Connection Policies:
              a2dp: ALLOWED
              battery: ALLOWED
            Permissions:
            A2DP Settings:
            Custom Metadata:
              Key  Value
              ---- ----------
              0    Google
              1    Pixel Buds Pro 2
              6    true
              10   72
              11   70
              12   40
              13   false
              14   false
              15   true
          }
    """.trimIndent()

    @Test fun readsAndroid17Table() {
        assertEquals(mapOf("C3:2B" to BudLevels(72, 70, 40)), BudDump.parse(android17))
    }

    @Test fun android17RowsOutsideTheTableAreIgnored() {
        val dump = """
              Device: XX:XX:XX:XX:AA:01 [Persist on Disk] {
                Some Table:
                  10   55
                  11   66
                Custom Metadata:
                  Key  Value
                  ---- -----
                  12   30
              }
              Device: XX:XX:XX:XX:AA:02 [Persist on Disk] {
                Custom Metadata:
                  10   -1
                  11   15
              }
        """.trimIndent()
        // AA:01 has only a case level (the 10 and 11 above aren't in its metadata table).
        assertEquals(mapOf("AA:02" to BudLevels(null, 15, null)), BudDump.parse(dump))
    }

    /**
     * The Pixel Buds' block exactly as a Pixel 11 Pro printed it (Android 17 DP11.261016.004),
     * with trailing spaces as printed; key 16 (a link holding the full address) left out. Rows
     * aren't in key order. Another table follows the device blocks with the same indent.
     */
    @Test fun readsTheRealPixelBudsBlock() {
        val rows = listOf(
            "26   true", "17   Untethered Headset", "1    G1", "0    Google", "3    FAST_PAIR_02FC97",
            "4    com.google.android.apps.wearables.maestro.companion", "21   15", "22   15", "23   19",
            "6    true", "2    Revision 10", "10   100", "13   false", "11   100", "14   false", "12   49", "15   false",
        ).joinToString("\n") { "      $it        " }
        val dump = "  Device: XX:XX:XX:XX:C3:2B [Persist on Disk] {\n" +
            "    connection_counter: 436\n" +
            "    Custom Metadata:\n" +
            "      Key  Value     \n" +
            "      ---- ----------\n" +
            rows + "\n" +
            "  }\n" +
            "      1         NORMAL   0      0\n" +
            "      10        NORMAL   0      0\n"
        assertEquals(mapOf("C3:2B" to BudLevels(100, 100, 49)), BudDump.parse(dump))
    }

    @Test fun relevantLinesAreEnoughToParse() {
        val kept = android17.lineSequence().filter(BudDump::isRelevant).joinToString("\n")
        assertEquals(BudDump.parse(android17), BudDump.parse(kept))
    }

    @Test fun emptyDumpWithoutPermission() {
        assertTrue(BudDump.parse("").isEmpty())
    }
}
