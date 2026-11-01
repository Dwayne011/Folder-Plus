package com.widgetsplus.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.widgetsplus.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Success callback for [WidgetPinner]: the system appends the new widget id, we
 * bind it to the folder the user chose and draw it.
 */
class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PINNED) return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val folderId = intent.getLongExtra(EXTRA_FOLDER_ID, -1L)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || folderId <= 0) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                appContext.graph.repository.bindWidget(appWidgetId, folderId)
                WidgetRenderer.render(appContext, AppWidgetManager.getInstance(appContext), appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PINNED = "com.widgetsplus.widget.PINNED"
        const val EXTRA_FOLDER_ID = "folder_id"
    }
}
