package com.widgetsplus.core

import com.widgetsplus.core.model.AppRef
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.SortMode
import com.widgetsplus.core.sort.AppSorter
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSorterTest {
    private fun item(id: Long, pkg: String, order: Int = 0, launches: Int = 0, last: Long = 0, added: Long = 0) =
        FolderItem(id = id, folderId = 1, app = AppRef(pkg, "$pkg.Main"), manualOrder = order,
            launchCount = launches, lastLaunchedAt = last, addedAt = added)

    private val labels = mapOf("b.maps" to "Maps", "a.zoo" to "zoo", "c.alpha" to "Alpha")
    private val labelOf: (FolderItem) -> String = { labels[it.app.packageName] ?: it.app.packageName }

    private val items = listOf(
        item(1, "b.maps", order = 2, launches = 5, last = 100, added = 10),
        item(2, "a.zoo", order = 0, launches = 9, last = 50, added = 30),
        item(3, "c.alpha", order = 1, launches = 5, last = 200, added = 20),
    )

    @Test fun manualUsesManualOrder() {
        assertEquals(listOf(2L, 3L, 1L), AppSorter.sort(items, SortMode.MANUAL, labelOf).map { it.id })
    }

    @Test fun nameSortIsCaseInsensitive() {
        assertEquals(listOf("Alpha", "Maps", "zoo"), AppSorter.sort(items, SortMode.NAME_ASC, labelOf).map(labelOf))
        assertEquals(listOf("zoo", "Maps", "Alpha"), AppSorter.sort(items, SortMode.NAME_DESC, labelOf).map(labelOf))
    }

    @Test fun mostUsedBreaksTiesByRecency() {
        // zoo has 9 launches; maps and alpha tie at 5, alpha was used more recently.
        assertEquals(listOf(2L, 3L, 1L), AppSorter.sort(items, SortMode.MOST_USED, labelOf).map { it.id })
    }

    @Test fun recentlyUsedAndAdded() {
        assertEquals(listOf(3L, 1L, 2L), AppSorter.sort(items, SortMode.RECENTLY_USED, labelOf).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), AppSorter.sort(items, SortMode.RECENTLY_ADDED, labelOf).map { it.id })
    }

    @Test fun moveRenumbers() {
        val manual = AppSorter.sort(items, SortMode.MANUAL, labelOf) // ids 2,3,1
        val moved = AppSorter.move(manual, from = 2, to = 0)
        assertEquals(listOf(1L, 2L, 3L), moved.map { it.id })
        assertEquals(listOf(0, 1, 2), moved.map { it.manualOrder })
    }

    @Test fun moveOutOfRangeIsNoOp() {
        val manual = AppSorter.sort(items, SortMode.MANUAL, labelOf)
        assertEquals(manual.map { it.id }, AppSorter.move(manual, 0, 7).map { it.id })
    }
}
