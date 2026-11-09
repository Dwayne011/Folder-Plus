package com.widgetsplus.devices

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Who can give this app DUMP, which earbuds' left, right and case levels need ([BudBattery]).
 *
 * - At a Glance Plus grants it by itself over its wireless debugging connection, with nothing
 *   asked of you here.
 * - Shizuku, when installed: the Connected devices settings show one row for it, and tapping it
 *   shows Shizuku's own one-time "Allow" pop-up; then this app runs `pm grant` through Shizuku.
 *
 * Either way it's granted once and stays until this app is uninstalled.
 */
object BudAccess {
    private const val TAG = "BudAccess"
    const val AT_A_GLANCE = "com.ataglanceplus"
    const val SHIZUKU = "moe.shizuku.privileged.api"

    private const val FILE = "widgetsplus_buds"
    private const val KEY_VIA_SHIZUKU = "via_shizuku"

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Whether Shizuku is running and this app can reach it. */
    fun shizukuRunning(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11()
    } catch (e: Throwable) {
        false
    }

    /** Whether you've allowed this app in Shizuku. */
    fun shizukuAllowed(): Boolean = try {
        shizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** Whether the access came through Shizuku, for the settings row's wording. */
    fun grantedViaShizuku(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_VIA_SHIZUKU, false)

    /**
     * Grants DUMP to this app through Shizuku (call off the main thread, once Shizuku has
     * allowed this app). Returns whether this app now holds it.
     */
    fun grantWithShizuku(context: Context): Boolean {
        if (BudBattery.hasAccess(context)) return true
        if (!shizukuAllowed()) return false
        return try {
            // Shizuku's process API is private from v13; it runs the command as the shell.
            val newProcess = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
            ).apply { isAccessible = true }
            val process = newProcess.invoke(null, arrayOf("pm", "grant", context.packageName, BudBattery.DUMP), null, null) as Process
            if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroy()
            val granted = BudBattery.hasAccess(context)
            if (granted) {
                context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_VIA_SHIZUKU, true).apply()
            }
            granted
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku couldn't grant DUMP", e)
            false
        }
    }
}
