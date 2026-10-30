package com.folderplus.ui.open

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folderplus.R
import com.folderplus.core.model.AppRef
import com.folderplus.core.model.Category
import com.folderplus.core.model.Folder
import com.folderplus.core.model.FolderItem
import com.folderplus.core.sort.AppSorter
import com.folderplus.ui.common.AppIconImage

/** Scrim + centred card with category chips and an app grid. */
@Composable
fun FolderPopup(
    folder: Folder,
    categories: List<Category>,
    items: List<FolderItem>,
    labelOf: (AppRef) -> String,
    onLaunch: (FolderItem) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedCategory by rememberSaveable { mutableStateOf(-1L) }
    val category = categories.firstOrNull { it.id == selectedCategory }
    val shown = remember(items, category, folder.sortMode) {
        val filtered = if (category == null) items else items.filter { it.categoryId == category.id }
        AppSorter.sort(filtered, category?.sortMode ?: folder.sortMode) { labelOf(it.app) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                // Swallow clicks so they don't reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(folder.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) }
                }
                if (categories.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                        FilterChip(selected = category == null, onClick = { selectedCategory = -1L }, label = { Text(stringResource(R.string.category_all)) }, modifier = Modifier.padding(end = 8.dp))
                        categories.forEach { c ->
                            FilterChip(selected = c.id == selectedCategory, onClick = { selectedCategory = c.id }, label = { Text(c.name) }, modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.folder_empty),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 76.dp),
                        modifier = Modifier.heightIn(max = 440.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        items(shown, key = { it.id }) { item ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickable { onLaunch(item) }
                                    .padding(vertical = 8.dp, horizontal = 2.dp),
                            ) {
                                AppIconImage(app = item.app, size = 48.dp)
                                Spacer(Modifier.padding(top = 4.dp))
                                Text(
                                    labelOf(item.app),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
