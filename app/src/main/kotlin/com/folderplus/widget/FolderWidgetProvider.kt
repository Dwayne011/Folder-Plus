package com.folderplus.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.folderplus.core.model.WidgetSize
import com.folderplus.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Shared behaviour of the three widget providers. Each subclass only differs in
 * the provider-info XML declared in the manifest (default/target cell size).
 */
abstract class FolderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                appWidgetIds.forEach { WidgetRenderer.render(context, appWidgetManager, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                WidgetRenderer.render(context, appWidgetManager, appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                context.graph.repository.unbindWidgets(appWidgetIds.toList())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val ALL: List<Class<out FolderWidgetProvider>> = listOf(
            FolderWidgetProvider1x1::class.java,
            FolderWidgetProvider2x2::class.java,
            FolderWidgetProvider4x1::class.java,
        )

        /** Picks the provider whose default size is closest to what the user asked for. */
        fun providerFor(size: WidgetSize): Class<out FolderWidgetProvider> = when {
            size.rows >= 2 && size.columns >= 2 -> FolderWidgetProvider2x2::class.java
            size.columns >= 2 -> FolderWidgetProvider4x1::class.java
            else -> FolderWidgetProvider1x1::class.java
        }
    }
}

class FolderWidgetProvider1x1 : FolderWidgetProvider()
class FolderWidgetProvider2x2 : FolderWidgetProvider()
class FolderWidgetProvider4x1 : FolderWidgetProvider()
