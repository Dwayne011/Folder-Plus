package com.widgetsplus.ui.open

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.widgetsplus.R
import com.widgetsplus.core.model.Folder

/**
 * Card colour for a popup: the agenda widget's plate, opaque at frosting 0 and well see-through
 * at 100 (blur behind does the rest). A resource colour, so it follows the phone's theme.
 */
@Composable
fun popupContainerColor(frosting: Int): Color {
    val f = frosting.coerceIn(0, Folder.MAX_FROSTING) / Folder.MAX_FROSTING.toFloat()
    return colorResource(R.color.agenda_plate).copy(alpha = 1f - 0.6f * f)
}

/** The dim behind the card eases off as frosting (and blur) increase. */
fun scrimAlpha(frosting: Int): Float {
    val f = frosting.coerceIn(0, Folder.MAX_FROSTING) / Folder.MAX_FROSTING.toFloat()
    return 0.45f - 0.25f * f
}
