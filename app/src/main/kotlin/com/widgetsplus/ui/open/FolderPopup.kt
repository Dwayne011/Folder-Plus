package com.widgetsplus.ui.open

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.widgetsplus.R
import com.widgetsplus.core.model.AppRef
import com.widgetsplus.core.model.Category
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.FolderTab
import com.widgetsplus.core.model.FolderTabs
import com.widgetsplus.core.model.SortMode
import com.widgetsplus.core.sort.AppSorter
import com.widgetsplus.ui.common.AppIconImage
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** One swipeable page of the popup. */
private data class Tab(val title: String, val items: List<FolderItem>)

/**
 * Scrim + centred card in the agenda widget's style: its see-through plate (the Frosting setting
 * sets how see-through) with a hairline, the folder's name top left when it has one, the tabs as
 * the agenda's pills, and a swipeable app grid per tab on the agenda's pale panel.
 */
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
    val onPlate = colorResource(R.color.agenda_on_plate)
    val onCard = colorResource(R.color.agenda_on_card)
    val badge = colorResource(R.color.agenda_badge)

    // Tall enough for the fullest page (4 columns), capped so the card stays a popup.
    val pagerHeight = remember(tabs) {
        val maxCount = tabs.maxOfOrNull { it.items.size } ?: 0
        val rows = ceil(maxCount / 4f).toInt().coerceAtLeast(1)
        (rows * 92 + 16).coerceIn(108, 440).dp
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
            contentColor = onPlate,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                // Swallow clicks so they don't reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Column(Modifier.padding(8.dp)) {
                if (folder.name.isNotBlank()) {
                    Text(
                        folder.name,
                        color = onPlate,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                // One compact row: the tabs as pills on the left, actions on the right.
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (tabs.size > 1) {
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tabs.forEachIndexed { index, tab ->
                                val selected = pagerState.currentPage == index
                                Box(
                                    Modifier
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .then(
                                            if (selected) Modifier.background(badge)
                                            else Modifier.border(1.dp, onPlate.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                                        )
                                        .selectable(selected = selected, role = Role.Tab, onClick = { scope.launch { pagerState.animateScrollToPage(index) } })
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(tab.title, color = if (selected) onCard else onPlate, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (onShowWidget != null) {
                        IconButton(onClick = onShowWidget) { Icon(Icons.Default.Widgets, contentDescription = stringResource(R.string.show_widget), tint = onPlate) }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = onPlate) }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colorResource(R.color.agenda_group)),
                ) {
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
                                color = colorResource(R.color.agenda_on_card_var),
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                            ) {
                                items(shown, key = { it.id }) { item ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onLaunch(item) }
                                            .padding(vertical = 8.dp, horizontal = 2.dp),
                                    ) {
                                        AppIconImage(app = item.app, size = 48.dp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            labelOf(item.app),
                                            color = onCard,
                                            fontSize = 12.sp,
                                            lineHeight = 15.sp,
                                            fontWeight = FontWeight.Medium,
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
}

/**
 * Pages in the order set on the Categories page: "All" (unless hidden) wherever it was placed,
 * one per category, and "Other" for uncategorised apps (last) when "All" is hidden. Each page is
 * sorted by the category's override or the folder's sort mode.
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
    FolderTabs.ordered(categories, folder.allTabPosition).forEach { tab ->
        when (tab) {
            FolderTab.All -> if (folder.showAllCategory) tabs += Tab(allLabel, sorted(items, folder.sortMode))
            is FolderTab.OfCategory -> tabs += Tab(
                tab.category.name,
                sorted(items.filter { it.categoryId == tab.category.id }, tab.category.sortMode ?: folder.sortMode),
            )
        }
    }
    if (!folder.showAllCategory) {
        val knownIds = categories.map { it.id }.toSet()
        val other = items.filter { it.categoryId == null || it.categoryId !in knownIds }
        if (other.isNotEmpty()) tabs += Tab(otherLabel, sorted(other, folder.sortMode))
    }
    return tabs
}
