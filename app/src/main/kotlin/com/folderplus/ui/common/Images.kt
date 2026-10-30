package com.folderplus.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.folderplus.core.model.AppRef
import com.folderplus.core.model.Folder
import com.folderplus.core.sort.AppSorter
import com.folderplus.graph
import com.folderplus.widget.IconComposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An installed app's icon, loaded off the main thread and cached. */
@Composable
fun AppIconImage(app: AppRef, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = app) {
        val loaded = withContext(Dispatchers.IO) { context.graph.installedApps.icon(app) }
        value = loaded
    }
    val current = bitmap
    if (current != null) {
        Image(bitmap = current.asImageBitmap(), contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size))
    }
}

/** The folder's composed icon, exactly as the widget draws it. */
@Composable
fun FolderIconImage(folder: Folder, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = folder, key2 = sizePx) {
        val composed = withContext(Dispatchers.IO) {
            val graph = context.graph
            graph.installedApps.ensureLoaded()
            val items = AppSorter.sort(graph.repository.getItems(folder.id), folder.sortMode) { graph.installedApps.label(it.app) }
            IconComposer.folderIcon(context, folder, items, graph.installedApps, sizePx.coerceAtLeast(1))
        }
        value = composed
    }
    val current = bitmap
    if (current != null) {
        Image(bitmap = current.asImageBitmap(), contentDescription = null, modifier = modifier.size(size))
    } else {
        Icon(Icons.Filled.Folder, contentDescription = null, modifier = modifier.size(size), tint = MaterialTheme.colorScheme.primary)
    }
}
