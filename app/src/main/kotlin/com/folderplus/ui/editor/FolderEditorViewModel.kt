package com.folderplus.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folderplus.Graph
import com.folderplus.core.model.AppRef
import com.folderplus.core.model.Category
import com.folderplus.core.model.Folder
import com.folderplus.core.model.FolderIconMode
import com.folderplus.core.model.FolderItem
import com.folderplus.core.model.LockMode
import com.folderplus.core.model.SortMode
import com.folderplus.core.model.TapAction
import com.folderplus.core.model.WidgetSize
import com.folderplus.core.sort.AppSorter
import com.folderplus.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared by the editor, the apps list and the picker for one folder. */
class FolderEditorViewModel(private val graph: Graph, val folderId: Long) : ViewModel() {
    private val repo = graph.repository
    private val apps = graph.installedApps

    val folder: StateFlow<Folder?> = repo.observeFolder(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<Category>> = repo.observeCategories(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Items in the folder's current sort order. */
    val items: StateFlow<List<FolderItem>> =
        combine(folder, repo.observeItems(folderId), apps.apps) { f, items, _ ->
            AppSorter.sort(items, f?.sortMode ?: SortMode.NAME_ASC) { apps.label(it.app) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun label(app: AppRef): String = apps.label(app)

    val hasPin: Boolean get() = graph.lockManager.hasPin
    fun biometricsAvailable(): Boolean = graph.lockManager.biometricsAvailable()
    fun setPin(pin: String) = graph.lockManager.setPin(pin)

    private fun update(transform: (Folder) -> Folder) {
        viewModelScope.launch {
            val current = repo.getFolder(folderId) ?: return@launch
            repo.updateFolder(transform(current))
            WidgetUpdater.updateForFolder(graph.appContext, folderId)
        }
    }

    private fun afterChange(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            WidgetUpdater.updateForFolder(graph.appContext, folderId)
        }
    }

    fun rename(name: String) = update { it.copy(name = name) }
    fun setIconMode(mode: FolderIconMode) = update { it.copy(iconMode = mode) }
    fun setDefaultApp(app: AppRef?) = update { it.copy(defaultApp = app) }
    fun setTapAction(action: TapAction) = update { it.copy(tapAction = action) }
    fun setDoubleTapAction(action: TapAction) = update { it.copy(doubleTapAction = action) }
    fun setSortMode(mode: SortMode) = update { it.copy(sortMode = mode) }
    fun setLockMode(mode: LockMode) = update { it.copy(lockMode = mode) }
    fun setWidgetSize(size: WidgetSize) = update { it.copy(widgetSize = size) }
    fun setShowLabel(show: Boolean) = update { it.copy(showLabel = show) }

    fun deleteFolder(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteFolder(folderId)
            WidgetUpdater.updateAll(graph.appContext)
            onDeleted()
        }
    }

    // ---- categories ----
    fun addCategory(name: String) = afterChange { repo.addCategory(folderId, name) }
    fun renameCategory(category: Category, name: String) = afterChange { repo.updateCategory(category.copy(name = name)) }
    fun setCategorySort(category: Category, mode: SortMode?) = afterChange { repo.updateCategory(category.copy(sortMode = mode)) }
    fun deleteCategory(category: Category) = afterChange { repo.deleteCategory(category.id) }

    // ---- items ----
    fun addApps(refs: Collection<AppRef>, categoryId: Long?) = afterChange { repo.addApps(folderId, refs, categoryId) }
    fun removeItem(item: FolderItem) = afterChange {
        repo.removeItem(item.id)
        val f = repo.getFolder(folderId)
        if (f?.defaultApp == item.app) repo.updateFolder(f.copy(defaultApp = null))
    }
    fun setItemCategory(item: FolderItem, categoryId: Long?) = afterChange { repo.setItemCategory(item.id, categoryId) }

    /** Bulk versions used by the app list's selection mode. */
    fun setItemsCategory(itemIds: Collection<Long>, categoryId: Long?) = afterChange {
        itemIds.forEach { repo.setItemCategory(it, categoryId) }
    }

    fun removeItems(itemIds: Collection<Long>) = afterChange {
        val f = repo.getFolder(folderId)
        val removed = items.value.filter { it.id in itemIds }
        itemIds.forEach { repo.removeItem(it) }
        if (f != null && removed.any { it.app == f.defaultApp }) repo.updateFolder(f.copy(defaultApp = null))
    }

    /** Persists a complete manual order (called when a drag ends). */
    fun saveManualOrder(ordered: List<FolderItem>) = afterChange { repo.saveManualOrder(ordered) }
}
