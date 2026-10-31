package com.widgetsplus.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import com.widgetsplus.R
import com.widgetsplus.apps.InstalledApps
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderIconMode
import com.widgetsplus.core.model.FolderItem

/** Builds the bitmap shown as the folder's icon on widgets and in the app. */
object IconComposer {

    fun folderIcon(
        context: Context,
        folder: Folder,
        sortedItems: List<FolderItem>,
        apps: InstalledApps,
        sizePx: Int,
    ): Bitmap = when (folder.iconMode) {
        FolderIconMode.DEFAULT_APP ->
            folder.defaultApp?.let { apps.icon(it, sizePx) } ?: glyph(context, sizePx)
        FolderIconMode.GLYPH -> glyph(context, sizePx)
        FolderIconMode.MOSAIC -> {
            val icons = sortedItems.asSequence().mapNotNull { apps.icon(it.app, sizePx / 2) }.take(4).toList()
            if (icons.isEmpty()) glyph(context, sizePx) else mosaic(context, icons, sizePx)
        }
    }

    fun glyph(context: Context, sizePx: Int): Bitmap =
        ContextCompat.getDrawable(context, R.drawable.ic_folder)!!.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)

    /** Launcher-style folder: a translucent disc holding up to four icons in a 2x2 grid. */
    private fun mosaic(context: Context, icons: List<Bitmap>, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.widget_folder_disc), 0xCC)
        }
        val half = sizePx / 2f
        canvas.drawCircle(half, half, half, disc)

        val pad = sizePx * 0.14f
        val gap = sizePx * 0.06f
        val cell = (sizePx - 2 * pad - gap) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        icons.forEachIndexed { index, icon ->
            val col = index % 2
            val row = index / 2
            val left = pad + col * (cell + gap)
            val top = pad + row * (cell + gap)
            val dst = RectF(left, top, left + cell, top + cell)
            canvas.drawBitmap(icon, Rect(0, 0, icon.width, icon.height), dst, paint)
        }
        return bitmap
    }
}
