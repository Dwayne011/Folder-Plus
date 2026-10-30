package com.folderplus.ui.open

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.folderplus.core.model.Folder

/** Card colour for a popup: opaque at frosting 0, well translucent at 100 (blur behind does the rest). */
@Composable
fun popupContainerColor(frosting: Int): Color {
    val f = frosting.coerceIn(0, Folder.MAX_FROSTING) / Folder.MAX_FROSTING.toFloat()
    return MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f - 0.6f * f)
}

/** The dim behind the card eases off as frosting (and blur) increase. */
fun scrimAlpha(frosting: Int): Float {
    val f = frosting.coerceIn(0, Folder.MAX_FROSTING) / Folder.MAX_FROSTING.toFloat()
    return 0.45f - 0.25f * f
}
