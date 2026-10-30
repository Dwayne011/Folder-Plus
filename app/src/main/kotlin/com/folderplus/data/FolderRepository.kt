package com.folderplus.data

import androidx.room.withTransaction
import com.folderplus.core.model.AppRef
import com.folderplus.core.model.Category
import com.folderplus.core.model.Folder
import com.folderplus.core.model.FolderItem
import com.folderplus.core.sort.AppSorter
import com.folderplus.data.db.FolderPlusDatabase
import com.folderplus.data.db.WidgetBindingEntity
import com.folderplus.data.db.toEntity
import com.folderplus.data.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single entry point for reading and writing folders, categories, items and widget bindings. */
class FolderRepository(private val db: FolderPlusDatabase) {

    // ---- folders ----
    fun observeFolders(): Flow<List<Folder>> = db.folders().observeAll().map { list -> list.map { it.toModel() } }
    fun observeFolder(id: Long): Flow<Folder?> = db.folders().observe(id).map { it?.toModel() }
    suspend fun getFolders(): List<Folder> = db.folders().getAll().map { it.toModel() }
    suspend fun getFolder(id: Long): Folder? = db.folders().get(id)?.toModel()

    suspend fun createFolder(name: String): Long {
        val position = db.folders().nextPosition()
        return db.folders().insert(Folder(name = name.trim(), position = position).toEntity())
    }

    suspend fun updateFolder(folder: Folder) = db.folders().update(folder.toEntity())
    suspend fun deleteFolder(id: Long) = db.folders().delete(id)

    // ---- categories ----
    fun observeCategories(folderId: Long): Flow<List<Category>> =
        db.categories().observeForFolder(folderId).map { list -> list.map { it.toModel() } }
    suspend fun getCategories(folderId: Long): List<Category> = db.categories().getForFolder(folderId).map { it.toModel() }

    suspend fun addCategory(folderId: Long, name: String): Long {
        val position = db.categories().nextPosition(folderId)
        return db.categories().insert(Category(folderId = folderId, name = name.trim(), position = position).toEntity())
    }

    suspend fun updateCategory(category: Category) = db.categories().update(category.toEntity())
    suspend fun deleteCategory(id: Long) = db.categories().delete(id)

    // ---- items ----
    fun observeItems(folderId: Long): Flow<List<FolderItem>> =
        db.items().observeForFolder(folderId).map { list -> list.mapNotNull { it.toModel() } }
    suspend fun getItems(folderId: Long): List<FolderItem> = db.items().getForFolder(folderId).mapNotNull { it.toModel() }

    /** Number of apps per folder, for the folder list. */
    fun observeItemCounts(): Flow<Map<Long, Int>> =
        db.items().observeAll().map { list -> list.groupingBy { it.folderId }.eachCount() }

    suspend fun addApps(folderId: Long, apps: Collection<AppRef>, categoryId: Long? = null) {
        if (apps.isEmpty()) return
        db.withTransaction {
            var order = db.items().nextOrder(folderId)
            val now = System.currentTimeMillis()
            val entities = apps.distinct().map { app ->
                FolderItem(folderId = folderId, categoryId = categoryId, app = app, manualOrder = order++, addedAt = now).toEntity()
            }
            db.items().insertAll(entities)
        }
    }

    suspend fun removeItem(itemId: Long) = db.items().delete(itemId)
    suspend fun setItemCategory(itemId: Long, categoryId: Long?) = db.items().setCategory(itemId, categoryId)

    suspend fun recordLaunch(itemId: Long) = db.items().recordLaunch(itemId, System.currentTimeMillis())

    /** Persists a manual reorder: [orderedItems] is the full item list in its new order. */
    suspend fun saveManualOrder(orderedItems: List<FolderItem>) {
        val renumbered = AppSorter.renumber(orderedItems)
        db.items().updateAll(renumbered.map { it.toEntity() })
    }

    /**
     * Removes items whose apps were uninstalled and clears default apps that
     * point at them. Returns how many items were removed.
     */
    suspend fun pruneMissingApps(installed: Set<AppRef>): Int = db.withTransaction {
        val all = db.items().getAll()
        val missing = all.filter { entity -> AppRef.unflatten(entity.app)?.let { it !in installed } ?: true }
        if (missing.isNotEmpty()) db.items().deleteEntities(missing)
        db.folders().getAll().forEach { entity ->
            val default = entity.defaultApp?.let(AppRef::unflatten)
            if (entity.defaultApp != null && (default == null || default !in installed)) {
                db.folders().update(entity.copy(defaultApp = null))
            }
        }
        missing.size
    }

    // ---- widget bindings ----
    suspend fun bindWidget(appWidgetId: Int, folderId: Long) =
        db.widgetBindings().upsert(WidgetBindingEntity(appWidgetId, folderId))
    suspend fun folderIdForWidget(appWidgetId: Int): Long? = db.widgetBindings().get(appWidgetId)?.folderId
    suspend fun widgetIdsForFolder(folderId: Long): List<Int> = db.widgetBindings().getForFolder(folderId).map { it.appWidgetId }
    suspend fun allWidgetBindings(): Map<Int, Long> = db.widgetBindings().getAll().associate { it.appWidgetId to it.folderId }
    suspend fun unbindWidgets(appWidgetIds: List<Int>) = db.widgetBindings().delete(appWidgetIds)
}
