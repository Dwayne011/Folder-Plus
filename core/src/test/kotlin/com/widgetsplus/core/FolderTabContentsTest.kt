package com.widgetsplus.core

import com.widgetsplus.core.model.AppRef
import com.widgetsplus.core.model.Category
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.FolderTabContents
import com.widgetsplus.core.model.SortMode
import com.widgetsplus.core.widget.MosaicLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderTabContentsTest {
    private val google = Category(id = 1, folderId = 9, name = "Google", position = 0)
    private val utility = Category(id = 2, folderId = 9, name = "Utility", position = 1)
    private fun item(id: Long, cat: Long?, label: String) =
        FolderItem(id = id, folderId = 9, categoryId = cat, app = AppRef("pkg.$label", "Act"), manualOrder = id.toInt())
    private val items = listOf(item(1, 1, "maps"), item(2, 2, "glance"), item(3, 1, "drive"), item(4, null, "loose"))
    private fun labelOf(app: AppRef) = app.packageName.removePrefix("pkg.")

    @Test fun hiddenAllGivesCategoriesThenOther() {
        val folder = Folder(id = 9, name = "Google", sortMode = SortMode.NAME_ASC, showAllCategory = false)
        val tabs = FolderTabContents.build(folder, listOf(utility, google), items, ::labelOf)
        assertEquals(listOf("c1", "c2", "other"), tabs.map { it.key })
        assertEquals(listOf("drive", "maps"), tabs[0].items.map { labelOf(it.app) })
        assertEquals(listOf("loose"), tabs[2].items.map { labelOf(it.app) })
    }

    @Test fun shownAllSitsWhereTheUserPutItAndThereIsNoOther() {
        val folder = Folder(id = 9, name = "Google", sortMode = SortMode.NAME_ASC, showAllCategory = true, allTabPosition = 1)
        val tabs = FolderTabContents.build(folder, listOf(google, utility), items, ::labelOf)
        assertEquals(listOf("c1", "all", "c2"), tabs.map { it.key })
        assertEquals(4, tabs[1].items.size)
    }

    @Test fun aFolderWithoutCategoriesHasJustAll() {
        val folder = Folder(id = 9, name = "Google", showAllCategory = false)
        assertEquals(listOf("all"), FolderTabContents.build(folder, emptyList(), items, ::labelOf).map { it.key })
    }

    /** The category name pill takes a row's worth of space: as in the approved mockup. */
    @Test fun namePillTakesRoomFromTheMosaic() {
        fun slots(w: Float, h: Float, pill: Boolean) =
            MosaicLayout.plan(w, h, 100, if (pill) MosaicLayout.NAME_PILL_DP else 0f).let { it.columns * it.rows }
        assertEquals(4, slots(62f, 190f, false))
        assertEquals(3, slots(62f, 190f, true))
        assertEquals(12, slots(150f, 190f, false))
        assertEquals(9, slots(150f, 190f, true))
        assertEquals(7, slots(318f, 86f, false))
        assertEquals(7, slots(318f, 86f, true))
        // "+N" is counted for the space left: 12 Google apps on a 1x2 with the pill show 2 and "+10".
        val plan = MosaicLayout.plan(62f, 190f, 12, MosaicLayout.NAME_PILL_DP)
        assertEquals(2, plan.shown)
        assertEquals(10, plan.more)
    }
}
