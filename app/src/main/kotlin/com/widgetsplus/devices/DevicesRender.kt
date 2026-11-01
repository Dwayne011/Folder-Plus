package com.widgetsplus.devices

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.widgetsplus.R
import com.widgetsplus.core.devices.DeviceKind

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
