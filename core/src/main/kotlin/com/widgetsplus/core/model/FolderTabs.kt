package com.widgetsplus.core.model

/** One tab of an open folder, in the order the user arranged them. */
sealed interface FolderTab {
    /** Every app in the folder. */
    data object All : FolderTab

    data class OfCategory(val category: Category) : FolderTab
}

object FolderTabs {
    /**
     * The folder's tabs in display order: its categories by position, with "All" placed at
     * [allTabPosition] (clamped). "All" is included even when hidden, so it keeps its place
     * in the Categories list; callers skip it when the folder hides it.
     */
    fun ordered(categories: List<Category>, allTabPosition: Int): List<FolderTab> {
        val tabs: MutableList<FolderTab> = categories
            .sortedWith(compareBy<Category>({ it.position }, { it.id }))
            .map { FolderTab.OfCategory(it) }
            .toMutableList()
        tabs.add(allTabPosition.coerceIn(0, tabs.size), FolderTab.All)
        return tabs
    }

    /** Where "All" sits in [order], and the categories' new positions (0, 1, 2, ...). */
    fun positionsOf(order: List<FolderTab>): Pair<Int, List<Pair<Long, Int>>> {
        val all = order.indexOf(FolderTab.All).coerceAtLeast(0)
        val categories = order.filterIsInstance<FolderTab.OfCategory>().mapIndexed { index, tab -> tab.category.id to index }
        return all to categories
    }
}
