package com.widgetsplus.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF

/**
 * Lets Widgets Plus display other apps' widgets inside its popup, the way a
 * launcher does. Wraps the single [AppWidgetHost] for the process and keeps a
 * listener refcount so several activities can show widgets at once.
 */
class HostedWidgets(context: Context) {
    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val host = AppWidgetHost(appContext, HOST_ID)
    private var listeners = 0

    fun startListening() {
        if (listeners++ == 0) host.startListening()
    }

    fun stopListening() {
        if (listeners > 0 && --listeners == 0) host.stopListening()
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(appWidgetId: Int) {
        if (appWidgetId > 0) runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /** True when the widget is bound; false means the user must grant permission via ACTION_APPWIDGET_BIND. */
    fun bindIfAllowed(appWidgetId: Int, provider: ComponentName): Boolean =
        manager.bindAppWidgetIdIfAllowed(appWidgetId, provider)

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)

    /** All home-screen widgets installed on the device, except our own. */
    fun installedProviders(): List<AppWidgetProviderInfo> =
        manager.installedProviders.filter { it.provider.packageName != appContext.packageName }

    fun label(info: AppWidgetProviderInfo): String = info.loadLabel(appContext.packageManager)

    fun appLabel(info: AppWidgetProviderInfo): String {
        val pm = appContext.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
            .getOrDefault(info.provider.packageName)
    }

    /** Creates the live widget view and keeps the provider informed of its size. */
    fun createView(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo): AppWidgetHostView {
        val view = host.createView(context, appWidgetId, info)
        val density = context.resources.displayMetrics.density
        view.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) {
                updateSize(v as AppWidgetHostView, (w / density).toInt(), (h / density).toInt())
            }
        }
        return view
    }

    fun updateSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
        }
    }

    /** Height for the popup: the provider's minimum, nudged up to its target cells, within sane bounds. */
    fun popupHeightDp(info: AppWidgetProviderInfo): Int {
        val density = appContext.resources.displayMetrics.density
        var h = (info.minHeight / density).toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
            h = maxOf(h, info.targetCellHeight * CELL_DP)
        }
        return h.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
    }

    companion object {
        /** Arbitrary but stable id identifying our host to the system. */
        const val HOST_ID = 0xF01D
        private const val CELL_DP = 70
        private const val MIN_HEIGHT_DP = 80
        private const val MAX_HEIGHT_DP = 480
    }
}
