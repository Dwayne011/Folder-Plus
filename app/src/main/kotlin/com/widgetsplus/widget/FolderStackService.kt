package com.widgetsplus.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.RequiresApi
import com.widgetsplus.R
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.FolderTabContents
import com.widgetsplus.core.model.NameAlign
import com.widgetsplus.core.model.TabContent
import com.widgetsplus.core.widget.MosaicLayout
import com.widgetsplus.core.widget.MosaicPlan
import com.widgetsplus.graph
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/** Supplies the category cards of a folder widget that swipes between categories. */
class FolderStackService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        FolderStackFactory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
            intent.getFloatExtra(EXTRA_WIDTH_DP, 0f),
            intent.getFloatExtra(EXTRA_HEIGHT_DP, 0f),
        )

    companion object {
        const val EXTRA_WIDTH_DP = "width_dp"
        const val EXTRA_HEIGHT_DP = "height_dp"
    }
}

/**
 * One card per tab of the open folder, in the same order ([FolderTabContents]): the tab's apps as
 * the mosaic for this widget size, "+N" counting that tab's apps that don't fit, and optionally
 * the tab's name in a pill at the top. Each card is exactly the frame's height (see
 * widget_folder_stack.xml). Taps go through the stack's template to [WidgetTapActivity]: an app
 * opens (a double tap opens the folder instead), "+N" and gaps open the folder on that tab.
 */
class FolderStackFactory(
    private val context: Context,
    private val appWidgetId: Int,
    private val widthDp: Float,
    private val heightDp: Float,
) : RemoteViewsService.RemoteViewsFactory {
    private var folder: Folder? = null
    private var tabs: List<TabContent> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        // Called on a binder thread, where blocking is allowed.
        runBlocking {
            val graph = context.graph
            val repo = graph.repository
            val loaded = repo.folderIdForWidget(appWidgetId)?.let { repo.getFolder(it) }
            folder = loaded
            if (loaded == null) {
                tabs = emptyList()
                return@runBlocking
            }
            val apps = graph.installedApps
            apps.ensureLoaded()
            val items = repo.getItems(loaded.id).filter { apps.isInstalled(it.app) }
            tabs = FolderTabContents.build(loaded, repo.getCategories(loaded.id), items) { apps.label(it) }
        }
    }

    override fun onDestroy() {
        tabs = emptyList()
    }

    override fun getCount(): Int = tabs.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tabs.getOrNull(position)?.key?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val f = folder
        val tab = tabs.getOrNull(position)
        if (f == null || tab == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return RemoteViews(context.packageName, R.layout.widget_folder_card)
        }
        return card(f, tab)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun card(folder: Folder, tab: TabContent): RemoteViews {
        val dip = TypedValue.COMPLEX_UNIT_DIP
        val views = RemoteViews(context.packageName, R.layout.widget_folder_card)
        val oneColumn = widthDp < MosaicLayout.SINGLE_COLUMN_MAX_DP
        views.setViewLayoutHeight(R.id.fcard_root, heightDp - 2 * MosaicLayout.BORDER_DP, dip)
        views.setImageViewResource(R.id.fcard_panel, if (oneColumn) R.drawable.folder_panel_small else R.drawable.agenda_shape_group)
        views.setContentDescription(R.id.fcard_root, title(tab))

        // The category's name, when the folder shows it.
        val pill = folder.showCategoryName
        if (pill) {
            val fullColumns = MosaicLayout.plan(widthDp, heightDp, Int.MAX_VALUE, MosaicLayout.NAME_PILL_DP).columns
            val rowWidth = if (oneColumn) widthDp - 2 * MosaicLayout.BORDER_DP - 2 * ONE_COLUMN_PILL_INSET_DP
            else fullColumns * MosaicLayout.ICON_DP + (fullColumns - 1) * MosaicLayout.GAP_DP
            views.setViewVisibility(R.id.fcard_pill_row, View.VISIBLE)
            views.setViewLayoutWidth(R.id.fcard_pill_row, rowWidth, dip)
            val (pillId, textId) = when {
                oneColumn -> R.id.fcard_pill_full to R.id.fcard_pill_full_text
                folder.categoryNameAlign == NameAlign.LEFT -> R.id.fcard_pill_left to R.id.fcard_pill_left_text
                folder.categoryNameAlign == NameAlign.RIGHT -> R.id.fcard_pill_right to R.id.fcard_pill_right_text
                else -> R.id.fcard_pill_center to R.id.fcard_pill_center_text
            }
            views.setViewVisibility(pillId, View.VISIBLE)
            views.setTextViewText(textId, title(tab))
        } else {
            views.setViewVisibility(R.id.fcard_pill_row, View.GONE)
        }

        // The apps: as many as fit below the pill, "+N" for the rest of this category.
        val plan = MosaicLayout.plan(widthDp, heightDp, tab.items.size, if (pill) MosaicLayout.NAME_PILL_DP else 0f)
        val cellDp = plan.iconDp + plan.gapDp
        views.removeAllViews(R.id.fcard_grid)
        views.setViewLayoutWidth(R.id.fcard_grid, plan.columns * cellDp, dip)
        views.setViewLayoutHeight(R.id.fcard_grid, plan.rows * cellDp, dip)
        val apps = context.graph.installedApps
        val iconPx = (plan.iconDp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        var index = 0
        for (r in 0 until plan.rows) {
            val row = RemoteViews(context.packageName, R.layout.widget_folder_row)
            for (c in 0 until plan.columns) {
                val i = index++
                val cell = when {
                    i < plan.shown -> appCell(folder, tab, tab.items[i], iconPx)
                    i == plan.shown && plan.more > 0 -> moreCell(folder, tab, plan)
                    else -> RemoteViews(context.packageName, R.layout.widget_folder_cell)
                }
                row.addView(R.id.folder_row, cell)
            }
            views.addView(R.id.fcard_grid, row)
        }
        // A gap opens the folder on this category.
        views.setOnClickFillInIntent(R.id.fcard_root, fillIn(folder, tab, openNow = true))
        return views
    }

    private fun appCell(folder: Folder, tab: TabContent, item: FolderItem, iconPx: Int): RemoteViews {
        val apps = context.graph.installedApps
        val cell = RemoteViews(context.packageName, R.layout.widget_folder_cell)
        apps.icon(item.app, iconPx)?.let { cell.setImageViewBitmap(R.id.cell_icon, it) }
        cell.setContentDescription(R.id.cell_icon, apps.label(item.app))
        cell.setOnClickFillInIntent(
            R.id.cell_icon,
            fillIn(folder, tab)
                .putExtra(WidgetTapActivity.EXTRA_APP, item.app.flatten())
                .putExtra(WidgetTapActivity.EXTRA_ITEM_ID, item.id),
        )
        return cell
    }

    private fun moreCell(folder: Folder, tab: TabContent, plan: MosaicPlan): RemoteViews {
        val cell = RemoteViews(context.packageName, R.layout.widget_folder_cell_more)
        cell.setTextViewText(R.id.cell_more_text, "+${plan.more}")
        cell.setContentDescription(R.id.cell_more_text, context.resources.getQuantityString(R.plurals.widget_more_apps, plan.more, plan.more))
        cell.setOnClickFillInIntent(R.id.cell_more, fillIn(folder, tab, openNow = true))
        return cell
    }

    private fun fillIn(folder: Folder, tab: TabContent, openNow: Boolean = false): Intent =
        Intent()
            .putExtra(WidgetTapActivity.EXTRA_FOLDER_ID, folder.id)
            .putExtra(WidgetTapActivity.EXTRA_TAB, tab.key)
            .putExtra(WidgetTapActivity.EXTRA_OPEN_NOW, openNow)

    private fun title(tab: TabContent): String = when (tab.key) {
        FolderTabContents.KEY_ALL -> context.getString(R.string.category_all)
        FolderTabContents.KEY_OTHER -> context.getString(R.string.category_other)
        else -> tab.category?.name.orEmpty()
    }

    private companion object {
        /** A one-column widget's pill stops this far in from the panel's edges. */
        const val ONE_COLUMN_PILL_INSET_DP = 5f
    }
}
