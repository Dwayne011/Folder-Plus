package com.widgetsplus.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import com.widgetsplus.R
import com.widgetsplus.apps.InstalledApps
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderIconMode
import com.widgetsplus.core.model.FolderItem

/**
 * Builds the bitmap shown as the folder's icon in the app (folder settings, the folder list and
 * the folder picker): the 1×1 folder widget, drawn to scale. The agenda's see-through plate with
 * its hairline, the pale panel 8dp inside, and on it the 2×2 mini mosaic (three apps and "+N"
 * when there are more than four), or one icon for the "Default app" and "Folder glyph" modes.
 */
object IconComposer {

    /** The 1×1 widget's width in dp; every measurement below is scaled from it. */
    private const val REF_DP = 67f
    private const val PLATE_RADIUS = 22f
    private const val BORDER = 8f
    private const val PANEL_RADIUS = 14f
    private const val PANEL_PADDING = 6f
    private const val CELL_PADDING = 2f
    private const val SINGLE_PADDING = 10f
    private const val PLATE_ALPHA = 0.58f
    private const val HAIRLINE_ALPHA = 0.16f

    fun folderIcon(
        context: Context,
        folder: Folder,
        sortedItems: List<FolderItem>,
        apps: InstalledApps,
        sizePx: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val k = sizePx / REF_DP
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Plate (see-through) and its hairline.
        val plate = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        paint.color = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.agenda_plate), (PLATE_ALPHA * 255).toInt())
        canvas.drawRoundRect(plate, PLATE_RADIUS * k, PLATE_RADIUS * k, paint)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, k)
            color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), (HAIRLINE_ALPHA * 255).toInt())
        }
        val half = line.strokeWidth / 2
        canvas.drawRoundRect(RectF(half, half, sizePx - half, sizePx - half), PLATE_RADIUS * k - half, PLATE_RADIUS * k - half, line)

        // Pale panel.
        val panel = RectF(BORDER * k, BORDER * k, sizePx - BORDER * k, sizePx - BORDER * k)
        paint.color = ContextCompat.getColor(context, R.color.agenda_group)
        canvas.drawRoundRect(panel, PANEL_RADIUS * k, PANEL_RADIUS * k, paint)

        val single: Bitmap? = when {
            folder.iconMode == FolderIconMode.DEFAULT_APP -> folder.defaultApp?.let { apps.icon(it, sizePx) } ?: glyph(context, sizePx)
            folder.iconMode == FolderIconMode.GLYPH -> glyph(context, sizePx)
            else -> null
        }
        val icons = if (single == null) {
            sortedItems.asSequence().filter { apps.isInstalled(it.app) }.mapNotNull { apps.icon(it.app, sizePx / 2) }.take(4).toList()
        } else {
            emptyList()
        }
        if (single != null || icons.isEmpty()) {
            val icon = single ?: glyph(context, sizePx)
            val inset = SINGLE_PADDING * k
            draw(canvas, icon, RectF(panel.left + inset, panel.top + inset, panel.right - inset, panel.bottom - inset), paint)
            return bitmap
        }

        // 2×2 mini mosaic, as the widget lays it out: equal quarters, each icon 2dp inside its quarter.
        val count = sortedItems.count { apps.isInstalled(it.app) }
        val shown = if (count > 4) 3 else icons.size
        val inner = RectF(panel.left + PANEL_PADDING * k, panel.top + PANEL_PADDING * k, panel.right - PANEL_PADDING * k, panel.bottom - PANEL_PADDING * k)
        val cell = inner.width() / 2
        for (i in 0 until 4) {
            val left = inner.left + (i % 2) * cell
            val top = inner.top + (i / 2) * cell
            val box = RectF(left + CELL_PADDING * k, top + CELL_PADDING * k, left + cell - CELL_PADDING * k, top + cell - CELL_PADDING * k)
            when {
                i < shown -> draw(canvas, icons[i], box, paint)
                i == shown && count > 4 -> more(context, canvas, box, count - shown)
            }
        }
        return bitmap
    }

    private fun draw(canvas: Canvas, icon: Bitmap, box: RectF, paint: Paint) {
        canvas.drawBitmap(icon, Rect(0, 0, icon.width, icon.height), box, paint)
    }

    /** "+N" on the agenda's card colour, like the widget's tile. */
    private fun more(context: Context, canvas: Canvas, box: RectF, n: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.agenda_card) }
        canvas.drawOval(box, fill)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.agenda_on_card)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("google-sans", Typeface.BOLD)
            textSize = box.height() * 0.42f
        }
        val label = "+$n"
        // Shrink long counts to fit the circle.
        val maxWidth = box.width() * 0.84f
        val width = text.measureText(label)
        if (width > maxWidth) text.textSize *= maxWidth / width
        val y = box.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(label, box.centerX(), y, text)
    }

    fun glyph(context: Context, sizePx: Int): Bitmap =
        ContextCompat.getDrawable(context, R.drawable.ic_folder)!!.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
}
