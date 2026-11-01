package com.widgetsplus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.widgetsplus.R
import com.widgetsplus.apps.InstalledApps
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderIconMode
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.TapAction
import com.widgetsplus.core.sort.AppSorter
import com.widgetsplus.core.widget.MosaicLayout
import com.widgetsplus.core.widget.MosaicPlan
import com.widgetsplus.core.widget.WidgetLayoutPolicy
import com.widgetsplus.graph
import com.widgetsplus.launch.AppLaunchActivity
import com.widgetsplus.ui.open.FolderOpenActivity
import kotlin.math.roundToInt

/**
 * Turns a folder into RemoteViews for one placed widget, in the agenda's style (approved mockup):
 * the see-through plate, an 8dp border and the pale panel holding a mosaic of the folder's apps
 * that fills whatever size the widget is (see [MosaicLayout]). The folder's name is never shown
 * on the widget, only in the open folder.
 *
 * Taps: a single-cell widget is one target with the folder's own single and double tap. On
 * anything bigger, tapping an app opens it, and "+N", a gap or a double tap opens the folder.
 *
 * Colours all come from the layouts' colour resources (none are set from here), so the launcher
 * re-resolves them when the phone's theme or wallpaper colours change.
 */
object WidgetRenderer {

    private const val ZONE_MAIN = 0
    private const val ZONE_BADGE = 1
    private const val ZONE_MORE = 2
    private const val ZONE_ICON_BASE = 10
    private const val MAX_ICONS = 900
    /** ViewGroup.LayoutParams.MATCH_PARENT, as setViewLayoutWidth/Height take it (with COMPLEX_UNIT_PX). */
    private const val MATCH_PARENT = -1f

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
            .filter { apps.isInstalled(it.app) }
        val options = manager.getAppWidgetOptions(appWidgetId)

        val views: RemoteViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty()) {
                RemoteViews(sizes.take(16).associateWith { size -> build(context, apps, appWidgetId, folder, items, size.width, size.height) })
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
        return build(context, apps, appWidgetId, folder, items, width.toFloat(), height.toFloat())
    }

    private fun build(
        context: Context,
        apps: InstalledApps,
        appWidgetId: Int,
        folder: Folder,
        items: List<FolderItem>,
        widthDp: Float,
        heightDp: Float,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_folder_mosaic)
        val oneColumn = widthDp < MosaicLayout.SINGLE_COLUMN_MAX_DP
        views.setImageViewResource(R.id.folder_plate, if (oneColumn) R.drawable.folder_plate_small else R.drawable.agenda_shape_plate)
        views.setImageViewResource(R.id.folder_hairline, if (oneColumn) R.drawable.folder_hairline_small else R.drawable.agenda_hairline)
        views.setImageViewResource(R.id.folder_panel, if (oneColumn) R.drawable.folder_panel_small else R.drawable.agenda_shape_group)
        // A reapplied update would otherwise add a second set of rows.
        views.removeAllViews(R.id.folder_grid)
        views.setContentDescription(R.id.folder_root, folder.title(context))

        val density = context.resources.displayMetrics.density
        val mosaic = folder.iconMode == FolderIconMode.MOSAIC && items.isNotEmpty()
        if (!mosaic) {
            // "Default app" and "Folder glyph" icon modes (and empty folders): one icon on the panel.
            views.setViewVisibility(R.id.folder_grid, View.GONE)
            views.setViewVisibility(R.id.folder_single, View.VISIBLE)
            val px = (minOf(64f, minOf(MosaicLayout.innerWidth(widthDp), MosaicLayout.innerHeight(heightDp))) * density).roundToInt().coerceAtLeast(1)
            val icon = folder.defaultApp?.takeIf { folder.iconMode == FolderIconMode.DEFAULT_APP }?.let { apps.icon(it, px) }
            views.setImageViewBitmap(R.id.folder_single, icon ?: IconComposer.glyph(context, px))
            folderTaps(context, views, appWidgetId, folder, items)
            return views
        }

        views.setViewVisibility(R.id.folder_single, View.GONE)
        views.setViewVisibility(R.id.folder_grid, View.VISIBLE)
        val plan = MosaicLayout.plan(widthDp, heightDp, items.size)
        val iconPx = (plan.iconDp * density).roundToInt().coerceAtLeast(1)
        sizeGrid(views, plan, heightDp)

        var index = 0
        for (r in 0 until plan.rows) {
            val row = RemoteViews(context.packageName, R.layout.widget_folder_row)
            for (c in 0 until plan.columns) {
                val i = index++
                val cell = when {
                    i < plan.shown -> appCell(context, apps, appWidgetId, folder, items[i], i, plan, iconPx)
                    i == plan.shown && plan.more > 0 -> moreCell(context, appWidgetId, folder, plan)
                    // keeps the last row's icons in their columns
                    else -> RemoteViews(context.packageName, if (plan.compact) R.layout.widget_folder_cell_compact else R.layout.widget_folder_cell)
                }
                row.addView(R.id.folder_row, cell)
            }
            views.addView(R.id.folder_grid, row)
        }

        if (plan.compact) {
            folderTaps(context, views, appWidgetId, folder, items)
        } else {
            // Gaps open the folder; the apps and "+N" have their own taps.
            views.setOnClickPendingIntent(R.id.folder_root, openFolder(context, appWidgetId, folder.id, ZONE_MAIN))
            views.setViewVisibility(R.id.folder_badge, View.GONE)
        }
        return views
    }

    /** Square 2x2 mini mosaic, or only the rows a sparse folder needs (Android 12+ can size views). */
    private fun sizeGrid(views: RemoteViews, plan: MosaicPlan, heightDp: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val pad = 2 * MosaicLayout.PANEL_PADDING_DP
        when {
            plan.compact -> {
                val side = 2 * plan.iconDp + plan.gapDp + pad
                views.setViewLayoutWidth(R.id.folder_grid, side, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.folder_grid, side, TypedValue.COMPLEX_UNIT_DIP)
            }
            plan.sparse -> {
                views.setViewLayoutWidth(R.id.folder_grid, MATCH_PARENT, TypedValue.COMPLEX_UNIT_PX)
                val h = plan.rows * plan.iconDp + (plan.rows - 1) * plan.gapDp + pad
                views.setViewLayoutHeight(R.id.folder_grid, minOf(h, MosaicLayout.innerHeight(heightDp) + pad), TypedValue.COMPLEX_UNIT_DIP)
            }
            else -> {
                // Reset in case this view tree last showed a mini or sparse mosaic.
                views.setViewLayoutWidth(R.id.folder_grid, MATCH_PARENT, TypedValue.COMPLEX_UNIT_PX)
                views.setViewLayoutHeight(R.id.folder_grid, MATCH_PARENT, TypedValue.COMPLEX_UNIT_PX)
            }
        }
    }

    private fun appCell(
        context: Context, apps: InstalledApps, appWidgetId: Int, folder: Folder, item: FolderItem, index: Int, plan: MosaicPlan, iconPx: Int,
    ): RemoteViews {
        val cell = RemoteViews(context.packageName, if (plan.compact) R.layout.widget_folder_cell_compact else R.layout.widget_folder_cell)
        apps.icon(item.app, iconPx)?.let { cell.setImageViewBitmap(R.id.cell_icon, it) }
        cell.setContentDescription(R.id.cell_icon, apps.label(item.app))
        if (!plan.compact && index < MAX_ICONS) {
            // Through the tap activity, so a quick second tap opens the folder instead.
            val intent = WidgetTapActivity.appIntent(context, folder.id, item.app.flatten(), item.id)
            cell.setOnClickPendingIntent(R.id.cell_icon, PendingIntent.getActivity(context, requestCode(appWidgetId, ZONE_ICON_BASE + index), intent, immutableFlags()))
        }
        return cell
    }

    private fun moreCell(context: Context, appWidgetId: Int, folder: Folder, plan: MosaicPlan): RemoteViews {
        val cell = RemoteViews(context.packageName, if (plan.compact) R.layout.widget_folder_cell_more_compact else R.layout.widget_folder_cell_more)
        cell.setTextViewText(R.id.cell_more_text, "+${plan.more}")
        cell.setContentDescription(R.id.cell_more_text, context.resources.getQuantityString(R.plurals.widget_more_apps, plan.more, plan.more))
        if (plan.compact) {
            // Scale the count with the mini mosaic's tiles.
            cell.setTextViewTextSize(R.id.cell_more_text, TypedValue.COMPLEX_UNIT_DIP, (plan.iconDp * 0.42f).coerceIn(7f, 12f))
        } else {
            cell.setOnClickPendingIntent(R.id.cell_more, openFolder(context, appWidgetId, folder.id, ZONE_MORE))
        }
        return cell
    }

    /** One target for the whole widget: the folder's single and double tap, as set in its Taps page. */
    private fun folderTaps(context: Context, views: RemoteViews, appWidgetId: Int, folder: Folder, items: List<FolderItem>) {
        val tap = folder.effectiveTapAction
        val doubleTap = folder.effectiveDoubleTapAction
        val main: PendingIntent? = when {
            // Both gestures live on the same view; WidgetTapActivity times them apart.
            doubleTap != TapAction.NONE -> gestureActivity(context, appWidgetId, folder.id)
            tap == TapAction.OPEN_FOLDER -> openFolder(context, appWidgetId, folder.id, ZONE_MAIN)
            tap == TapAction.SHOW_WIDGET -> openFolder(context, appWidgetId, folder.id, ZONE_MAIN, showWidget = true)
            tap == TapAction.LAUNCH_DEFAULT_APP -> folder.defaultApp?.let { app ->
                launchApp(context, appWidgetId, ZONE_MAIN, app.flatten(), items.firstOrNull { it.app == app }?.id)
            }
            else -> null
        }
        views.setOnClickPendingIntent(R.id.folder_root, main)
        // The corner badge keeps the folder reachable when neither tap opens it.
        val reachable = tap == TapAction.OPEN_FOLDER || doubleTap == TapAction.OPEN_FOLDER
        views.setViewVisibility(R.id.folder_badge, if (reachable) View.GONE else View.VISIBLE)
        if (!reachable) views.setOnClickPendingIntent(R.id.folder_badge, openFolder(context, appWidgetId, folder.id, ZONE_BADGE))
    }

    private fun unconfigured(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_folder_mosaic)
        val density = context.resources.displayMetrics.density
        views.removeAllViews(R.id.folder_grid)
        views.setViewVisibility(R.id.folder_grid, View.GONE)
        views.setViewVisibility(R.id.folder_single, View.VISIBLE)
        views.setImageViewBitmap(R.id.folder_single, IconComposer.glyph(context, (48 * density).toInt()))
        views.setContentDescription(R.id.folder_root, context.getString(R.string.widget_setup_prompt))
        views.setViewVisibility(R.id.folder_badge, View.GONE)
        val intent = Intent(context, FolderWidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        views.setOnClickPendingIntent(R.id.folder_root, PendingIntent.getActivity(context, requestCode(appWidgetId, ZONE_MAIN), intent, immutableFlags()))
        return views
    }

    private fun Folder.title(context: Context): String = name.ifBlank { context.getString(R.string.untitled_folder) }

    // ---- pending intents ----

    private fun requestCode(appWidgetId: Int, zone: Int) = appWidgetId * 1000 + zone

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
