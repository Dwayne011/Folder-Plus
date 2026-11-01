package com.widgetsplus.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.widgetsplus.core.model.AppRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A launchable app as shown in pickers and folders. */
data class LauncherApp(val ref: AppRef, val label: String)

/**
 * Enumerates launcher activities and caches labels/icons. Icons are cached as
 * bitmaps so Compose and RemoteViews can share them.
 */
class InstalledApps(context: Context) {
    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager
    private val mutex = Mutex()

    private val _apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val apps: StateFlow<List<LauncherApp>> = _apps

    private var labels: Map<AppRef, String> = emptyMap()

    private val iconCache = object : LruCache<String, Bitmap>(64) {}

    /** Default icon size in px used for both Compose grids and widgets. */
    val iconSizePx: Int = (56 * appContext.resources.displayMetrics.density).toInt().coerceAtLeast(96)

    suspend fun refresh(): List<LauncherApp> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            val list = resolved
                .filter { it.activityInfo.packageName != appContext.packageName }
                .map { info ->
                    LauncherApp(
                        ref = AppRef(info.activityInfo.packageName, info.activityInfo.name),
                        label = info.loadLabel(pm).toString(),
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            labels = list.associate { it.ref to it.label }
            _apps.value = list
            list
        }
    }

    suspend fun ensureLoaded(): List<LauncherApp> = _apps.value.ifEmpty { refresh() }

    /** Label for an app or added shortcut; falls back to the package name when it's gone. */
    fun label(ref: AppRef): String {
        ref.shortcutId?.let { id -> return ShortcutStore.get(appContext, id)?.label ?: ref.packageName }
        return labels[ref] ?: ref.packageName
    }

    /** An app is installed while its launcher activity exists; a shortcut while it's stored and its app is installed. */
    fun isInstalled(ref: AppRef): Boolean {
        val id = ref.shortcutId ?: return labels.containsKey(ref)
        return ShortcutStore.get(appContext, id) != null && isPackageInstalled(ref.packageName)
    }

    fun isPackageInstalled(packageName: String): Boolean = try {
        pm.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Loads (and caches) the app's or shortcut's icon as a square bitmap. Null if it's gone. */
    fun icon(ref: AppRef, sizePx: Int = iconSizePx): Bitmap? {
        val key = "${ref.flatten()}@$sizePx"
        iconCache.get(key)?.let { return it }
        val drawable: Drawable = try {
            val id = ref.shortcutId
            if (id != null) {
                // The shortcut's own icon, or its app's icon when it came without one.
                ShortcutStore.icon(appContext, id)?.let { stored ->
                    val bitmap = Bitmap.createScaledBitmap(stored, sizePx, sizePx, true)
                    iconCache.put(key, bitmap)
                    return bitmap
                }
                pm.getApplicationIcon(ref.packageName)
            } else {
                pm.getActivityIcon(android.content.ComponentName(ref.packageName, ref.activityName))
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val bitmap = drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        iconCache.put(key, bitmap)
        return bitmap
    }
}
