package com.widgetsplus.core.sort

import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.SortMode

/**
 * Pure ordering of folder items. Labels are resolved by the caller (they come
 * from PackageManager on Android) so this stays unit-testable.
 */
object AppSorter {

    fun sort(
        items: List<FolderItem>,
        mode: SortMode,
        labelOf: (FolderItem) -> String,
    ): List<FolderItem> {
        val byName = compareBy<FolderItem, String>(String.CASE_INSENSITIVE_ORDER) { labelOf(it) }
            .thenBy { it.app.flatten() }
        return when (mode) {
            SortMode.MANUAL -> items.sortedWith(compareBy<FolderItem> { it.manualOrder }.thenBy { it.id })
            SortMode.NAME_ASC -> items.sortedWith(byName)
            SortMode.NAME_DESC -> items.sortedWith(byName.reversed())
            SortMode.MOST_USED -> items.sortedWith(
                compareByDescending<FolderItem> { it.launchCount }
                    .thenByDescending { it.lastLaunchedAt }
                    .then(byName),
            )
            SortMode.RECENTLY_USED -> items.sortedWith(
                compareByDescending<FolderItem> { it.lastLaunchedAt }.then(byName),
            )
            SortMode.RECENTLY_ADDED -> items.sortedWith(
                compareByDescending<FolderItem> { it.addedAt }.then(byName),
            )
        }
    }

    /**
     * Moves the item at [from] to [to] and returns the list with manualOrder
     * renumbered 0..n-1 so the new order is stable when persisted.
     */
    fun move(items: List<FolderItem>, from: Int, to: Int): List<FolderItem> {
        if (from !in items.indices || to !in items.indices || from == to) {
            return renumber(items)
        }
        val mutable = items.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        return renumber(mutable)
    }

    /**
     * The folder's whole order after one tab was rearranged: the tab's apps take the same places
     * they held in [whole], in their new order [tabOrder]; every other app keeps its place.
     * Apps in [tabOrder] that aren't in [whole] are ignored. For the "All" tab, [tabOrder] is
     * simply the new whole order.
     */
    fun mergeTabOrder(whole: List<FolderItem>, tabOrder: List<FolderItem>): List<FolderItem> {
        val wholeIds = whole.map { it.id }.toSet()
        val moved = tabOrder.filter { it.id in wholeIds }
        val movedIds = moved.map { it.id }.toSet()
        val next = moved.iterator()
        return whole.map { item -> if (item.id in movedIds) next.next() else item }
    }

    fun renumber(items: List<FolderItem>): List<FolderItem> =
        items.mapIndexed { index, item -> if (item.manualOrder == index) item else item.copy(manualOrder = index) }
}
