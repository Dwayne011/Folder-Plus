package com.widgetsplus.core.devices

/** A pair of earbuds' left bud, right bud and case levels, 0..100, or null where not known. */
data class BudLevels(val left: Int?, val right: Int?, val case: Int?) {
    /** Whether either bud has a level (a case level alone isn't enough to show). */
    val hasBuds: Boolean get() = left != null || right != null

    /** The bud that will run out first, for a single bar. */
    val lowerBud: Int? get() = listOfNotNull(left, right).minOrNull()
}

/**
 * Reads earbuds' left, right and case levels from the text of `dumpsys bluetooth_manager`.
 *
 * Google Play services (Fast Pair) keeps those three levels in Android's Bluetooth metadata
 * (METADATA_UNTETHERED_LEFT/RIGHT/CASE_BATTERY, keys 10, 11 and 12). Apps can only read that
 * metadata with a permission Android keeps for itself, but the Bluetooth service's dump (behind
 * the DUMP permission) prints it for every paired device. Two formats are known:
 *
 * - Android 17 (seen on a Pixel 11 Pro): a block per device with a table of metadata keys:
 *
 *       Device: XX:XX:XX:XX:AB:CD [Persist on Disk] {
 *         Custom Metadata:
 *           Key  Value
 *           ---- -----
 *           10   85
 *           11   90
 *           12   40
 *       }
 *
 * - AOSP main (DatabaseManager.dump, CustomizedMetadataEntity.toString), one line per device:
 *
 *       XX:XX:XX:XX:AB:CD last_active_time=7 {..., custom metadata(manufacturer_name=Google|...
 *         |untethered_left_battery=85|untethered_right_battery=90|untethered_case_battery=40|...), ...}
 *
 * The address is shortened to its last two bytes, so devices are matched by those; two paired
 * devices ending in the same two bytes can't be told apart and are both left out.
 */
object BudDump {
    private val ADDRESS = Regex("""XX:XX:XX:XX:([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})""")
    private val FIELDS = listOf("left", "right", "case").associateWith { Regex("""untethered_${it}_battery=([^|)]*)""") }

    /** Android 17: a device's block starts with "Device: <address>". */
    private val DEVICE = Regex("""^\s*Device:\s*XX:XX:XX:XX:([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})\b""")
    /** Android 17: a row of the "Custom Metadata:" table, "<key>   <value>". */
    private val ROW = Regex("""^\s+(\d+)\s{2,}(.*)$""")
    const val KEY_LEFT = 10
    const val KEY_RIGHT = 11
    const val KEY_CASE = 12

    /** The key a device is matched by: the last two bytes of its address, as "AB:CD". */
    fun key(address: String): String = address.takeLast(5).uppercase()

    /** Whether a dump line can matter to [parse]; lets a reader keep only these. */
    fun isRelevant(line: String): Boolean {
        val t = line.trim()
        return "untethered_" in line || t.startsWith("Device:") || t.endsWith(":") || t.startsWith("}") || ROW.matches(line)
    }

    /** Levels by [key], for every paired device the dump gives a bud level for. */
    fun parse(dump: String): Map<String, BudLevels> {
        val found = HashMap<String, BudLevels>()
        val repeated = HashSet<String>()
        fun add(key: String, levels: BudLevels) {
            if (!levels.hasBuds) return
            if (key in found || key in repeated) {
                repeated += key
                found.remove(key)
            } else {
                found[key] = levels
            }
        }
        // Android 17's table: rows belong to the last "Device:" block, inside its "Custom Metadata:".
        var device: String? = null
        var inTable = false
        var rows = HashMap<Int, Int?>()
        fun closeDevice() {
            val d = device
            if (d != null && rows.isNotEmpty()) add(d, BudLevels(rows[KEY_LEFT], rows[KEY_RIGHT], rows[KEY_CASE]))
            device = null
            inTable = false
            rows = HashMap()
        }
        dump.lineSequence().forEach { line ->
            parseLine(line)?.let { (key, levels) -> add(key, levels); return@forEach }
            DEVICE.find(line)?.let {
                closeDevice()
                device = it.groupValues[1].uppercase()
                return@forEach
            }
            if (device == null) return@forEach
            val t = line.trim()
            when {
                t == "Custom Metadata:" -> inTable = true
                t.startsWith("}") -> closeDevice()
                inTable -> {
                    val row = ROW.matchEntire(line)
                    if (row != null) {
                        val key = row.groupValues[1].toInt()
                        if (key == KEY_LEFT || key == KEY_RIGHT || key == KEY_CASE) rows[key] = level(row.groupValues[2])
                    } else if (!t.startsWith("Key") && !t.startsWith("-")) {
                        // Any other line ends the table (the next section of the device's block).
                        inTable = false
                    }
                }
            }
        }
        closeDevice()
        return found
    }

    /** AOSP main's one-line form: one paired device's key and levels, or null when it has no bud level. */
    fun parseLine(line: String): Pair<String, BudLevels>? {
        if ("untethered_" !in line) return null
        val key = ADDRESS.find(line)?.groupValues?.get(1)?.uppercase() ?: return null
        fun field(name: String): Int? = FIELDS.getValue(name).find(line)?.groupValues?.get(1)?.let(::level)
        val levels = BudLevels(field("left"), field("right"), field("case"))
        return if (levels.hasBuds) key to levels else null
    }

    /** A level written as text ("85"); anything else (null, -1, 101) is unknown. */
    private fun level(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..100 }
}
