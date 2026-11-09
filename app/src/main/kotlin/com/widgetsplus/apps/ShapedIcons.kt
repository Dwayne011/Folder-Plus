package com.widgetsplus.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.widgetsplus.R
import com.widgetsplus.core.model.IconShape
import com.widgetsplus.core.model.IconShapes
import kotlin.math.roundToInt

/**
 * Draws app icons in a folder's chosen [IconShape] for its home-screen widgets.
 *
 * An adaptive icon (most apps) is redrawn from its two layers and cut to the shape, as a
 * launcher does with its own icon shape: the layers are 108 units with the visible icon in the
 * middle 72, so they're drawn half as big again as the icon and centred. Any other icon is
 * scaled into a white tile of that shape, the way the Pixel launcher shows older icons.
 */
object ShapedIcons {
    /** How much of the tile an older (non-adaptive) icon takes. */
    private const val LEGACY_SCALE = 0.72f

    fun render(drawable: Drawable, sizePx: Int, shape: IconShape): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outline = path(shape, sizePx.toFloat())
        canvas.clipPath(outline)
        if (drawable is AdaptiveIconDrawable) {
            val layer = (sizePx * 1.5f).roundToInt()
            val offset = (sizePx - layer) / 2
            listOfNotNull(drawable.background, drawable.foreground).forEach { part ->
                part.setBounds(offset, offset, offset + layer, offset + layer)
                part.draw(canvas)
            }
        } else {
            canvas.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            val inner = (sizePx * LEGACY_SCALE).roundToInt()
            val inset = (sizePx - inner) / 2
            drawable.setBounds(inset, inset, inset + inner, inset + inner)
            drawable.draw(canvas)
        }
        return bitmap
    }

    /** The shape's outline in a square [size] wide. */
    fun path(shape: IconShape, size: Float): Path = Path().apply {
        when (shape) {
            IconShape.CIRCLE, IconShape.DEFAULT -> addOval(RectF(0f, 0f, size, size), Path.Direction.CW)
            IconShape.ROUNDED_SQUARE -> {
                val r = size * IconShapes.ROUNDED_RADIUS
                addRoundRect(RectF(0f, 0f, size, size), r, r, Path.Direction.CW)
            }
            IconShape.SQUIRCLE -> {
                val points = IconShapes.squirclePoints()
                moveTo(points[0].first * size, points[0].second * size)
                points.drop(1).forEach { (x, y) -> lineTo(x * size, y * size) }
                close()
            }
        }
    }

    /** The "+N" tile's background in the shape (tinted by the layout). */
    fun moreBackground(shape: IconShape): Int = when (shape) {
        IconShape.DEFAULT, IconShape.CIRCLE -> R.drawable.folder_circle
        IconShape.SQUIRCLE -> R.drawable.folder_squircle
        IconShape.ROUNDED_SQUARE -> R.drawable.folder_rounded_square
    }
}
