package com.widgetsplus.ui.editor

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgetsplus.R
import com.widgetsplus.core.model.Category
import com.widgetsplus.core.model.FolderTab
import com.widgetsplus.core.model.FolderTabs
import com.widgetsplus.core.model.SortMode
import com.widgetsplus.graph
import com.widgetsplus.ui.common.ConfirmDialog
import com.widgetsplus.ui.common.Option
import com.widgetsplus.ui.common.OptionPickerDialog
import com.widgetsplus.ui.common.TextInputDialog
import com.widgetsplus.ui.common.groupItemShape
import com.widgetsplus.ui.common.label

/**
 * The open folder's tabs in their order: "All" and every category. Drag a handle (or use a row's
 * Move up / Move down) to rearrange them; "All" can sit anywhere and has its own on/off switch.
 * While "All" is hidden, apps without a category get an "Other" tab, always last.
 */
@Composable
fun CategoriesScreen(folderId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: FolderEditorViewModel = viewModel(key = "editor-$folderId") { FolderEditorViewModel(context.graph, folderId) }
    val folder by vm.folder.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val current = folder ?: return

    val saved = remember(categories, current.allTabPosition) { FolderTabs.ordered(categories, current.allTabPosition) }
    var working by remember(saved) { mutableStateOf(saved) }
    var dragging by remember { mutableStateOf<FolderTab?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }
    val gapPx = with(LocalDensity.current) { 2.dp.toPx() }
    // "new", or an action and a category id: "rename:12", "sort:12", "delete:12"
    var dialog by rememberSaveable { mutableStateOf("") }

    fun move(from: Int, to: Int) {
        if (from == to || to !in working.indices) return
        working = working.toMutableList().apply { add(to, removeAt(from)) }
        vm.saveTabOrder(working)
    }

    EditorScaffold(title = stringResource(R.string.categories_title), onBack = onBack) {
        item {
            Text(
                stringResource(R.string.categories_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 16.dp),
            )
        }
        item {
            val rows = working.size + if (current.showAllCategory) 0 else 1
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                working.forEachIndexed { index, tab ->
                    val isDragging = tab == dragging
                    val handle = Modifier.pointerInput(tab) {
                        detectDragGestures(
                            onDragStart = { dragging = tab; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val step = rowHeight + gapPx
                                val from = working.indexOf(tab)
                                if (step > 0f && dragOffset > step / 2 && from < working.lastIndex) {
                                    working = working.toMutableList().apply { add(from + 1, removeAt(from)) }
                                    dragOffset -= step
                                } else if (step > 0f && dragOffset < -step / 2 && from > 0) {
                                    working = working.toMutableList().apply { add(from - 1, removeAt(from)) }
                                    dragOffset += step
                                }
                            },
                            onDragEnd = {
                                dragging = null
                                dragOffset = 0f
                                if (working != saved) vm.saveTabOrder(working)
                            },
                            onDragCancel = {
                                dragging = null
                                dragOffset = 0f
                                working = saved
                            },
                        )
                    }
                    Surface(
                        shape = groupItemShape(index, rows),
                        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { rowHeight = it.height }
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                shadowElevation = if (isDragging) 8.dp.toPx() else 0f
                            },
                    ) {
                        when (tab) {
                            FolderTab.All -> AllRow(
                                shown = current.showAllCategory,
                                handle = handle,
                                onShownChange = vm::setShowAllCategory,
                                canMoveUp = index > 0,
                                canMoveDown = index < working.lastIndex,
                                onMove = { move(index, index + it) },
                            )
                            is FolderTab.OfCategory -> CategoryRow(
                                category = tab.category,
                                appCount = items.count { it.categoryId == tab.category.id },
                                handle = handle,
                                canMoveUp = index > 0,
                                canMoveDown = index < working.lastIndex,
                                onMove = { move(index, index + it) },
                                onAction = { action -> dialog = "$action:${tab.category.id}" },
                            )
                        }
                    }
                }
                if (!current.showAllCategory) {
                    Surface(shape = groupItemShape(rows - 1, rows), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.category_other)) },
                            supportingContent = { Text(stringResource(R.string.category_other_desc)) },
                            leadingContent = { Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inbox, contentDescription = null) } },
                            colors = ListItemDefaults.colors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                headlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { dialog = "new" }, modifier = Modifier.padding(start = 16.dp, top = 16.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.new_category), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    val action = dialog.substringBefore(':')
    val target = dialog.substringAfter(':', "").toLongOrNull()?.let { id -> categories.firstOrNull { it.id == id } }
    when {
        action == "new" -> TextInputDialog(
            title = stringResource(R.string.new_category),
            label = stringResource(R.string.category_name),
            confirmText = stringResource(R.string.create),
            onConfirm = vm::addCategory,
            onDismiss = { dialog = "" },
        )
        action == "rename" && target != null -> TextInputDialog(
            title = stringResource(R.string.rename),
            label = stringResource(R.string.category_name),
            initial = target.name,
            onConfirm = { vm.renameCategory(target, it) },
            onDismiss = { dialog = "" },
        )
        action == "sort" && target != null -> OptionPickerDialog(
            title = target.name + " · " + stringResource(R.string.sort_mode),
            options = listOf(Option<SortMode?>(null, stringResource(R.string.sort_inherit, current.sortMode.label()))) +
                SortMode.entries.map { Option<SortMode?>(it, it.label()) },
            selected = target.sortMode,
            onSelect = { vm.setCategorySort(target, it) },
            onDismiss = { dialog = "" },
        )
        action == "delete" && target != null -> ConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_category_confirm, target.name),
            confirmText = stringResource(R.string.delete),
            onConfirm = { vm.deleteCategory(target) },
            onDismiss = { dialog = "" },
        )
    }
}

@Composable
private fun DragHandle(modifier: Modifier) {
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.drag_to_reorder), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoveItems(canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit, close: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, enabled = canMoveUp, onClick = { close(); onMove(-1) })
    DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, enabled = canMoveDown, onClick = { close(); onMove(1) })
}

@Composable
private fun AllRow(
    shown: Boolean,
    handle: Modifier,
    onShownChange: (Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.category_all)) },
        supportingContent = { Text(stringResource(if (shown) R.string.category_all_desc else R.string.category_all_hidden)) },
        leadingContent = { DragHandle(handle) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = shown, onCheckedChange = onShownChange)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.category_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MoveItems(canMoveUp, canMoveDown, onMove) { menu = false }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun CategoryRow(
    category: Category,
    appCount: Int,
    handle: Modifier,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onAction: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(category.name) },
        supportingContent = { Text(pluralStringResource(R.plurals.category_app_count, appCount, appCount)) },
        leadingContent = { DragHandle(handle) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.category_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onAction("rename") })
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_mode)) }, onClick = { menu = false; onAction("sort") })
                    MoveItems(canMoveUp, canMoveDown, onMove) { menu = false }
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onAction("delete") })
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
