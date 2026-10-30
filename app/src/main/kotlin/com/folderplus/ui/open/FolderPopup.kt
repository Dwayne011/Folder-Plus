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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.folderplus.core.model.SortMode
import com.folderplus.core.sort.AppSorter
import com.folderplus.ui.common.AppIconImage
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** One swipeable page of the popup. */
private data class Tab(val title: String, val items: List<FolderItem>)

/** Scrim + centred card with category chips and a swipeable app grid per category. */
@Composable
fun FolderPopup(
    folder: Folder,
    categories: List<Category>,
    items: List<FolderItem>,
    labelOf: (AppRef) -> String,
    onLaunch: (FolderItem) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onShowWidget: (() -> Unit)? = null,
    frosting: Int = Folder.DEFAULT_FROSTING,
) {
    val allLabel = stringResource(R.string.category_all)
    val otherLabel = stringResource(R.string.category_other)
    val tabs = remember(folder, categories, items, allLabel, otherLabel) {
        buildTabs(folder, categories, items, labelOf, allLabel, otherLabel)
    }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // Tall enough for the fullest page (assuming ~4 columns), capped so the card stays a popup.
    val pagerHeight = remember(tabs) {
        val maxCount = tabs.maxOfOrNull { it.items.size } ?: 0
        val rows = ceil(maxCount / 4f).toInt().coerceAtLeast(1)
        (rows * 96 + 16).coerceIn(112, 440).dp
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha(frosting)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = popupContainerColor(frosting),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                // Swallow clicks so they don't reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                if (folder.name.isNotBlank()) {
                    Text(
                        folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // One compact row: category chips on the left, actions on the right. No empty header.
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (tabs.size > 1) {
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                            tabs.forEachIndexed { index, tab ->
                                FilterChip(
                                    selected = pagerState.currentPage == index,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    label = { Text(tab.title) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (onShowWidget != null) {
                        IconButton(onClick = onShowWidget) { Icon(Icons.Default.Widgets, contentDescription = stringResource(R.string.show_widget)) }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(pagerHeight),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val shown = tabs[page].items
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
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
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
}

/**
 * Pages: "All" (unless hidden and categories exist), one per category, and
 * "Other" for uncategorised apps when "All" is hidden. Each page is sorted by
 * the category's override or the folder's sort mode.
 */
private fun buildTabs(
    folder: Folder,
    categories: List<Category>,
    items: List<FolderItem>,
    labelOf: (AppRef) -> String,
    allLabel: String,
    otherLabel: String,
): List<Tab> {
    fun sorted(list: List<FolderItem>, mode: SortMode) = AppSorter.sort(list, mode) { labelOf(it.app) }
    if (categories.isEmpty()) return listOf(Tab(allLabel, sorted(items, folder.sortMode)))

    val tabs = mutableListOf<Tab>()
    if (folder.showAllCategory) tabs += Tab(allLabel, sorted(items, folder.sortMode))
    categories.forEach { category ->
        tabs += Tab(category.name, sorted(items.filter { it.categoryId == category.id }, category.sortMode ?: folder.sortMode))
    }
    if (!folder.showAllCategory) {
        val knownIds = categories.map { it.id }.toSet()
        val other = items.filter { it.categoryId == null || it.categoryId !in knownIds }
        if (other.isNotEmpty()) tabs += Tab(otherLabel, sorted(other, folder.sortMode))
    }
    return tabs
}
