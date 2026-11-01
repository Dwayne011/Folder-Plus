package com.widgetsplus.apps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/** A shortcut added to a folder: the app that made it, its name, its icon (PNG) and what it opens. */
data class SavedShortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val intentUri: String,
    val iconPng: ByteArray?,
) {
    override fun equals(other: Any?) = other is SavedShortcut && other.id == id
    override fun hashCode() = id.hashCode()
}

/**
 * Shortcuts added to folders, kept in their own shared-preferences file (so Google backup covers
 * them, like the rest of the app's settings) and cached in memory. Folder items refer to them with
 * [com.widgetsplus.core.model.AppRef.shortcut].
 */
object ShortcutStore {
    private const val FILE = "widgetsplus_shortcuts"
    private const val KEY = "s:"
    private const val ICON_PX = 192

    private val cache = ConcurrentHashMap<String, SavedShortcut>()
    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            prefs(context).all.forEach { (key, value) ->
                if (key.startsWith(KEY) && value is String) decode(key.removePrefix(KEY), value)?.let { cache[it.id] = it }
            }
            loaded = true
        }
    }

    fun get(context: Context, id: String): SavedShortcut? {
        load(context)
        return cache[id]
    }

    fun all(context: Context): List<SavedShortcut> {
        load(context)
        return cache.values.toList()
    }

    fun put(context: Context, shortcut: SavedShortcut) {
        load(context)
        cache[shortcut.id] = shortcut
        prefs(context).edit().putString(KEY + shortcut.id, encode(shortcut)).apply()
    }

    fun putAll(context: Context, shortcuts: Collection<SavedShortcut>) {
        load(context)
        val edit = prefs(context).edit()
        shortcuts.forEach {
            cache[it.id] = it
            edit.putString(KEY + it.id, encode(it))
        }
        edit.apply()
    }

    /** Forgets shortcuts no folder uses any more. */
    fun retainOnly(context: Context, ids: Set<String>) {
        load(context)
        val gone = cache.keys.filter { it !in ids }
        if (gone.isEmpty()) return
        val edit = prefs(context).edit()
        gone.forEach {
            cache.remove(it)
            edit.remove(KEY + it)
        }
        edit.apply()
    }

    fun icon(context: Context, id: String): Bitmap? =
        get(context, id)?.iconPng?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    /**
     * The intent to start for a shortcut, made safe to start from this app: never one of our own
     * screens, no URI permission grants or selector carried over from the app that made it.
     */
    fun launchIntent(context: Context, id: String): Intent? {
        val saved = get(context, id) ?: return null
        val intent = try {
            Intent.parseUri(saved.intentUri, Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            return null
        }
        return sanitize(context, intent)
    }

    fun sanitize(context: Context, intent: Intent): Intent? {
        val own = context.packageName
        if (intent.component?.packageName == own || intent.`package` == own) return null
        intent.selector = null
        intent.clipData = null
        intent.removeFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /** A square PNG of [bitmap], small enough to keep in preferences. */
    fun png(bitmap: Bitmap): ByteArray {
        val scaled = if (bitmap.width == ICON_PX && bitmap.height == ICON_PX) bitmap else Bitmap.createScaledBitmap(bitmap, ICON_PX, ICON_PX, true)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    fun toJson(s: SavedShortcut): JSONObject = JSONObject()
        .put("id", s.id)
        .put("package", s.packageName)
        .put("label", s.label)
        .put("intent", s.intentUri)
        .put("icon", s.iconPng?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)

    fun fromJson(o: JSONObject): SavedShortcut? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val pkg = o.optString("package").takeIf { it.isNotBlank() } ?: return null
        val intent = o.optString("intent").takeIf { it.isNotBlank() } ?: return null
        val icon = o.optString("icon").takeIf { it.isNotBlank() && it != "null" }?.let {
            try { Base64.decode(it, Base64.NO_WRAP) } catch (e: IllegalArgumentException) { null }
        }
        return SavedShortcut(id, pkg, o.optString("label", pkg), intent, icon)
    }

    private fun encode(s: SavedShortcut): String = toJson(s).toString()

    private fun decode(id: String, value: String): SavedShortcut? = try {
        fromJson(JSONObject(value).put("id", id))
    } catch (e: Exception) {
        null
    }
}
