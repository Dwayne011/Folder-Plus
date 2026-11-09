package com.widgetsplus.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.widgetsplus.graph
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

    /**
     * After an app in [folderId] was opened. Its widgets only change when the folder, or one of
     * its categories, is sorted by use (most used, recently used), so only then are they redrawn;
     * otherwise nothing on them is different and redrawing every icon would only cost battery.
     */
    suspend fun afterLaunch(context: Context, folderId: Long) {
        val repo = context.graph.repository
        val folder = repo.getFolder(folderId) ?: return
        val byUse = folder.sortMode.isByUse || repo.getCategories(folderId).any { it.sortMode?.isByUse == true }
        if (byUse) updateForFolder(context, folderId)
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
