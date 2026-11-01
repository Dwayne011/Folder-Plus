package com.widgetsplus.core

import com.widgetsplus.core.model.AppRef
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.sort.AppSorter
import org.junit.Assert.assertEquals
import org.junit.Test

class TabReorderTest {
    private fun item(id: Long, category: Long?) = FolderItem(id = id, folderId = 1, categoryId = category, app = AppRef("pkg$id", "Main"))

    // Folder order: 1(G) 2(U) 3(G) 4(U) 5(G)
    private val whole = listOf(item(1, 10), item(2, 20), item(3, 10), item(4, 20), item(5, 10))

    @Test fun aCategoryTabKeepsItsSlotsInTheWholeOrder() {
        // In the Google tab (1, 3, 5) the user drags 5 to the front: 5, 1, 3
        val tab = listOf(whole[4], whole[0], whole[2])
        assertEquals(listOf(5L, 2L, 1L, 4L, 3L), AppSorter.mergeTabOrder(whole, tab).map { it.id })
    }

    @Test fun theAllTabIsTheWholeOrder() {
        val tab = listOf(whole[1], whole[0], whole[2], whole[3], whole[4])
        assertEquals(listOf(2L, 1L, 3L, 4L, 5L), AppSorter.mergeTabOrder(whole, tab).map { it.id })
    }

    @Test fun unknownAppsAreIgnored() {
        // An app removed meanwhile (99) is dropped; Google's 3, 1, 5 fill Google's places.
        val tab = listOf(item(99, 10), whole[2], whole[0], whole[4])
        assertEquals(listOf(3L, 2L, 1L, 4L, 5L), AppSorter.mergeTabOrder(whole, tab).map { it.id })
    }
}
