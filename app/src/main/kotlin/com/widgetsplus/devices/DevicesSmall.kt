package com.widgetsplus.devices

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.widgetsplus.R

/**
 * Fills the connected devices widget's 2×2 arrangement (widget_devices_small, approved mockup)
 * with this phone and the connected devices, this phone first. Nothing connected: this phone
 * alone, big and centred. One device: two rows, each stacked (drawing, name, level). Two or three
 * devices: three or four rows with the drawing on the left; the rows share the height, and three
 * rows get bigger drawings and text than four. Android 8 to 11 keep the four-row sizes.
 */
internal object DevicesSmall {

    private class Ids(
        val root: Int, val box: Int,
        val main: Int, val detail: Int, val top: Int, val icon: Int,
        val name: Int, val nameLow: Int, val sub: Int, val subLow: Int,
        val bar: Int, val barFill: Int, val barLow: Int,
    )

    private val SINGLE = Ids(
        R.id.ds1, R.id.ds1_box, R.id.ds1_draw_main, R.id.ds1_draw_detail, R.id.ds1_draw_top, R.id.ds1_icon,
        R.id.ds1_name, R.id.ds1_name_low, R.id.ds1_sub, R.id.ds1_sub_low, R.id.ds1_bar, R.id.ds1_bar_fill, R.id.ds1_bar_low,
    )
    private val TWO = listOf(
        Ids(
            R.id.dt1, R.id.dt1_box, R.id.dt1_draw_main, R.id.dt1_draw_detail, R.id.dt1_draw_top, R.id.dt1_icon,
            R.id.dt1_name, R.id.dt1_name_low, R.id.dt1_sub, R.id.dt1_sub_low, R.id.dt1_bar, R.id.dt1_bar_fill, R.id.dt1_bar_low,
        ),
        Ids(
            R.id.dt2, R.id.dt2_box, R.id.dt2_draw_main, R.id.dt2_draw_detail, R.id.dt2_draw_top, R.id.dt2_icon,
            R.id.dt2_name, R.id.dt2_name_low, R.id.dt2_sub, R.id.dt2_sub_low, R.id.dt2_bar, R.id.dt2_bar_fill, R.id.dt2_bar_low,
        ),
    )
    private val LIST = listOf(
        Ids(
            R.id.dl1, R.id.dl1_box, R.id.dl1_draw_main, R.id.dl1_draw_detail, R.id.dl1_draw_top, R.id.dl1_icon,
            R.id.dl1_name, R.id.dl1_name_low, R.id.dl1_sub, R.id.dl1_sub_low, R.id.dl1_bar, R.id.dl1_bar_fill, R.id.dl1_bar_low,
        ),
        Ids(
            R.id.dl2, R.id.dl2_box, R.id.dl2_draw_main, R.id.dl2_draw_detail, R.id.dl2_draw_top, R.id.dl2_icon,
            R.id.dl2_name, R.id.dl2_name_low, R.id.dl2_sub, R.id.dl2_sub_low, R.id.dl2_bar, R.id.dl2_bar_fill, R.id.dl2_bar_low,
        ),
        Ids(
            R.id.dl3, R.id.dl3_box, R.id.dl3_draw_main, R.id.dl3_draw_detail, R.id.dl3_draw_top, R.id.dl3_icon,
            R.id.dl3_name, R.id.dl3_name_low, R.id.dl3_sub, R.id.dl3_sub_low, R.id.dl3_bar, R.id.dl3_bar_fill, R.id.dl3_bar_low,
        ),
        Ids(
            R.id.dl4, R.id.dl4_box, R.id.dl4_draw_main, R.id.dl4_draw_detail, R.id.dl4_draw_top, R.id.dl4_icon,
            R.id.dl4_name, R.id.dl4_name_low, R.id.dl4_sub, R.id.dl4_sub_low, R.id.dl4_bar, R.id.dl4_bar_fill, R.id.dl4_bar_low,
        ),
    )

    /** Sizes for three and four rows (the layout's own are four rows'). */
    private class Fit(val boxW: Float, val boxH: Float, val name: Float, val sub: Float)
    private val THREE_ROWS = Fit(34f, 26f, 12f, 10.5f)
    private val FOUR_ROWS = Fit(30f, 22f, 11.5f, 10f)

    /** The 2×2 widget's width when the launcher doesn't say. */
    private const val DEFAULT_WIDTH_DP = 138f
    /** Around a list row's bar: the well's 8dp inset and the rows' 10dp padding each side, the 10dp after the drawing, and the level (6dp gap, 26dp). */
    private const val LIST_ROW_FIXED_DP = 2 * 8f + 2 * 10f + 10f + 6f + 26f

    fun fill(context: Context, v: RemoteViews, columns: List<DeviceColumn>, widthDp: Float) {
        val n = columns.size
        v.setViewVisibility(R.id.ds1, if (n == 1) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.ds2, if (n == 2) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.ds3, if (n >= 3) View.VISIBLE else View.GONE)
        when (n) {
            0 -> Unit
            1 -> slot(context, v, SINGLE, columns[0], 40f, 5f)
            2 -> TWO.forEachIndexed { i, ids -> slot(context, v, ids, columns[i], 56f, 6f) }
            else -> {
                val sized = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val fit = if (n == 3 && sized) THREE_ROWS else FOUR_ROWS
                val width = if (widthDp > 0f) widthDp else DEFAULT_WIDTH_DP
                val barW = if (sized) (width - LIST_ROW_FIXED_DP - fit.boxW).coerceAtLeast(16f) else 30f
                LIST.forEachIndexed { i, ids ->
                    val c = columns.getOrNull(i)
                    v.setViewVisibility(ids.root, if (c == null) View.GONE else View.VISIBLE)
                    if (c == null) return@forEachIndexed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val dip = TypedValue.COMPLEX_UNIT_DIP
                        v.setViewLayoutWidth(ids.box, fit.boxW, dip)
                        v.setViewLayoutHeight(ids.box, fit.boxH, dip)
                        listOf(ids.name, ids.nameLow).forEach { v.setTextViewTextSize(it, dip, fit.name) }
                        listOf(ids.sub, ids.subLow).forEach { v.setTextViewTextSize(it, dip, fit.sub) }
                        v.setViewLayoutWidth(ids.bar, barW, dip)
                    }
                    slot(context, v, ids, c, barW, 5f)
                }
            }
        }
    }

    private fun slot(context: Context, v: RemoteViews, ids: Ids, c: DeviceColumn, barW: Float, barH: Float) {
        val low = DevicesLevels.isLow(c.level)
        v.setTextViewText(ids.name, c.name)
        v.setTextViewText(ids.nameLow, c.name)
        v.setViewVisibility(ids.name, if (low) View.GONE else View.VISIBLE)
        v.setViewVisibility(ids.nameLow, if (low) View.VISIBLE else View.GONE)
        val level = "${c.level}%"
        v.setTextViewText(ids.sub, level)
        v.setTextViewText(ids.subLow, level)
        v.setViewVisibility(ids.sub, if (low) View.GONE else View.VISIBLE)
        v.setViewVisibility(ids.subLow, if (low) View.VISIBLE else View.GONE)
        val drawing = c.drawing
        if (drawing != null) {
            listOf(ids.main to drawing.main, ids.detail to drawing.detail, ids.top to drawing.top).forEach { (id, res) ->
                v.setViewVisibility(id, if (res == 0) View.GONE else View.VISIBLE)
                if (res != 0) v.setImageViewResource(id, res)
            }
            v.setViewVisibility(ids.icon, View.GONE)
        } else {
            // Device types without a drawing show their icon.
            listOf(ids.main, ids.detail, ids.top).forEach { v.setViewVisibility(it, View.GONE) }
            v.setViewVisibility(ids.icon, View.VISIBLE)
            v.setImageViewResource(ids.icon, c.icon)
        }
        val bar = DevicesDraw.bar(context, barW, barH, c.level)
        v.setImageViewBitmap(if (low) ids.barLow else ids.barFill, bar)
        v.setViewVisibility(ids.barFill, if (low) View.GONE else View.VISIBLE)
        v.setViewVisibility(ids.barLow, if (low) View.VISIBLE else View.GONE)
    }
}
