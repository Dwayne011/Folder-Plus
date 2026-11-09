package com.widgetsplus.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgetsplus.R
import com.widgetsplus.core.model.Category
import com.widgetsplus.core.model.FolderTab
import com.widgetsplus.core.model.FolderTabs
import com.widgetsplus.graph
import com.widgetsplus.ui.common.ConfirmDialog
import com.widgetsplus.ui.common.TextInputDialog
import com.widgetsplus.ui.common.displayName
import com.widgetsplus.ui.frost.FrostIcon
import com.widgetsplus.ui.frost.FrostIconButton
import com.widgetsplus.ui.frost.FrostIcons
import com.widgetsplus.ui.frost.FrostNote
import com.widgetsplus.ui.frost.FrostPage
import com.widgetsplus.ui.frost.FrostRow
import com.widgetsplus.ui.frost.FrostShapes
import com.widgetsplus.ui.frost.FrostToggle
import com.widgetsplus.ui.frost.FrostType
import com.widgetsplus.ui.frost.GroupLabel
import com.widgetsplus.ui.frost.LocalFrost
import com.widgetsplus.ui.frost.frost

/**
 * The open folder's tabs in their order: "All" and every category. Drag a handle (or use a row's
 * Move up / Move down) to rearrange them; "All" can sit anywhere and has its own on/off switch.
 * While "All" is hidden, apps without a category get an "Other" tab, always last.
 */
@Composable
fun CategoriesScreen(folderId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val frost = LocalFrost.current
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
    // Rows are a hairline apart.
    val gapPx = with(LocalDensity.current) { 1.dp.toPx() }
    // "new", or an action and a category id: "rename:12", "sort:12", "delete:12"
    var dialog by rememberSaveable { mutableStateOf("") }

    fun move(from: Int, to: Int) {
        if (from == to || to !in working.indices) return
        working = working.toMutableList().apply { add(to, removeAt(from)) }
        vm.saveTabOrder(working)
    }

    val action = dialog.substringBefore(':')
    val target = dialog.substringAfter(':', "").toLongOrNull()?.let { id -> categories.firstOrNull { it.id == id } }
    if (action == "sort" && target != null) {
        CategorySortPage(target, current.sortMode, onSelect = { vm.setCategorySort(target, it) }, onBack = { dialog = "" })
        return
    }

    FrostPage(title = stringResource(R.string.categories_title), overline = current.displayName(), onBack = onBack) {
        item(key = "intro") { FrostNote(stringResource(R.string.categories_intro)) }
        item(key = "tabs") {
            Column(Modifier.fillMaxWidth().frost(frost).padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 12.dp)) {
                GroupLabel(stringResource(R.string.tabs_group))
                working.forEachIndexed { index, tab ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = frost.divider, modifier = Modifier.padding(horizontal = 10.dp))
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
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { rowHeight = it.height }
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                shadowElevation = if (isDragging) 8.dp.toPx() else 0f
                                shape = FrostShapes.inner
                                clip = false
                            }
                            .then(if (isDragging) Modifier.background(frost.raised, FrostShapes.inner) else Modifier),
                    ) {
                        when (tab) {
                            FolderTab.All -> TabRow(
                                title = stringResource(R.string.category_all),
                                caption = stringResource(if (current.showAllCategory) R.string.category_all_desc else R.string.category_all_hidden),
                                handle = handle,
                                toggle = current.showAllCategory to vm::setShowAllCategory,
                                canMoveUp = index > 0,
                                canMoveDown = index < working.lastIndex,
                                onMove = { move(index, index + it) },
                                onAction = null,
                            )
                            is FolderTab.OfCategory -> {
                                val count = items.count { it.categoryId == tab.category.id }
                                TabRow(
                                    title = tab.category.name,
                                    caption = pluralStringResource(R.plurals.category_app_count, count, count),
                                    handle = handle,
                                    toggle = null,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < working.lastIndex,
                                    onMove = { move(index, index + it) },
                                    onAction = { action -> dialog = "$action:${tab.category.id}" },
                                )
                            }
                        }
                    }
                }
                if (!current.showAllCategory) {
                    HorizontalDivider(thickness = 1.dp, color = frost.divider, modifier = Modifier.padding(horizontal = 10.dp))
                    FrostRow(FrostIcons.Inbox, stringResource(R.string.category_other), stringResource(R.string.category_other_desc))
                }
                HorizontalDivider(thickness = 1.dp, color = frost.divider, modifier = Modifier.padding(horizontal = 10.dp))
                FrostRow(FrostIcons.Plus, stringResource(R.string.new_category), onClick = { dialog = "new" })
            }
        }
    }

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
        action == "delete" && target != null -> ConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_category_confirm, target.name),
            confirmText = stringResource(R.string.delete),
            onConfirm = { vm.deleteCategory(target) },
            onDismiss = { dialog = "" },
        )
    }
}

/**
 * One tab: a drag handle, its name and what's in it, the "All" tab's switch, and a menu to
 * move it (and, for a category, rename, sort or delete it).
 */
@Composable
private fun TabRow(
    title: String,
    caption: String,
    handle: Modifier,
    toggle: Pair<Boolean, (Boolean) -> Unit>?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onAction: ((String) -> Unit)?,
) {
    val frost = LocalFrost.current
    var menu by remember { mutableStateOf(false) }
    val dragLabel = stringResource(R.string.drag_to_reorder)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(FrostShapes.inner).padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(handle.size(44.dp).semantics { contentDescription = dragLabel }, contentAlignment = Alignment.Center) {
            FrostIcon(FrostIcons.DragHandle, frost.textMuted, size = 20.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = FrostType.body, color = frost.textPrimary)
            Text(caption, style = FrostType.small, color = frost.textTertiary)
        }
        if (toggle != null) FrostToggle(toggle.first, toggle.second)
        Box {
            FrostIconButton(FrostIcons.MoreVertical, stringResource(R.string.category_options), { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (onAction != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onAction("rename") })
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_mode)) }, onClick = { menu = false; onAction("sort") })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, enabled = canMoveUp, onClick = { menu = false; onMove(-1) })
                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, enabled = canMoveDown, onClick = { menu = false; onMove(1) })
                if (onAction != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onAction("delete") })
                }
            }
        }
    }
}
