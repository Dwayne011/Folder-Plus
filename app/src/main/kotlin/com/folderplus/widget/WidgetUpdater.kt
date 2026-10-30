package com.folderplus.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.folderplus.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Re-renders placed widgets after folder data changes. */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun placedWidgetIds(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context)
        return FolderWidgetProvider.ALL.flatMap { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).toList()
        }
    }

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        placedWidgetIds(context).forEach { WidgetRenderer.render(context, manager, it) }
    }

    suspend fun updateForFolder(context: Context, folderId: Long) {
        val manager = AppWidgetManager.getInstance(context)
        val placed = placedWidgetIds(context).toSet()
        context.graph.repository.widgetIdsForFolder(folderId)
            .filter { it in placed }
            .forEach { WidgetRenderer.render(context, manager, it) }
    }

    fun updateAllAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { updateAll(appContext) }
    }

    fun updateForFolderAsync(context: Context, folderId: Long) {
        val appContext = context.applicationContext
        scope.launch { updateForFolder(appContext, folderId) }
    }
}
