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
 * (METADATA_UNTETHERED_LEFT/RIGHT/CASE_BATTERY). Apps can only read that metadata with a
 * permission Android keeps for itself, but the Bluetooth service's dump (behind the DUMP
 * permission) prints it for every paired device, one line each, as in AOSP's
 * DatabaseManager.dump and CustomizedMetadataEntity.toString:
 *
 *     XX:XX:XX:XX:AB:CD last_active_time=7 {..., custom metadata(manufacturer_name=Google|...
 *       |untethered_left_battery=85|untethered_right_battery=90|untethered_case_battery=40|...), ...}
 *
 * The address is shortened to its last two bytes, so devices are matched by those; two paired
 * devices ending in the same two bytes can't be told apart and are both left out.
 */
object BudDump {
    private val ADDRESS = Regex("""XX:XX:XX:XX:([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})""")
    private val FIELDS = listOf("left", "right", "case").associateWith { Regex("""untethered_${it}_battery=([^|)]*)""") }

    /** The key a device is matched by: the last two bytes of its address, as "AB:CD". */
    fun key(address: String): String = address.takeLast(5).uppercase()

    /** Levels by [key], for every paired device the dump gives a bud level for. */
    fun parse(dump: String): Map<String, BudLevels> {
        val found = HashMap<String, BudLevels>()
        val repeated = HashSet<String>()
        dump.lineSequence().forEach { line ->
            val (key, levels) = parseLine(line) ?: return@forEach
            if (key in found || key in repeated) {
                repeated += key
                found.remove(key)
            } else {
                found[key] = levels
            }
        }
        return found
    }

    /** One paired device's line: its key and levels, or null when it has no bud level. */
    fun parseLine(line: String): Pair<String, BudLevels>? {
        if ("untethered_" !in line) return null
        val key = ADDRESS.find(line)?.groupValues?.get(1)?.uppercase() ?: return null
        fun level(name: String): Int? =
            FIELDS.getValue(name).find(line)?.groupValues?.get(1)?.trim()?.toIntOrNull()?.takeIf { it in 0..100 }
        val levels = BudLevels(level("left"), level("right"), level("case"))
        return if (levels.hasBuds) key to levels else null
    }
}
