package com.widgetsplus.core.model

import com.widgetsplus.core.sort.AppSorter

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

/**
 * One tab of the open folder with its apps in order: also one card of the folder widget when it
 * swipes between categories. [key] identifies the tab across both: "all", "other", or "c" and the
 * category's id. [category] is null for All and Other.
 */
data class TabContent(val key: String, val category: Category?, val items: List<FolderItem>)

object FolderTabContents {
    const val KEY_ALL = "all"
    const val KEY_OTHER = "other"

    fun keyOf(category: Category): String = "c${category.id}"

    /**
     * The folder's tabs in the order the user arranged them, each with its apps sorted (a
     * category's own sort, or the folder's). "All" is left out when hidden; then apps in no
     * category get an "Other" tab, last. A folder without categories has just "All".
     */
    fun build(folder: Folder, categories: List<Category>, items: List<FolderItem>, labelOf: (AppRef) -> String): List<TabContent> {
        fun sorted(list: List<FolderItem>, mode: SortMode) = AppSorter.sort(list, mode) { labelOf(it.app) }
        if (categories.isEmpty()) return listOf(TabContent(KEY_ALL, null, sorted(items, folder.sortMode)))

        val tabs = mutableListOf<TabContent>()
        FolderTabs.ordered(categories, folder.allTabPosition).forEach { tab ->
            when (tab) {
                FolderTab.All -> if (folder.showAllCategory) tabs += TabContent(KEY_ALL, null, sorted(items, folder.sortMode))
                is FolderTab.OfCategory -> tabs += TabContent(
                    keyOf(tab.category),
                    tab.category,
                    sorted(items.filter { it.categoryId == tab.category.id }, tab.category.sortMode ?: folder.sortMode),
                )
            }
        }
        if (!folder.showAllCategory) {
            val knownIds = categories.map { it.id }.toSet()
            val other = items.filter { it.categoryId == null || it.categoryId !in knownIds }
            if (other.isNotEmpty()) tabs += TabContent(KEY_OTHER, null, sorted(other, folder.sortMode))
        }
        return tabs
    }
}
