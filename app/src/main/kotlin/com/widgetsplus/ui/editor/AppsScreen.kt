package com.widgetsplus.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgetsplus.R
import com.widgetsplus.core.model.Category
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.SortMode
import com.widgetsplus.core.sort.AppSorter
import com.widgetsplus.graph
import com.widgetsplus.ui.common.AppIconImage
import com.widgetsplus.ui.common.ConfirmDialog
import com.widgetsplus.ui.common.Option
import com.widgetsplus.ui.common.OptionPickerDialog
import com.widgetsplus.ui.common.TextInputDialog
import com.widgetsplus.ui.common.label

/** Lists the apps in one folder: filter by category, assign categories, remove, and drag to reorder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    folderId: Long,
    onBack: () -> Unit,
    onAddApps: (categoryId: Long?) -> Unit,
    onEditCategories: () -> Unit,
    onAddShortcut: (categoryId: Long?) -> Unit,
) {
    val context = LocalContext.current
    val vm: FolderEditorViewModel = viewModel(key = "editor-$folderId") { FolderEditorViewModel(context.graph, folderId) }
    val folder by vm.folder.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val sortedItems by vm.items.collectAsStateWithLifecycle()

    var selectedCategory by rememberSaveable { mutableStateOf(-1L) } // -1 = All
    var categoryDialogFor by rememberSaveable { mutableStateOf(-1L) }

    // Selection mode: tap rows to pick several apps, then move or remove them together.
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var bulkCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var bulkRemoveDialog by rememberSaveable { mutableStateOf(false) }

    // Category management lives here, next to the chips: "new", "rename", "sort", "delete".
    var categoryDialog by rememberSaveable { mutableStateOf("") }
    var categoryMenu by remember { mutableStateOf(false) }
    fun exitSelection() {
        selecting = false
        selectedIds = emptySet()
    }

    val current = folder ?: return
    val manual = current.sortMode == SortMode.MANUAL
    val filtering = selectedCategory > 0
    val activeCategory = categories.firstOrNull { it.id == selectedCategory }
    if (filtering && activeCategory == null) selectedCategory = -1L
    val visible = if (filtering) sortedItems.filter { it.categoryId == selectedCategory } else sortedItems

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection)) }
                    },
                    actions = {
                        IconButton(enabled = selectedIds.isNotEmpty() && categories.isNotEmpty(), onClick = { bulkCategoryDialog = true }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = stringResource(R.string.move_to_category))
                        }
                        IconButton(enabled = selectedIds.isNotEmpty(), onClick = { bulkRemoveDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_from_folder))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.apps_in_folder)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                    },
                    actions = {
                        if (sortedItems.isNotEmpty()) {
                            IconButton(onClick = { selecting = true }) { Icon(Icons.Default.Checklist, contentDescription = stringResource(R.string.select)) }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                // Shortcuts have their own list (there can be many), so their own button.
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = { onAddShortcut(selectedCategory.takeIf { it > 0 }) },
                        icon = { Icon(Icons.Default.Shortcut, contentDescription = null) },
                        text = { Text(stringResource(R.string.add_shortcut)) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    ExtendedFloatingActionButton(
                        onClick = { onAddApps(selectedCategory.takeIf { it > 0 }) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = {
                            Text(
                                if (activeCategory != null) stringResource(R.string.add_to_category, activeCategory.name)
                                else stringResource(R.string.add_apps),
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    if (categories.isNotEmpty()) {
                        FilterChip(
                            selected = !filtering,
                            onClick = { selectedCategory = -1L },
                            label = { Text(stringResource(R.string.category_all)) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category.id,
                            onClick = { selectedCategory = category.id },
                            label = { Text(category.name) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    AssistChip(
                        onClick = { categoryDialog = "new" },
                        label = { Text(stringResource(R.string.new_category)) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                IconButton(onClick = onEditCategories) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_categories)) }
                if (activeCategory != null) {
                    IconButton(onClick = { categoryMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.category_options)) }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { categoryMenu = false; categoryDialog = "rename" })
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort_mode)) }, onClick = { categoryMenu = false; categoryDialog = "sort" })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { categoryMenu = false; categoryDialog = "delete" })
                    }
                }
            }
            if (manual) {
                Text(
                    stringResource(if (filtering) R.string.manual_sort_filtered_hint else R.string.manual_sort_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.folder_empty), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                ReorderableAppList(
                    items = visible,
                    categories = categories,
                    labelOf = vm::label,
                    isDefault = { it.app == current.defaultApp },
                    dragEnabled = manual && !filtering && !selecting,
                    selecting = selecting,
                    selectedIds = selectedIds,
                    onToggleSelected = { item ->
                        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                    },
                    onOrderCommitted = vm::saveManualOrder,
                    onSetDefault = { vm.setDefaultApp(it.app) },
                    onRemove = vm::removeItem,
                    onChangeCategory = { categoryDialogFor = it.id },
                )
            }
        }
    }

    when (categoryDialog) {
        "new" -> TextInputDialog(
            title = stringResource(R.string.new_category),
            label = stringResource(R.string.category_name),
            confirmText = stringResource(R.string.create),
            onConfirm = vm::addCategory,
            onDismiss = { categoryDialog = "" },
        )
        "rename" -> activeCategory?.let { category ->
            TextInputDialog(
                title = stringResource(R.string.rename),
                label = stringResource(R.string.category_name),
                initial = category.name,
                onConfirm = { vm.renameCategory(category, it) },
                onDismiss = { categoryDialog = "" },
            )
        }
        "sort" -> activeCategory?.let { category ->
            OptionPickerDialog(
                title = category.name + " · " + stringResource(R.string.sort_mode),
                options = listOf(Option<SortMode?>(null, stringResource(R.string.sort_inherit, current.sortMode.label()))) +
                    SortMode.entries.map { Option<SortMode?>(it, it.label()) },
                selected = category.sortMode,
                onSelect = { vm.setCategorySort(category, it) },
                onDismiss = { categoryDialog = "" },
            )
        }
        "delete" -> activeCategory?.let { category ->
            ConfirmDialog(
                title = stringResource(R.string.delete),
                text = stringResource(R.string.delete_category_confirm, category.name),
                confirmText = stringResource(R.string.delete),
                onConfirm = { vm.deleteCategory(category); selectedCategory = -1L },
                onDismiss = { categoryDialog = "" },
            )
        }
    }

    if (bulkCategoryDialog) {
        OptionPickerDialog(
            title = stringResource(R.string.move_to_category),
            options = listOf(Option<Long?>(null, stringResource(R.string.category_none))) +
                categories.map { Option<Long?>(it.id, it.name) },
            selected = null,
            onSelect = { target ->
                vm.setItemsCategory(selectedIds, target)
                exitSelection()
            },
            onDismiss = { bulkCategoryDialog = false },
        )
    }
    if (bulkRemoveDialog) {
        ConfirmDialog(
            title = stringResource(R.string.remove_from_folder),
            text = stringResource(R.string.remove_selected_confirm, selectedIds.size),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                vm.removeItems(selectedIds)
                exitSelection()
            },
            onDismiss = { bulkRemoveDialog = false },
        )
    }

    if (categoryDialogFor > 0) {
        val item = sortedItems.firstOrNull { it.id == categoryDialogFor }
        if (item == null) {
            categoryDialogFor = -1L
        } else {
            OptionPickerDialog(
                title = stringResource(R.string.move_to_category),
                options = listOf(Option<Long?>(null, stringResource(R.string.category_none))) +
                    categories.map { Option<Long?>(it.id, it.name) },
                selected = item.categoryId,
                onSelect = { vm.setItemCategory(item, it) },
                onDismiss = { categoryDialogFor = -1L },
            )
        }
    }
}

/**
 * A LazyColumn whose rows can be long-pressed and dragged when [dragEnabled].
 * The order is kept locally while dragging and committed once on release.
 */
@Composable
private fun ReorderableAppList(
    items: List<FolderItem>,
    categories: List<Category>,
    labelOf: (com.widgetsplus.core.model.AppRef) -> String,
    isDefault: (FolderItem) -> Boolean,
    dragEnabled: Boolean,
    selecting: Boolean,
    selectedIds: Set<Long>,
    onToggleSelected: (FolderItem) -> Unit,
    onOrderCommitted: (List<FolderItem>) -> Unit,
    onSetDefault: (FolderItem) -> Unit,
    onRemove: (FolderItem) -> Unit,
    onChangeCategory: (FolderItem) -> Unit,
) {
    val listState = rememberLazyListState()
    var working by remember(items) { mutableStateOf(items) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val dragModifier = if (dragEnabled) {
        Modifier.pointerInput(items) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    draggingIndex = listState.indexAt(offset.y) ?: -1
                    dragOffset = 0f
                },
                onDrag = { change, amount ->
                    change.consume()
                    val from = draggingIndex
                    if (from < 0) return@detectDragGesturesAfterLongPress
                    dragOffset += amount.y
                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from } ?: return@detectDragGesturesAfterLongPress
                    val centre = info.offset + info.size / 2f + dragOffset
                    val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index != from && centre >= it.offset && centre < it.offset + it.size }
                    if (target != null) {
                        working = AppSorter.move(working, from, target.index)
                        dragOffset += info.offset - target.offset
                        draggingIndex = target.index
                    }
                },
                onDragEnd = {
                    if (draggingIndex >= 0 && working != items) onOrderCommitted(working)
                    draggingIndex = -1
                    dragOffset = 0f
                },
                onDragCancel = {
                    draggingIndex = -1
                    dragOffset = 0f
                    working = items
                },
            )
        }
    } else {
        Modifier
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(dragModifier)) {
        itemsIndexed(working, key = { _, item -> item.id }) { index, item ->
            val dragging = index == draggingIndex
            AppRow(
                item = item,
                label = labelOf(item.app),
                categoryName = categories.firstOrNull { it.id == item.categoryId }?.name,
                isDefault = isDefault(item),
                showHandle = dragEnabled,
                selecting = selecting,
                selected = item.id in selectedIds,
                onToggleSelected = { onToggleSelected(item) },
                onSetDefault = { onSetDefault(item) },
                onRemove = { onRemove(item) },
                onChangeCategory = { onChangeCategory(item) },
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        shadowElevation = if (dragging) 8.dp.toPx() else 0f
                    },
            )
        }
    }
}

private fun LazyListState.indexAt(y: Float): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }?.index

@Composable
private fun AppRow(
    item: FolderItem,
    label: String,
    categoryName: String?,
    isDefault: Boolean,
    showHandle: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit,
    onChangeCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        // Shortcuts say so, beside their category.
        supportingContent = listOfNotNull(
            if (item.app.isShortcut) stringResource(R.string.shortcut_tag) else null,
            categoryName,
        ).takeIf { it.isNotEmpty() }?.let { parts -> { Text(parts.joinToString(" · ")) } },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
                    Spacer(Modifier.width(4.dp))
                }
                if (showHandle) {
                    Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                }
                AppIconImage(app = item.app, size = 40.dp)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDefault) Icon(Icons.Default.Star, contentDescription = stringResource(R.string.default_app), tint = MaterialTheme.colorScheme.primary)
                if (!selecting) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.set_as_default)) }, onClick = { menu = false; onSetDefault() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_to_category)) }, onClick = { menu = false; onChangeCategory() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_folder)) }, onClick = { menu = false; onRemove() })
                    }
                }
            }
        },
        modifier = if (selecting) modifier.fillMaxWidth().clickable(onClick = onToggleSelected) else modifier.fillMaxWidth(),
    )
}
