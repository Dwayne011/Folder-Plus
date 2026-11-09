package com.widgetsplus.widget

import android.content.Context

/**
 * The category each folder widget's card stack was last used on (the card an app, "+N" or a gap
 * was tapped on), so the widget's next update starts its stack there (see CardLoop).
 */
object FolderStackPosition {
    private const val FILE = "folder_stack_position"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun key(appWidgetId: Int) = "tab_$appWidgetId"

    fun get(context: Context, appWidgetId: Int): String? = prefs(context).getString(key(appWidgetId), null)

    fun set(context: Context, appWidgetId: Int, tabKey: String) {
        prefs(context).edit().putString(key(appWidgetId), tabKey).apply()
    }

    fun clear(context: Context, appWidgetIds: IntArray) {
        val editor = prefs(context).edit()
        appWidgetIds.forEach { editor.remove(key(it)) }
        editor.apply()
    }
}
