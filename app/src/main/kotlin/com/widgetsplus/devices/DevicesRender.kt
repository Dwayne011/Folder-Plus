package com.widgetsplus.devices

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.widgetsplus.R

/** Colours of the devices widget (Material You on Android 12+, spec palette before). */
class DevicesPalette(context: Context) {
    val frame = ContextCompat.getColor(context, R.color.dev_frame)
    val well = ContextCompat.getColor(context, R.color.dev_well)
    val track = ContextCompat.getColor(context, R.color.dev_track)
    val tone2 = ContextCompat.getColor(context, R.color.dev_tone2)
    val onWell = ContextCompat.getColor(context, R.color.dev_on_well)
    val onWellVar = ContextCompat.getColor(context, R.color.dev_on_well_var)
    val error = ContextCompat.getColor(context, R.color.dev_error)

    /** Anything at 15% or less shows in the error colour. */
    fun fillFor(level: Int) = if (level in 0..LOW) error else frame
    fun textFor(level: Int, normal: Int) = if (level in 0..LOW) error else normal

    companion object {
        const val LOW = 15
        const val FRAME_ALPHA = 87      // 34%
        const val WELL_ALPHA = 158      // 62%
        const val HAIRLINE_ALPHA = 46   // 18%
        const val WELL_LINE_ALPHA = 20  // 8%
        const val CARD_ALPHA = 26       // 10%
        const val PILL_ALPHA = 31       // 12%
    }
}

/** Ring gauges and bars, drawn as bitmaps because widgets can't draw arcs themselves. */
object DevicesDraw {
    fun ring(context: Context, sizeDp: Float, strokeDp: Float, level: Int, track: Int, fill: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (sizeDp * d).toInt().coerceAtLeast(1)
        val stroke = strokeDp * d
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        val r = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        p.color = track
        c.drawArc(r, 0f, 360f, false, p)
        if (level > 0) {
            p.color = fill
            c.drawArc(r, -90f, 360f * level.coerceIn(0, 100) / 100f, false, p)
        }
        return bmp
    }

    fun bar(context: Context, widthDp: Float, heightDp: Float, level: Int, track: Int, fill: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (widthDp * d).toInt().coerceAtLeast(1)
        val h = (heightDp * d).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rad = h / 2f
        p.color = track
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), rad, rad, p)
        val fw = w * level.coerceIn(0, 100) / 100f
        if (fw > 0f) {
            p.color = fill
            c.drawRoundRect(RectF(0f, 0f, fw.coerceAtLeast(h.toFloat()), h.toFloat()), rad, rad, p)
        }
        return bmp
    }

    fun iconFor(kind: DeviceKind): Int = when (kind) {
        DeviceKind.EARBUDS -> R.drawable.ic_dev_earbuds
        DeviceKind.HEADPHONES -> R.drawable.ic_dev_headphones
        DeviceKind.WATCH -> R.drawable.ic_dev_watch
        DeviceKind.KEYBOARD -> R.drawable.ic_dev_keyboard
        DeviceKind.MOUSE -> R.drawable.ic_dev_mouse
        DeviceKind.SPEAKER -> R.drawable.ic_dev_speaker
        DeviceKind.CAR -> R.drawable.ic_dev_car
        DeviceKind.OTHER -> R.drawable.ic_dev_bluetooth
    }

    fun labelFor(kind: DeviceKind): Int = when (kind) {
        DeviceKind.EARBUDS -> R.string.devices_kind_buds
        DeviceKind.HEADPHONES -> R.string.devices_kind_headphones
        DeviceKind.WATCH -> R.string.devices_kind_watch
        DeviceKind.KEYBOARD -> R.string.devices_kind_keyboard
        DeviceKind.MOUSE -> R.string.devices_kind_mouse
        DeviceKind.SPEAKER -> R.string.devices_kind_speaker
        DeviceKind.CAR -> R.string.devices_kind_car
        DeviceKind.OTHER -> R.string.devices_kind_other
    }
}
