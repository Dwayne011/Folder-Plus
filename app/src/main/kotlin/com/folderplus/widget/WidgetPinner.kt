package com.folderplus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.folderplus.core.model.Folder

/** Asks the launcher to place a widget for [folder] ("Add to home screen"). */
object WidgetPinner {

    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** Returns false when the launcher does not support pinning. */
    fun pin(context: Context, folder: Folder): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false

        val provider = ComponentName(context, FolderWidgetProvider.providerFor(folder.widgetSize))
        val callback = Intent(context, WidgetPinReceiver::class.java)
            .setAction(WidgetPinReceiver.ACTION_PINNED)
            .putExtra(WidgetPinReceiver.EXTRA_FOLDER_ID, folder.id)
        // Must be mutable: the system adds EXTRA_APPWIDGET_ID to this intent.
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        val successCallback = PendingIntent.getBroadcast(context, folder.id.toInt(), callback, flags)
        return manager.requestPinAppWidget(provider, null, successCallback)
    }
}
