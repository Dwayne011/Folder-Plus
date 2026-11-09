package com.widgetsplus.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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
import com.widgetsplus.ui.common.TextInputDialog
import com.widgetsplus.ui.common.displayName
import com.widgetsplus.ui.frost.FrostBackground
import com.widgetsplus.ui.frost.FrostCheck
import com.widgetsplus.ui.frost.FrostChip
import com.widgetsplus.ui.frost.FrostCircleButton
import com.widgetsplus.ui.frost.FrostGroup
import com.widgetsplus.ui.frost.FrostHeader
import com.widgetsplus.ui.frost.FrostIcon
import com.widgetsplus.ui.frost.FrostIconButton
import com.widgetsplus.ui.frost.FrostIcons
import com.widgetsplus.ui.frost.FrostNote
import com.widgetsplus.ui.frost.FrostPill
import com.widgetsplus.ui.frost.FrostShapes
import com.widgetsplus.ui.frost.FrostType
import com.widgetsplus.ui.frost.LocalFrost
import com.widgetsplus.ui.frost.frostSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** Lists the apps in one folder: filter by category, assign categories, remove, and drag to reorder. */
@Composable
fun AppsScreen(
    folderId: Long,
    onBack: () -> Unit,
    onAddApps: (categoryId: Long?) -> Unit,
    onEditCategories: () -> Unit,
    onAddShortcut: (categoryId: Long?) -> Unit,
) {
    val context = LocalContext.current
    val frost = LocalFrost.current
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

    // Choices open as their own page, like the launcher's.
    if (bulkCategoryDialog) {
        MoveToCategoryPage(
            overline = stringResource(R.string.selected_count, selectedIds.size),
            categories = categories,
            selected = null,
            onSelect = { target ->
                vm.setItemsCategory(selectedIds, target)
                exitSelection()
            },
            onBack = { bulkCategoryDialog = false },
        )
        return
    }
    if (categoryDialogFor > 0) {
        val item = sortedItems.firstOrNull { it.id == categoryDialogFor }
        if (item == null) {
            categoryDialogFor = -1L
        } else {
            MoveToCategoryPage(
                overline = vm.label(item.app),
                categories = categories,
                selected = item.categoryId,
                onSelect = { vm.setItemCategory(item, it) },
                onBack = { categoryDialogFor = -1L },
            )
            return
        }
    }
    if (categoryDialog == "sort" && activeCategory != null) {
        CategorySortPage(activeCategory, current.sortMode, onSelect = { vm.setCategorySort(activeCategory, it) }, onBack = { categoryDialog = "" })
        return
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    FrostBackground {
        Column(Modifier.fillMaxSize().padding(top = top + 12.dp)) {
            Box(Modifier.padding(horizontal = 14.dp)) {
                if (selecting) {
                    FrostHeader(
                        title = stringResource(R.string.selected_count, selectedIds.size),
                        overline = current.displayName(),
                        onBack = { exitSelection() },
                        backIcon = FrostIcons.Close,
                        backDescription = stringResource(R.string.clear_selection),
                        actions = {
                            FrostCircleButton(
                                FrostIcons.MoveTo, stringResource(R.string.move_to_category), { bulkCategoryDialog = true },
                                enabled = selectedIds.isNotEmpty() && categories.isNotEmpty(),
                            )
                            FrostCircleButton(FrostIcons.Trash, stringResource(R.string.remove_from_folder), { bulkRemoveDialog = true }, enabled = selectedIds.isNotEmpty())
                        },
                    )
                } else {
                    FrostHeader(
                        title = stringResource(R.string.apps_in_folder),
                        overline = current.displayName(),
                        onBack = onBack,
                        actions = {
                            if (sortedItems.isNotEmpty()) FrostCircleButton(FrostIcons.Checklist, stringResource(R.string.select), { selecting = true })
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (categories.isNotEmpty()) {
                        FrostChip(stringResource(R.string.category_all), selected = !filtering, onClick = { selectedCategory = -1L })
                    }
                    categories.forEach { category ->
                        FrostChip(category.name, selected = selectedCategory == category.id, onClick = { selectedCategory = category.id })
                    }
                    FrostChip(stringResource(R.string.new_category), selected = false, onClick = { categoryDialog = "new" }, icon = FrostIcons.Plus)
                }
                FrostCircleButton(FrostIcons.Edit, stringResource(R.string.edit_categories), onEditCategories)
                if (activeCategory != null) {
                    Box {
                        FrostCircleButton(FrostIcons.MoreVertical, stringResource(R.string.category_options), { categoryMenu = true })
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { categoryMenu = false; categoryDialog = "rename" })
                            DropdownMenuItem(text = { Text(stringResource(R.string.sort_mode)) }, onClick = { categoryMenu = false; categoryDialog = "sort" })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { categoryMenu = false; categoryDialog = "delete" })
                        }
                    }
                }
            }
            if (manual) {
                FrostNote(
                    stringResource(if (filtering) R.string.manual_sort_filtered_hint else R.string.manual_sort_hint),
                    Modifier.padding(horizontal = 14.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }
            // Room under the last rows for the floating buttons.
            val listBottom = bottom + if (selecting) 18.dp else 156.dp
            if (visible.isEmpty()) {
                Box(Modifier.padding(horizontal = 14.dp)) {
                    FrostGroup(null, listOf { FrostNote(stringResource(R.string.folder_empty)) })
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
                    bottomSpace = listBottom,
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
        if (!selecting) {
            // Shortcuts have their own list (there can be many), so their own button.
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottom + 16.dp),
            ) {
                FrostPill(stringResource(R.string.add_shortcut), FrostIcons.Shortcut, { onAddShortcut(selectedCategory.takeIf { it > 0 }) }, primary = false)
                FrostPill(
                    if (activeCategory != null) stringResource(R.string.add_to_category, activeCategory.name) else stringResource(R.string.add_apps),
                    FrostIcons.Plus,
                    { onAddApps(selectedCategory.takeIf { it > 0 }) },
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
}

/** How near the list's top or bottom edge the finger must be, while dragging, for the list to scroll. */
private val AutoScrollEdge = 64.dp

/** How fast the list scrolls while dragging, per second, with the finger at (or past) the very edge. */
private val AutoScrollMaxSpeed = 1200.dp

/** What a row does when it's dragged; null when the list can't be reordered. */
private class RowReorder(
    /** The row was picked up, with the finger this far down it; false if another row already is. */
    val onPickUp: (y: Float) -> Boolean,
    val onDragBy: (dy: Float) -> Unit,
    /** The finger lifted (true) or the gesture was taken over (false). */
    val onDrop: (completed: Boolean) -> Unit,
)

/**
 * A LazyColumn whose rows can be dragged to a new place when [dragEnabled]: after a short hold on
 * the handle, icon or name, or a long press anywhere else on the row. A haptic buzz when a row is
 * picked up and a tick each time it passes another; near the list's top or bottom edge the list
 * scrolls, faster nearer the edge. The order is kept locally while dragging and committed once on
 * release.
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
    bottomSpace: Dp,
    onToggleSelected: (FolderItem) -> Unit,
    onOrderCommitted: (List<FolderItem>) -> Unit,
    onSetDefault: (FolderItem) -> Unit,
    onRemove: (FolderItem) -> Unit,
    onChangeCategory: (FolderItem) -> Unit,
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edgePx = with(density) { AutoScrollEdge.toPx() }
    val maxSpeedPx = with(density) { AutoScrollMaxSpeed.toPx() }
    val latestItems by rememberUpdatedState(items)
    val commit by rememberUpdatedState(onOrderCommitted)

    var working by remember(items) { mutableStateOf(items) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    // The dragged row's top in the list when it was picked up, where the finger was on it, and
    // how far the finger has moved since.
    var startTop by remember { mutableFloatStateOf(0f) }
    var grabY by remember { mutableFloatStateOf(0f) }
    var moved by remember { mutableFloatStateOf(0f) }

    fun rowInfo(id: Long) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }

    /** The dragged row's top: under the finger, kept within the list. */
    fun draggedTop(size: Int): Float {
        val layout = listState.layoutInfo
        val min = layout.viewportStartOffset.toFloat()
        val max = maxOf(min, (layout.viewportEndOffset - size).toFloat())
        return (startTop + moved).coerceIn(min, max)
    }

    fun pickUp(id: Long, y: Float): Boolean {
        if (draggingId != null) return false
        val info = rowInfo(id) ?: return false
        draggingId = id
        startTop = info.offset.toFloat()
        grabY = y
        moved = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
    }

    fun putDown(completed: Boolean) {
        if (draggingId == null) return
        draggingId = null
        moved = 0f
        if (!completed) {
            working = latestItems
        } else if (working != latestItems) {
            commit(working)
        }
    }

    /** Moves the dragged app one place towards the finger once its middle passes the next row's. */
    fun followFinger(id: Long) {
        val current = rowInfo(id) ?: return
        val from = working.indexOfFirst { it.id == id }
        if (from < 0 || current.index != from) return // the list hasn't laid out the last move yet
        val middle = draggedTop(current.size) + current.size / 2f
        val visible = listState.layoutInfo.visibleItemsInfo
        val below = visible.firstOrNull { it.index == from + 1 }
        val above = visible.firstOrNull { it.index == from - 1 }
        val to = when {
            below != null && middle > below.offset + below.size / 2f -> from + 1
            above != null && middle < above.offset + above.size / 2f -> from - 1
            else -> return
        }
        val first = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        working = AppSorter.move(working, from, to)
        // Keep the list still: it would otherwise follow the first visible row when that one moves.
        if (from == first || to == first) listState.requestScrollToItem(first, firstOffset)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // While a row is held: each frame, scroll when the finger is near an edge, then let the row
    // follow it. An edge only scrolls once the finger has been away from it, or moves towards it,
    // so picking up a row near the top or bottom doesn't set the list off.
    LaunchedEffect(draggingId) {
        val id = draggingId ?: return@LaunchedEffect
        var armedTop = false
        var armedBottom = false
        var last = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            val layout = listState.layoutInfo
            val finger = startTop + grabY + moved
            val topZone = layout.viewportStartOffset + edgePx
            val bottomZone = layout.viewportEndOffset - edgePx
            if (finger >= topZone) armedTop = true
            if (finger <= bottomZone) armedBottom = true
            val speed = when {
                finger < topZone && (armedTop || moved < -edgePx / 4) ->
                    -maxSpeedPx * ((topZone - finger) / edgePx).coerceAtMost(1f)
                finger > bottomZone && (armedBottom || moved > edgePx / 4) ->
                    maxSpeedPx * ((finger - bottomZone) / edgePx).coerceAtMost(1f)
                else -> 0f
            }
            if (speed != 0f) {
                try {
                    listState.scrollBy(speed * seconds)
                } catch (e: CancellationException) {
                    ensureActive() // only a scroll that replaced ours; keep going
                }
            }
            followFinger(id)
        }
    }

    val frost = LocalFrost.current
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomSpace),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(working, key = { _, item -> item.id }) { index, item ->
            val dragging = item.id == draggingId
            if (dragging) {
                // Keep the held row, and its gesture, alive even if the list scrolls it out of view.
                val pinnable = LocalPinnableContainer.current
                DisposableEffect(pinnable) {
                    val handle = pinnable?.pin()
                    onDispose { handle?.release() }
                }
            }
            // The card stays in the row's place; the row itself follows the finger.
            Box(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .frostSegment(frost, first = index == 0, last = index == working.lastIndex)
                    .padding(horizontal = 8.dp),
            ) {
                AppRow(
                    item = item,
                    label = labelOf(item.app),
                    categoryName = categories.firstOrNull { it.id == item.categoryId }?.name,
                    isDefault = isDefault(item),
                    selecting = selecting,
                    selected = item.id in selectedIds,
                    onToggleSelected = { onToggleSelected(item) },
                    onSetDefault = { onSetDefault(item) },
                    onRemove = { onRemove(item) },
                    onChangeCategory = { onChangeCategory(item) },
                    reorder = if (dragEnabled) {
                        RowReorder(
                            onPickUp = { y -> pickUp(item.id, y) },
                            onDragBy = { dy -> moved += dy },
                            onDrop = { completed -> putDown(completed) },
                        )
                    } else {
                        null
                    },
                    modifier = Modifier
                        .graphicsLayer {
                            val info = if (dragging) rowInfo(item.id) else null
                            translationY = if (info != null) draggedTop(info.size) - info.offset else 0f
                            shadowElevation = if (dragging) 8.dp.toPx() else 0f
                            shape = FrostShapes.inner
                            clip = false
                        }
                        .then(if (dragging) Modifier.background(frost.raised, FrostShapes.inner) else Modifier),
                )
            }
        }
    }
}

/** Where a row's drag handle area ends: the start of its star and menu column. */
private class HandleArea {
    var row: LayoutCoordinates? = null
    var trailing: LayoutCoordinates? = null

    /** In the row's coordinates; the whole row until both have been laid out. */
    fun end(): Float {
        val r = row
        val t = trailing
        if (r == null || t == null || !r.isAttached || !t.isAttached) return Float.MAX_VALUE
        return r.localPositionOf(t, Offset.Zero).x
    }
}

/** How long a press on the handle, icon or name must stay still before it picks the row up. */
private const val HANDLE_HOLD_MS = 200L

/**
 * Picks a row up after a short, still hold ([HANDLE_HOLD_MS]) on the handle, icon or name (left of
 * [handleEnd]), or after a long press anywhere else on the row. A quick swipe or tap never picks
 * it up: the swipe scrolls the list. Then reports the finger's movement until it lifts or the
 * gesture is taken over.
 */
private fun Modifier.reorderGesture(key: Any, handleEnd: () -> Float, reorder: () -> RowReorder?): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var picked: PointerInputChange? = null
            if (down.position.x <= handleEnd()) {
                // True when the press lifted, moved past the touch slop (the list scrolls) or was
                // taken by something else before the hold time; null when it held still that long.
                val cancelled = withTimeoutOrNull(HANDLE_HOLD_MS) {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || change.isConsumed ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        ) {
                            break
                        }
                    }
                    true
                }
                if (cancelled == null) picked = down
            } else {
                picked = awaitLongPressOrCancellation(down.id)
            }
            val pointer = picked?.id ?: return@awaitEachGesture
            val callbacks = reorder() ?: return@awaitEachGesture
            if (!callbacks.onPickUp(down.position.y)) return@awaitEachGesture
            val completed = drag(pointer) { change ->
                callbacks.onDragBy(change.positionChange().y)
                change.consume()
            }
            callbacks.onDrop(completed)
        }
    }

@Composable
private fun AppRow(
    item: FolderItem,
    label: String,
    categoryName: String?,
    isDefault: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit,
    onChangeCategory: () -> Unit,
    reorder: RowReorder?,
    modifier: Modifier = Modifier,
) {
    val frost = LocalFrost.current
    var menu by remember { mutableStateOf(false) }
    val area = remember { HandleArea() }
    // The gesture outlives recompositions; it always reaches the latest callbacks.
    val latestReorder by rememberUpdatedState(reorder)
    val gesture = if (reorder != null) {
        Modifier
            .reorderGesture(item.id, handleEnd = area::end, reorder = { latestReorder })
            .onGloballyPositioned { area.row = it }
    } else {
        Modifier
    }
    // Shortcuts say so, beside their category.
    val caption = listOfNotNull(
        if (item.app.isShortcut) stringResource(R.string.shortcut_tag) else null,
        categoryName,
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .then(gesture)
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(FrostShapes.inner)
            .then(if (selecting) Modifier.clickable(onClick = onToggleSelected) else Modifier)
            .padding(start = 10.dp, end = 2.dp, top = 11.dp, bottom = 11.dp),
    ) {
        if (selecting) FrostCheck(selected)
        if (reorder != null) FrostIcon(FrostIcons.DragHandle, frost.textMuted, size = 20.dp)
        AppIconImage(app = item.app, size = 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = FrostType.body, color = frost.textPrimary)
            if (caption != null) Text(caption, style = FrostType.small, color = frost.textTertiary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.onGloballyPositioned { area.trailing = it }) {
            if (isDefault) FrostIcon(FrostIcons.Star, frost.accent, size = 18.dp, filled = true)
            if (!selecting) {
                Box {
                    FrostIconButton(FrostIcons.MoreVertical, stringResource(R.string.more), { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.set_as_default)) }, onClick = { menu = false; onSetDefault() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_to_category)) }, onClick = { menu = false; onChangeCategory() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_folder)) }, onClick = { menu = false; onRemove() })
                    }
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
