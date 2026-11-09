package com.widgetsplus.core.widget

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * How the folder widget's mosaic fills the space the launcher gives it (all sizes in dp).
 *
 * The widget is the agenda's see-through plate with an 8dp border around a pale panel, and the
 * panel has 6dp of padding. A single-cell widget shows a 2x2 mini mosaic (one tap target).
 * Anything bigger fits as many 34dp app icons, 6dp apart, as the panel holds; if the folder has
 * more apps than spaces the last space becomes "+N", and a folder with fewer apps than spaces
 * uses only the rows (and columns) it needs.
 *
 * With big icons (approved option B, Android 12+, widgets two columns wide or more) the icons are
 * sized like the open folder's instead: as many 48dp icons as fit, each then growing to fill its
 * share of the panel (up to 56dp), and the block of icons is centred, so the spare space is equal
 * on each side. A part-filled last row starts on the left, like any grid.
 */
data class MosaicPlan(
    /** Single cell: a 2x2 mini mosaic and the folder's own single/double tap. */
    val compact: Boolean,
    val columns: Int,
    val rows: Int,
    val iconDp: Float,
    val gapDp: Float,
    /** Apps drawn as icons. */
    val shown: Int,
    /** Apps beyond [shown], counted on the "+N" tile (0 = no tile). */
    val more: Int,
    /** Fewer apps than spaces: the grid is only [rows] tall and centred. */
    val sparse: Boolean,
    /** Each icon's square share of the grid, its gap included: the grid is columns × rows of these. */
    val cellDp: Float = iconDp + gapDp,
    /** Sized by the big-icon rule: the grid is a centred block of [cellDp] squares. */
    val big: Boolean = false,
) {
    /** From the first icon's left edge to the last column's right edge. */
    val iconsWidthDp: Float get() = columns * cellDp - (cellDp - iconDp)
}

object MosaicLayout {
    const val BORDER_DP = 8f
    const val PANEL_PADDING_DP = 6f
    const val ICON_DP = 34f
    const val GAP_DP = 6f
    const val COMPACT_GAP_DP = 4f
    /** Big icons: the size they're fitted at, like the open folder's... */
    const val BIG_ICON_DP = 48f
    /** ...and the most they grow to fill the panel. */
    const val BIG_ICON_MAX_DP = 56f

    /** One launcher cell is narrower than this... */
    const val SINGLE_COLUMN_MAX_DP = 130f
    /** ...and shorter than this (cells are taller than they are wide on phones). */
    const val SINGLE_ROW_MAX_DP = 150f

    fun isCompact(widthDp: Float, heightDp: Float): Boolean =
        widthDp < SINGLE_COLUMN_MAX_DP && heightDp < SINGLE_ROW_MAX_DP

    fun innerWidth(widthDp: Float) = widthDp - 2 * BORDER_DP - 2 * PANEL_PADDING_DP
    fun innerHeight(heightDp: Float) = heightDp - 2 * BORDER_DP - 2 * PANEL_PADDING_DP

    /** The category name pill above the apps (18dp) and its gap. */
    const val NAME_PILL_DP = 18f + GAP_DP

    /**
     * [reservedTopDp] is taken off the panel's height above the apps (the category name pill,
     * [NAME_PILL_DP]); the apps get the rest. [bigIcons]: size the icons like the open folder's
     * (see the class comment); single-column widgets keep 34dp icons either way.
     */
    fun plan(widthDp: Float, heightDp: Float, appCount: Int, reservedTopDp: Float = 0f, bigIcons: Boolean = false): MosaicPlan {
        val iw = innerWidth(widthDp)
        val ih = innerHeight(heightDp) - reservedTopDp
        val count = max(0, appCount)
        if (isCompact(widthDp, heightDp)) {
            val icon = max(8f, (min(iw, ih) - COMPACT_GAP_DP) / 2f)
            val shown = if (count > 4) 3 else count
            return MosaicPlan(true, 2, 2, icon, COMPACT_GAP_DP, shown, count - shown, sparse = false)
        }
        if (bigIcons && widthDp >= SINGLE_COLUMN_MAX_DP) return bigPlan(iw, ih, count)
        var columns = max(1, floor((iw + GAP_DP) / (ICON_DP + GAP_DP)).toInt())
        var rows = max(1, floor((ih + GAP_DP) / (ICON_DP + GAP_DP)).toInt())
        val slots = columns * rows
        val shown = if (count > slots) slots - 1 else count
        val sparse = count < slots
        if (sparse) {
            columns = min(columns, max(1, count))
            rows = max(1, ceil(count / columns.toFloat()).toInt())
        }
        return MosaicPlan(false, columns, rows, ICON_DP, GAP_DP, shown, count - shown, sparse)
    }

    private fun bigPlan(iw: Float, ih: Float, count: Int): MosaicPlan {
        var columns = max(1, floor((iw + GAP_DP) / (BIG_ICON_DP + GAP_DP)).toInt())
        var rows = max(1, floor((ih + GAP_DP) / (BIG_ICON_DP + GAP_DP)).toInt())
        val slots = columns * rows
        val shown = if (count > slots) slots - 1 else count
        val sparse = count < slots
        if (sparse) {
            columns = min(columns, max(1, count))
            rows = max(1, ceil(count / columns.toFloat()).toInt())
        }
        // Each icon's square share of the panel, and the icon inside it with at least the gap.
        val cell = min(iw / columns, ih / rows)
        val icon = min(BIG_ICON_MAX_DP, cell - GAP_DP)
        return MosaicPlan(false, columns, rows, icon, GAP_DP, shown, count - shown, sparse, cellDp = cell, big = true)
    }
}
