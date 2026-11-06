package com.widgetsplus.devices

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.widgetsplus.R
import com.widgetsplus.core.devices.BudLevels
import com.widgetsplus.core.devices.DeviceKind

/**
 * One device on a devices widget: a drawing with a bar, or (no drawing) a ring or its icon.
 * Earbuds may also have their left, right and case levels ([buds], see [BudBattery]); then those
 * show on one line instead of the level, and the bar shows the bud that will run out first.
 */
internal class DeviceColumn(val name: String, val level: Int, val drawing: DeviceDrawing?, val icon: Int, val buds: BudLevels? = null) {
    /** The level for the bar or ring. */
    val shownLevel: Int get() = buds?.lowerBud ?: level
}

/** Earbuds' levels on one line (approved option B): "L 72 · R 70 · Case 40", or "L72 R70 C40". */
internal object BudLine {
    private const val TEXT_DP = 10f
    private const val MIN_TEXT_DP = 8f
    /** The layouts' gaps: label to level, and between levels (around the dot in the long form). */
    private const val LONG_LABEL_GAP_DP = 2.5f
    private const val LONG_SEP_GAP_DP = 2.5f
    private const val SHORT_LABEL_GAP_DP = 0.5f
    private const val SHORT_GAP_DP = 5f

    private val TEXTS = listOf(
        R.id.bl_l_lab, R.id.bl_l_lab_low, R.id.bl_l_val, R.id.bl_l_val_low,
        R.id.bl_r_lab, R.id.bl_r_lab_low, R.id.bl_r_val, R.id.bl_r_val_low,
        R.id.bl_c_lab, R.id.bl_c_lab_low, R.id.bl_c_val, R.id.bl_c_val_low,
    )

    /**
     * The line for [levels]; [short] picks "L72 R70 C40". When it's still wider than
     * [maxWidthDp], its text shrinks to fit (not below 8dp).
     */
    fun views(context: Context, levels: BudLevels, short: Boolean, maxWidthDp: Float): RemoteViews {
        val v = RemoteViews(context.packageName, if (short) R.layout.widget_bud_line_short else R.layout.widget_bud_line)
        part(v, R.id.bl_l_lab, R.id.bl_l_lab_low, R.id.bl_l_val, R.id.bl_l_val_low, levels.left)
        part(v, R.id.bl_r_lab, R.id.bl_r_lab_low, R.id.bl_r_val, R.id.bl_r_val_low, levels.right)
        part(v, R.id.bl_c_lab, R.id.bl_c_lab_low, R.id.bl_c_val, R.id.bl_c_val_low, levels.case)
        // A separator only between two levels that are shown.
        v.setViewVisibility(R.id.bl_sep1, if (levels.left != null && (levels.right != null || levels.case != null)) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.bl_sep2, if (levels.right != null && levels.case != null) View.VISIBLE else View.GONE)
        val width = widthDp(context, levels, short)
        if (width > maxWidthDp) {
            val size = (TEXT_DP * maxWidthDp / width).coerceAtLeast(MIN_TEXT_DP)
            (if (short) TEXTS else TEXTS + R.id.bl_sep1 + R.id.bl_sep2).forEach {
                v.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_DIP, size)
            }
        }
        return v
    }

    /** Label and level in the low-battery colour at 15% or less; hidden when not known. */
    private fun part(v: RemoteViews, label: Int, labelLow: Int, value: Int, valueLow: Int, level: Int?) {
        val low = level != null && DevicesLevels.isLow(level)
        v.setViewVisibility(label, if (level != null && !low) View.VISIBLE else View.GONE)
        v.setViewVisibility(value, if (level != null && !low) View.VISIBLE else View.GONE)
        v.setViewVisibility(labelLow, if (low) View.VISIBLE else View.GONE)
        v.setViewVisibility(valueLow, if (low) View.VISIBLE else View.GONE)
        if (level != null) {
            v.setTextViewText(value, level.toString())
            v.setTextViewText(valueLow, level.toString())
        }
    }

    /** What a screen reader says for the line. */
    fun description(context: Context, levels: BudLevels): String = listOfNotNull(
        levels.left?.let { context.getString(R.string.buds_desc_left, it) },
        levels.right?.let { context.getString(R.string.buds_desc_right, it) },
        levels.case?.let { context.getString(R.string.buds_desc_case, it) },
    ).joinToString(", ")

    /** Whether the long line fits in [widthDp] at full size; otherwise the short one is used. */
    fun longFits(context: Context, levels: BudLevels, widthDp: Float): Boolean = widthDp(context, levels, short = false) <= widthDp

    /** The line's width at full size, measured in the widget's font. */
    private fun widthDp(context: Context, levels: BudLevels, short: Boolean): Float {
        val r = context.resources
        val density = r.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TEXT_DP * density
            typeface = Typeface.create("google-sans-text", Typeface.NORMAL)
        }
        fun dp(text: String) = paint.measureText(text) / density
        val labelGap = if (short) SHORT_LABEL_GAP_DP else LONG_LABEL_GAP_DP
        // The levels are a little heavier (weight 500) than the measuring paint.
        val groups = listOfNotNull(
            levels.left?.let { dp(r.getString(R.string.buds_left)) + labelGap + dp(it.toString()) * 1.06f },
            levels.right?.let { dp(r.getString(R.string.buds_right)) + labelGap + dp(it.toString()) * 1.06f },
            levels.case?.let { dp(r.getString(if (short) R.string.buds_case_short else R.string.buds_case)) + labelGap + dp(it.toString()) * 1.06f },
        )
        val gap = if (short) SHORT_GAP_DP else 2 * LONG_SEP_GAP_DP + dp(r.getString(R.string.buds_separator))
        return groups.sum() + gap * (groups.size - 1).coerceAtLeast(0) + 1f
    }
}

/** Levels at or below this show in the low-battery colour. */
object DevicesLevels {
    const val LOW = 15

    fun isLow(level: Int) = level in 0..LOW
}

/**
 * The flat two-tone drawings from the mockup. Each is a stack of white vector layers, 48dp tall:
 * the main tone, the second tone, and (for a few) details in the main tone drawn on top of the
 * second tone. The widget layout stacks three ImageViews and tints each with a colour resource.
 */
enum class DeviceDrawing(val widthDp: Float, val main: Int, val detail: Int, val top: Int = 0) {
    PHONE(36f, R.drawable.dev_draw_phone_main, R.drawable.dev_draw_phone_detail, R.drawable.dev_draw_phone_top),
    EARBUDS(112f, R.drawable.dev_draw_earbuds_main, R.drawable.dev_draw_earbuds_detail),
    HEADPHONES(60f, R.drawable.dev_draw_headphones_main, R.drawable.dev_draw_headphones_detail),
    WATCH(46f, R.drawable.dev_draw_watch_main, R.drawable.dev_draw_watch_detail, R.drawable.dev_draw_watch_top),
    KEYBOARD(96f, R.drawable.dev_draw_keyboard_main, R.drawable.dev_draw_keyboard_detail),
    MOUSE(32f, R.drawable.dev_draw_mouse_main, R.drawable.dev_draw_mouse_detail),
    GAMEPAD(80f, R.drawable.dev_draw_gamepad_main, R.drawable.dev_draw_gamepad_detail),
    SPEAKER(84f, R.drawable.dev_draw_speaker_main, R.drawable.dev_draw_speaker_detail),
}

/**
 * Ring gauges and bars, drawn as white bitmaps because widgets can't draw arcs themselves; the
 * layout tints them with colour resources so they follow the phone's theme.
 */
object DevicesDraw {
    private const val DRAWING_HEIGHT_DP = 48f

    /** The drawing for a kind of device, or null for kinds that show a ring instead. */
    fun drawingFor(kind: DeviceKind): DeviceDrawing? = when (kind) {
        DeviceKind.EARBUDS -> DeviceDrawing.EARBUDS
        DeviceKind.HEADPHONES -> DeviceDrawing.HEADPHONES
        DeviceKind.WATCH -> DeviceDrawing.WATCH
        DeviceKind.KEYBOARD -> DeviceDrawing.KEYBOARD
        DeviceKind.MOUSE -> DeviceDrawing.MOUSE
        DeviceKind.GAMEPAD -> DeviceDrawing.GAMEPAD
        DeviceKind.SPEAKER -> DeviceDrawing.SPEAKER
        DeviceKind.CAR, DeviceKind.OTHER -> null
    }

    /** A full circle ([level] = 100) or the arc for [level], white. */
    fun ring(context: Context, sizeDp: Float, strokeDp: Float, level: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (sizeDp * d).toInt().coerceAtLeast(1)
        val stroke = strokeDp * d
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        val r = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        p.color = Color.WHITE
        if (level > 0) c.drawArc(r, -90f, 360f * level.coerceIn(0, 100) / 100f, false, p)
        return bmp
    }

    /** The filled part of a bar for [level], white (the track is a shape in the layout). */
    fun bar(context: Context, widthDp: Float, heightDp: Float, level: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (widthDp * d).toInt().coerceAtLeast(1)
        val h = (heightDp * d).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rad = h / 2f
        p.color = Color.WHITE
        val fw = w * level.coerceIn(0, 100) / 100f
        if (fw > 0f) c.drawRoundRect(RectF(0f, 0f, fw.coerceAtLeast(h.toFloat()), h.toFloat()), rad, rad, p)
        return bmp
    }

    /** Icon shown inside the ring, for device types without a drawing. */
    fun iconFor(kind: DeviceKind): Int = when (kind) {
        DeviceKind.EARBUDS -> R.drawable.ic_dev_earbuds
        DeviceKind.HEADPHONES -> R.drawable.ic_dev_headphones
        DeviceKind.WATCH -> R.drawable.ic_dev_watch
        DeviceKind.KEYBOARD -> R.drawable.ic_dev_keyboard
        DeviceKind.MOUSE -> R.drawable.ic_dev_mouse
        DeviceKind.GAMEPAD -> R.drawable.ic_dev_gamepad
        DeviceKind.SPEAKER -> R.drawable.ic_dev_speaker
        DeviceKind.CAR -> R.drawable.ic_dev_car
        DeviceKind.OTHER -> R.drawable.ic_dev_bluetooth
    }
}
