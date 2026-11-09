package com.widgetsplus.ui.frost

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pixel Launcher Plus's stroke icons on a 24-unit grid (2 dp lines, round caps and joins), plus
 * the few more these pages need, drawn the same way.
 */
object FrostIcons {
    // The launcher's own.
    const val Search = "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M20 20l-4-4"
    const val More = "M5 12h.01 M12 12h.01 M19 12h.01"
    const val Calendar = "M5 6h14v14H5z M5 10h14 M9 4v4 M15 4v4"
    const val ChevronRight = "M9 6l6 6-6 6"
    const val ChevronLeft = "M15 6l-6 6 6 6"
    const val Close = "M6 6l12 12 M18 6L6 18"
    const val Image = "M4 5h16v14H4z M4 16l5-5 4 4 3-3 4 4 M15 9h.01"
    const val Lines = "M4 7h16 M4 12h12 M4 17h8"
    const val Drop = "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z"
    const val Grid = "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z"
    const val Shapes = "M12 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M4 14h7v7H4z M17.5 14l3.5 7h-7z"
    const val Apps = "M4 5h4v4H4z M10 5h4v4h-4z M16 5h4v4h-4z M4 11h4v4H4z M10 11h4v4h-4z M16 11h4v4h-4z M4 17h4v2H4z"
    const val Sort = "M7 4v16 M3 8l4-4 4 4 M17 20V4 M13 16l4 4 4-4"
    const val Folder = "M3 6h6l2 2h10v11H3z"
    const val Label = "M4 7h16 M4 12h16 M4 17h10"
    const val Down = "M12 4v14 M6 12l6 6 6-6"
    const val Phone = "M8 3h8v18H8z M11 18h2"
    const val Info = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v5 M12 8h.01"
    const val Plus = "M12 5v14 M5 12h14"
    const val Share = "M12 15V3 M7 8l5-5 5 5 M5 13v7h14v-7"
    const val Lock = "M6 11h12v9H6z M8 11V8a4 4 0 0 1 8 0v3"
    const val Small = "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z"
    const val Large = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7a5 5 0 1 0 0 10a5 5 0 1 0 0-10z"

    // Drawn for these pages in the same style.
    const val MoreVertical = "M12 5h.01 M12 12h.01 M12 19h.01"
    const val Edit = "M4 20h4L19 9l-4-4L4 16z"
    const val Trash = "M5 7h14 M9 7V4h6v3 M7 7l1 13h8l1-13"
    const val Check = "M5 12l5 5 9-10"
    const val Checklist = "M4 6l1.5 1.5L8 5 M11 6h9 M4 12l1.5 1.5L8 11 M11 12h9 M4 18l1.5 1.5L8 17 M11 18h9"
    const val DragHandle = "M5 9h14 M5 15h14"
    const val Star = "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z"
    const val MoveTo = "M3 6h6l2 2h10v11H3z M10 13.5h6 M13.5 11l2.5 2.5-2.5 2.5"
    const val Shortcut = "M14 4h6v6 M20 4l-9 9 M18 14v6H4V6h6"
    const val DoubleDown = "M8 4v12 M16 4v12 M4.5 12.5L8 16l3.5-3.5 M12.5 12.5L16 16l3.5-3.5"
    const val Widget = "M4 4h7v7H4z M4 13h7v7H4z M13 13h7v7h-7z M16.5 3.5l4 4-4 4-4-4z"
    const val AlignCentre = "M4 6h16 M7 12h10 M4 18h16"
    const val Inbox = "M4 13l2-8h12l2 8v6H4z M4 13h5l1 2h4l1-2h5"
    const val Bluetooth = "M7 7l10 10-5 4V3l5 4L7 17"
    const val Earbuds = "M7 4a3 3 0 0 0-3 3v4a3 3 0 0 0 6 0V7a3 3 0 0 0-3-3z M17 4a3 3 0 0 0-3 3v4a3 3 0 0 0 6 0V7a3 3 0 0 0-3-3z M7 14v6 M17 14v6"
    const val Headphones = "M4 15v-3a8 8 0 0 1 16 0v3 M4 15h3v5H4z M17 15h3v5h-3z"
    const val Watch = "M8 7h8v10H8z M9 7l1-4h4l1 4 M9 17l1 4h4l1-4"
    const val Keyboard = "M3 7h18v10H3z M7 10.5h.01 M11 10.5h.01 M15 10.5h.01 M7 13.5h10"
    const val Mouse = "M12 3a6 6 0 0 0-6 6v6a6 6 0 0 0 12 0V9a6 6 0 0 0-6-6z M12 7v3"
    const val Gamepad = "M7 8h10a4 4 0 0 1 4 4v2a3 3 0 0 1-5.5 1.6L14.5 14h-5l-1 1.6A3 3 0 0 1 3 14v-2a4 4 0 0 1 4-4z M8 10v4 M6 12h4 M15.5 11h.01 M17.5 13h.01"
    const val Speaker = "M7 3h10v18H7z M12 11a3 3 0 1 0 0 6a3 3 0 1 0 0-6z M12 7h.01"
    const val Car = "M5 17h14v-5l-2-5H7l-2 5z M5 12h14 M7 17v2 M17 17v2"
}

/** Draws one of [FrostIcons] at [size] in [color]; [filled] fills the shape too. */
@Composable
fun FrostIcon(
    path: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Float = 2f,
    filled: Boolean = false,
) {
    val parsed = remember(path) { PathParser().parsePathString(path).toPath() }
    // Dots ("h.01") need a wider stroke to show as dots.
    val width = if (path == FrostIcons.More || path == FrostIcons.MoreVertical) strokeWidth * 1.5f else strokeWidth
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            if (filled) drawPath(parsed, color, style = Fill)
            drawPath(parsed, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
