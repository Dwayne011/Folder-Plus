package com.widgetsplus.devices

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.widgetsplus.core.devices.BudDump
import com.widgetsplus.core.devices.BudLevels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Earbuds' left, right and case levels, when this app has been given Android's DUMP permission.
 *
 * Google Play services keeps those levels in Android's Bluetooth metadata, which apps can't read
 * directly. The Bluetooth service's dump prints them (see [BudDump]), and reading the dump needs
 * DUMP, a permission only the developer shell can grant. At a Glance Plus (over its wireless
 * debugging connection) or Shizuku grants it; once granted it stays until this app is
 * uninstalled. Without it nothing changes: the widget shows the one level Android shares.
 *
 * Reading the dump goes to the Bluetooth service, so it never runs while the widget draws:
 * drawing uses the last levels read ([cached]), and [refreshIfStale] reads again in the
 * background, at most every [FRESH_MS], and redraws when they changed.
 */
object BudBattery {
    const val DUMP = "android.permission.DUMP"
    private const val TAG = "BudBattery"
    private const val FRESH_MS = 30_000L
    /** Saved levels older than this aren't shown after the app's process restarts. */
    private const val KEEP_MS = 30 * 60_000L
    private const val READ_TIMEOUT_MS = 4_000L

    private const val FILE = "widgetsplus_buds"
    private const val KEY_TEXT = "levels"
    private const val KEY_AT = "at"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reading = AtomicBoolean(false)
    @Volatile private var memory: Pair<Long, Map<String, BudLevels>>? = null

    fun hasAccess(context: Context): Boolean =
        context.checkSelfPermission(DUMP) == PackageManager.PERMISSION_GRANTED

    /** The last levels read, by [BudDump.key]; empty without access. */
    fun cached(context: Context): Map<String, BudLevels> {
        if (!hasAccess(context)) return emptyMap()
        memory?.let { return it.second }
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val at = prefs.getLong(KEY_AT, 0L)
        if (System.currentTimeMillis() - at > KEEP_MS) return emptyMap()
        val levels = decode(prefs.getString(KEY_TEXT, null).orEmpty())
        memory = at to levels
        return levels
    }

    /** The levels for one connected device, if its buds report them. */
    fun forDevice(levels: Map<String, BudLevels>, address: String): BudLevels? = levels[BudDump.key(address)]

    /**
     * Reads the levels again in the background when the last read is older than [FRESH_MS],
     * then calls [onChanged] (on a background thread) if they differ from what was shown.
     */
    fun refreshIfStale(context: Context, onChanged: (Context) -> Unit) {
        if (!hasAccess(context)) return
        val last = memory?.first ?: 0L
        if (System.currentTimeMillis() - last < FRESH_MS) return
        if (!reading.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            try {
                val before = cached(app)
                val now = BudDump.parse(readDump() ?: return@launch)
                val at = System.currentTimeMillis()
                memory = at to now
                app.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                    .putString(KEY_TEXT, encode(now)).putLong(KEY_AT, at).apply()
                if (now != before) onChanged(app)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read earbud levels", e)
            } finally {
                reading.set(false)
            }
        }
    }

    /**
     * The lines of the Bluetooth service's dump that carry earbud levels, or null if Android
     * wouldn't give it. Without DUMP the service writes nothing, which reads as no levels.
     */
    private fun readDump(): String? {
        val binder = try {
            Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "bluetooth_manager") as? IBinder
        } catch (e: Exception) {
            Log.w(TAG, "No Bluetooth service", e)
            null
        } ?: return null
        val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe().let { it[0] to it[1] }
        val lines = StringBuilder()
        // The dump can be long, so it's read while it's written; only the metadata lines are kept.
        val reader = thread(name = "bud-levels", isDaemon = true) {
            try {
                ParcelFileDescriptor.AutoCloseInputStream(readEnd).bufferedReader().useLines { all ->
                    all.filter { "untethered_" in it }.forEach { synchronized(lines) { lines.appendLine(it) } }
                }
            } catch (e: Exception) {
                // Closed below after a timeout, or the service stopped writing.
            }
        }
        try {
            binder.dumpAsync(writeEnd.fileDescriptor, emptyArray())
        } finally {
            // The service holds its own copies; the pipe ends when they're closed too.
            writeEnd.close()
        }
        reader.join(TimeUnit.MILLISECONDS.toMillis(READ_TIMEOUT_MS))
        if (reader.isAlive) {
            runCatching { readEnd.close() }
            return null
        }
        return synchronized(lines) { lines.toString() }
    }

    private fun encode(levels: Map<String, BudLevels>): String =
        levels.entries.joinToString(";") { (key, l) -> "$key,${l.left ?: ""},${l.right ?: ""},${l.case ?: ""}" }

    private fun decode(text: String): Map<String, BudLevels> =
        text.split(";").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size != 4) return@mapNotNull null
            parts[0] to BudLevels(parts[1].toIntOrNull(), parts[2].toIntOrNull(), parts[3].toIntOrNull())
        }.toMap()
}
