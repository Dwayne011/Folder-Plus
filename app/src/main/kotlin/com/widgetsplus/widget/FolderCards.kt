package com.widgetsplus.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.widgetsplus.R
import com.widgetsplus.apps.InstalledApps
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.FolderTabContents
import com.widgetsplus.core.model.NameAlign
import com.widgetsplus.core.model.TabContent
import com.widgetsplus.core.widget.CardLoop
import com.widgetsplus.core.widget.MosaicLayout
import com.widgetsplus.core.widget.MosaicPlan
import kotlin.math.roundToInt

/**
 * The category cards of a folder widget that swipes between categories: one card per tab of the
 * open folder, in the same order ([FolderTabContents]). Each card is the tab's apps as the mosaic
 * for this widget size, "+N" counting that tab's apps that don't fit, and optionally the tab's
 * name in a pill at the top. Each card is exactly the frame's height (see
 * widget_folder_stack.xml).
 *
 * The cards go out inside the widget's own update ([RemoteViews.RemoteCollectionItems]), so the
 * launcher never has to ask a service for them. Taps go through the stack's template to
 * [WidgetTapActivity]: an app opens (a double tap opens the folder instead), "+N" and gaps open
 * the folder on that tab.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal class FolderCards(
    private val context: Context,
    private val apps: InstalledApps,
    private val folder: Folder,
    private val widthDp: Float,
    private val heightDp: Float,
) {
    /**
     * The cards in the loop's order, starting from the tab whose key is [lastTab] (the first tab
     * when there isn't one): the widget shows card 0 on every update ([CardLoop]).
     */
    fun items(tabs: List<TabContent>, lastTab: String?): RemoteViews.RemoteCollectionItems {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(1)
        CardLoop.order(tabs, { it.key }, lastTab).forEach { tab -> builder.addItem(stableId(tab), card(tab)) }
        return builder.build()
    }

    /** Unique per tab: a category's own id, or a negative number for All and Other. */
    private fun stableId(tab: TabContent): Long = when (tab.key) {
        FolderTabContents.KEY_ALL -> -1L
        FolderTabContents.KEY_OTHER -> -2L
        else -> tab.category?.id ?: -3L
    }

    private fun card(tab: TabContent): RemoteViews {
        val dip = TypedValue.COMPLEX_UNIT_DIP
        val views = RemoteViews(context.packageName, R.layout.widget_folder_card)
        val oneColumn = widthDp < MosaicLayout.SINGLE_COLUMN_MAX_DP
        // Exactly the frame's size: the stack measures cards with "at most" sizes, so a card left
        // to match its parent would shrink to its icons and sit on the left.
        views.setViewLayoutWidth(R.id.fcard_root, widthDp - 2 * MosaicLayout.BORDER_DP, dip)
        views.setViewLayoutHeight(R.id.fcard_root, heightDp - 2 * MosaicLayout.BORDER_DP, dip)
        views.setImageViewResource(R.id.fcard_panel, if (oneColumn) R.drawable.folder_panel_small else R.drawable.agenda_shape_group)
        views.setContentDescription(R.id.fcard_root, title(tab))

        // The category's name, when the folder shows it.
        val pill = folder.showCategoryName
        if (pill) {
            // As wide as a full row of icons, so left and right line up with the icons' edges.
            val full = MosaicLayout.plan(widthDp, heightDp, Int.MAX_VALUE, MosaicLayout.NAME_PILL_DP, bigIcons = true)
            val rowWidth = if (oneColumn) widthDp - 2 * MosaicLayout.BORDER_DP - 2 * ONE_COLUMN_PILL_INSET_DP
            else full.iconsWidthDp
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
        val plan = MosaicLayout.plan(widthDp, heightDp, tab.items.size, if (pill) MosaicLayout.NAME_PILL_DP else 0f, bigIcons = true)
        val cellDp = plan.cellDp
        views.removeAllViews(R.id.fcard_grid)
        views.setViewLayoutWidth(R.id.fcard_grid, plan.columns * cellDp, dip)
        views.setViewLayoutHeight(R.id.fcard_grid, plan.rows * cellDp, dip)
        val iconPx = (plan.iconDp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        var index = 0
        for (r in 0 until plan.rows) {
            val row = RemoteViews(context.packageName, R.layout.widget_folder_row)
            for (c in 0 until plan.columns) {
                val i = index++
                val cell = when {
                    i < plan.shown -> appCell(tab, tab.items[i], iconPx, plan)
                    i == plan.shown && plan.more > 0 -> moreCell(tab, plan)
                    else -> RemoteViews(context.packageName, R.layout.widget_folder_cell)
                }
                row.addView(R.id.folder_row, cell)
            }
            views.addView(R.id.fcard_grid, row)
        }
        // A gap opens the folder on this category.
        views.setOnClickFillInIntent(R.id.fcard_root, fillIn(tab, openNow = true))
        return views
    }

    private fun appCell(tab: TabContent, item: FolderItem, iconPx: Int, plan: MosaicPlan): RemoteViews {
        val cell = RemoteViews(context.packageName, R.layout.widget_folder_cell)
        apps.icon(item.app, iconPx)?.let { cell.setImageViewBitmap(R.id.cell_icon, it) }
        cell.setContentDescription(R.id.cell_icon, apps.label(item.app))
        FolderCellSize.apply(cell, plan)
        cell.setOnClickFillInIntent(
            R.id.cell_icon,
            fillIn(tab)
                .putExtra(WidgetTapActivity.EXTRA_APP, item.app.flatten())
                .putExtra(WidgetTapActivity.EXTRA_ITEM_ID, item.id),
        )
        return cell
    }

    private fun moreCell(tab: TabContent, plan: MosaicPlan): RemoteViews {
        val cell = RemoteViews(context.packageName, R.layout.widget_folder_cell_more)
        cell.setTextViewText(R.id.cell_more_text, "+${plan.more}")
        cell.setContentDescription(R.id.cell_more_text, context.resources.getQuantityString(R.plurals.widget_more_apps, plan.more, plan.more))
        FolderCellSize.applyMore(cell, plan)
        cell.setOnClickFillInIntent(R.id.cell_more, fillIn(tab, openNow = true))
        return cell
    }

    private fun fillIn(tab: TabContent, openNow: Boolean = false): Intent =
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
