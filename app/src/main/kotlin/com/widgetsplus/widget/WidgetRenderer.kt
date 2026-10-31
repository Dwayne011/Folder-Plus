package com.widgetsplus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.widgetsplus.R
import com.widgetsplus.apps.InstalledApps
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.TapAction
import com.widgetsplus.core.sort.AppSorter
import com.widgetsplus.core.widget.WidgetLayoutPolicy
import com.widgetsplus.core.widget.WidgetVariant
import com.widgetsplus.graph
import com.widgetsplus.launch.AppLaunchActivity
import com.widgetsplus.ui.open.FolderOpenActivity

/** Turns a folder into RemoteViews for one placed widget. */
object WidgetRenderer {

    private val quickSlotIds = intArrayOf(
        R.id.widget_quick_1, R.id.widget_quick_2, R.id.widget_quick_3,
        R.id.widget_quick_4, R.id.widget_quick_5, R.id.widget_quick_6,
    )

    private const val ZONE_MAIN = 0
    private const val ZONE_BADGE = 1
    private const val ZONE_QUICK_BASE = 10

    suspend fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val graph = context.graph
        val folder = graph.repository.folderIdForWidget(appWidgetId)?.let { graph.repository.getFolder(it) }
        if (folder == null) {
            manager.updateAppWidget(appWidgetId, unconfigured(context, appWidgetId))
            return
        }
        val apps = graph.installedApps
        apps.ensureLoaded()
        val items = AppSorter.sort(graph.repository.getItems(folder.id), folder.sortMode) { apps.label(it.app) }
        val options = manager.getAppWidgetOptions(appWidgetId)

        val views: RemoteViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty()) {
                RemoteViews(sizes.take(16).associateWith { size ->
                    build(context, apps, appWidgetId, folder, items, size.width.toInt(), size.height.toInt())
                })
            } else {
                buildForOptions(context, apps, appWidgetId, folder, items, options)
            }
        } else {
            buildForOptions(context, apps, appWidgetId, folder, items, options)
        }
        manager.updateAppWidget(appWidgetId, views)
    }

    private fun buildForOptions(
        context: Context, apps: InstalledApps, appWidgetId: Int, folder: Folder, items: List<FolderItem>, options: android.os.Bundle,
    ): RemoteViews {
        var width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        var height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (width <= 0 || height <= 0) {
            val (w, h) = WidgetLayoutPolicy.estimatedDp(folder.widgetSize)
            width = w
            height = h
        }
        return build(context, apps, appWidgetId, folder, items, width, height)
    }

    private fun build(
        context: Context,
        apps: InstalledApps,
        appWidgetId: Int,
        folder: Folder,
        items: List<FolderItem>,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val variant = WidgetLayoutPolicy.variantFor(widthDp, heightDp, folder.showLabel)
        val layout = when (variant) {
            WidgetVariant.COMPACT, WidgetVariant.LABELED -> R.layout.widget_folder_compact
            WidgetVariant.WIDE -> R.layout.widget_folder_wide
            WidgetVariant.LARGE -> R.layout.widget_folder_large
        }
        val views = RemoteViews(context.packageName, layout)
        val density = context.resources.displayMetrics.density
        val iconPx = when (variant) {
            WidgetVariant.LARGE -> 64
            WidgetVariant.COMPACT, WidgetVariant.LABELED -> 58
            WidgetVariant.WIDE -> 44
        } * density

        views.setImageViewBitmap(R.id.widget_icon, IconComposer.folderIcon(context, folder, items, apps, iconPx.toInt()))
        views.setTextViewText(R.id.widget_label, folder.name)
        views.setViewVisibility(
            R.id.widget_label,
            if (folder.showLabel && folder.name.isNotBlank() && variant != WidgetVariant.COMPACT) View.VISIBLE else View.GONE,
        )

        // Main tap zone.
        val tap = folder.effectiveTapAction
        val doubleTap = folder.effectiveDoubleTapAction
        when {
            doubleTap != TapAction.NONE ->
                // Both gestures live on the same view; WidgetTapActivity times them apart.
                views.setOnClickPendingIntent(R.id.widget_main, gestureActivity(context, appWidgetId, folder.id))
            tap == TapAction.OPEN_FOLDER ->
                views.setOnClickPendingIntent(R.id.widget_main, openFolder(context, appWidgetId, folder.id, ZONE_MAIN))
            tap == TapAction.SHOW_WIDGET ->
                views.setOnClickPendingIntent(R.id.widget_main, openFolder(context, appWidgetId, folder.id, ZONE_MAIN, showWidget = true))
            tap == TapAction.LAUNCH_DEFAULT_APP -> {
                val app = folder.defaultApp!!
                val item = items.firstOrNull { it.app == app }
                views.setOnClickPendingIntent(R.id.widget_main, launchApp(context, appWidgetId, ZONE_MAIN, app.flatten(), item?.id))
            }
            else -> Unit
        }

        // Corner badge keeps the folder reachable when a tap launches an app instead.
        if (tap != TapAction.OPEN_FOLDER) {
            views.setViewVisibility(R.id.widget_badge, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_badge, openFolder(context, appWidgetId, folder.id, ZONE_BADGE))
        } else {
            views.setViewVisibility(R.id.widget_badge, View.GONE)
        }

        // Quick-launch strip on wide/large widgets.
        val slots = WidgetLayoutPolicy.quickLaunchSlots(widthDp, variant).coerceAtMost(quickSlotIds.size)
        if (slots > 0 && items.isNotEmpty()) {
            views.setViewVisibility(R.id.widget_quick_strip, View.VISIBLE)
            val quickIconPx = (40 * density).toInt()
            items.take(slots).forEachIndexed { index, item ->
                val id = quickSlotIds[index]
                val icon = apps.icon(item.app, quickIconPx)
                if (icon != null) {
                    views.setImageViewBitmap(id, icon)
                    views.setViewVisibility(id, View.VISIBLE)
                    views.setOnClickPendingIntent(id, launchApp(context, appWidgetId, ZONE_QUICK_BASE + index, item.app.flatten(), item.id))
                } else {
                    views.setViewVisibility(id, View.GONE)
                }
            }
            for (index in minOf(slots, items.size) until quickSlotIds.size) {
                views.setViewVisibility(quickSlotIds[index], View.GONE)
            }
        } else if (layout != R.layout.widget_folder_compact) {
            views.setViewVisibility(R.id.widget_quick_strip, View.GONE)
        }
        return views
    }

    private fun unconfigured(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_folder_compact)
        val density = context.resources.displayMetrics.density
        views.setImageViewBitmap(R.id.widget_icon, IconComposer.glyph(context, (48 * density).toInt()))
        views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_not_configured))
        views.setViewVisibility(R.id.widget_label, View.VISIBLE)
        views.setViewVisibility(R.id.widget_badge, View.GONE)
        val intent = Intent(context, FolderWidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        views.setOnClickPendingIntent(R.id.widget_main, PendingIntent.getActivity(context, requestCode(appWidgetId, ZONE_MAIN), intent, immutableFlags()))
        return views
    }

    // ---- pending intents ----

    private fun requestCode(appWidgetId: Int, zone: Int) = appWidgetId * 100 + zone

    private fun immutableFlags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun openFolder(context: Context, appWidgetId: Int, folderId: Long, zone: Int, showWidget: Boolean = false): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(appWidgetId, zone),
            FolderOpenActivity.intent(context, folderId, showWidget),
            immutableFlags(),
        )

    private fun launchApp(context: Context, appWidgetId: Int, zone: Int, flatApp: String, itemId: Long?): PendingIntent {
        val intent = Intent(context, AppLaunchActivity::class.java)
            .putExtra(AppLaunchActivity.EXTRA_APP, flatApp)
            .putExtra(AppLaunchActivity.EXTRA_ITEM_ID, itemId ?: -1L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, requestCode(appWidgetId, zone), intent, immutableFlags())
    }

    private fun gestureActivity(context: Context, appWidgetId: Int, folderId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(appWidgetId, ZONE_MAIN),
            WidgetTapActivity.intent(context, folderId),
            immutableFlags(),
        )
}
