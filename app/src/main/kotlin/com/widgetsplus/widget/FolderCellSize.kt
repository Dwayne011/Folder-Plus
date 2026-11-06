package com.widgetsplus.widget

import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import com.widgetsplus.R
import com.widgetsplus.core.widget.MosaicPlan

/**
 * Big icons (MosaicLayout, approved option B) set each cell's icon and "+N" tile to the plan's
 * size; the layouts' own 34dp is the size for every other plan. Views can be sized from
 * Android 12, the only versions that get big icons.
 */
internal object FolderCellSize {
    /** "+N" text at the same share of the tile as the layout's 12dp on 34dp. */
    private const val MORE_TEXT_RATIO = 12f / 34f

    fun apply(cell: RemoteViews, plan: MosaicPlan) {
        if (!plan.big || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        cell.setViewLayoutWidth(R.id.cell_icon, plan.iconDp, TypedValue.COMPLEX_UNIT_DIP)
        cell.setViewLayoutHeight(R.id.cell_icon, plan.iconDp, TypedValue.COMPLEX_UNIT_DIP)
    }

    fun applyMore(cell: RemoteViews, plan: MosaicPlan) {
        if (!plan.big || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        cell.setViewLayoutWidth(R.id.cell_more, plan.iconDp, TypedValue.COMPLEX_UNIT_DIP)
        cell.setViewLayoutHeight(R.id.cell_more, plan.iconDp, TypedValue.COMPLEX_UNIT_DIP)
        cell.setTextViewTextSize(R.id.cell_more_text, TypedValue.COMPLEX_UNIT_DIP, plan.iconDp * MORE_TEXT_RATIO)
    }
}
