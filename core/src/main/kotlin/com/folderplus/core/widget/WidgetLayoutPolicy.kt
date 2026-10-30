package com.folderplus.core.widget

import com.folderplus.core.model.WidgetSize

/** The visual variant a widget renders at a given size. */
enum class WidgetVariant {
    /** Icon only, no label. Fits a 1x1 cell. */
    COMPACT,
    /** Icon with the folder name below it. */
    LABELED,
    /** Icon + name on the left and a row of quick-launch app icons on the right. */
    WIDE,
    /** Large mosaic icon with the name, and a strip of quick-launch icons. */
    LARGE,
}

/**
 * Decides how a widget should look from the actual size the launcher gave it.
 * All inputs are in dp. Launchers report sizes through appWidgetOptions, and the
 * user's preferred [WidgetSize] is only a hint used when the launcher reports
 * nothing (e.g. right after placement on some devices).
 */
object WidgetLayoutPolicy {
    /** Rough Android cell size used to convert a preset into dp. */
    const val CELL_DP = 70

    /** Max quick-launch icons we show in a strip for a given width. */
    fun quickLaunchSlots(widthDp: Int, variant: WidgetVariant): Int = when (variant) {
        WidgetVariant.COMPACT, WidgetVariant.LABELED -> 0
        WidgetVariant.WIDE -> ((widthDp - 90) / 56).coerceIn(0, 6)
        WidgetVariant.LARGE -> ((widthDp - 16) / 56).coerceIn(0, 6)
    }

    fun variantFor(widthDp: Int, heightDp: Int, showLabel: Boolean): WidgetVariant {
        val wide = widthDp >= 2 * CELL_DP - 10
        val tall = heightDp >= 2 * CELL_DP - 10
        return when {
            wide && tall -> WidgetVariant.LARGE
            wide -> WidgetVariant.WIDE
            tall && showLabel -> WidgetVariant.LABELED
            !tall && heightDp >= 96 && showLabel -> WidgetVariant.LABELED
            else -> WidgetVariant.COMPACT
        }
    }

    /** Fallback dimensions when the launcher has not reported a size yet. */
    fun estimatedDp(size: WidgetSize): Pair<Int, Int> = size.columns * CELL_DP to size.rows * CELL_DP

    fun variantFor(size: WidgetSize, showLabel: Boolean): WidgetVariant {
        val (w, h) = estimatedDp(size)
        return variantFor(w, h, showLabel)
    }
}
