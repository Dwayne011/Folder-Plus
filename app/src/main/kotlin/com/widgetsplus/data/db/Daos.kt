package com.widgetsplus.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY position, id")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY position, id")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observe(id: Long): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: Long): FolderEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM folders")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE folderId = :folderId ORDER BY position, id")
    fun observeForFolder(folderId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE folderId = :folderId ORDER BY position, id")
    suspend fun getForFolder(folderId: Long): List<CategoryEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM categories WHERE folderId = :folderId")
    suspend fun nextPosition(folderId: Long): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)
}

@Dao
interface FolderItemDao {
    @Query("SELECT * FROM folder_items WHERE folderId = :folderId ORDER BY manualOrder, id")
    fun observeForFolder(folderId: Long): Flow<List<FolderItemEntity>>

    @Query("SELECT * FROM folder_items WHERE folderId = :folderId ORDER BY manualOrder, id")
    suspend fun getForFolder(folderId: Long): List<FolderItemEntity>

    @Query("SELECT * FROM folder_items ORDER BY folderId, manualOrder, id")
    suspend fun getAll(): List<FolderItemEntity>

    @Query("SELECT * FROM folder_items ORDER BY folderId, manualOrder, id")
    fun observeAll(): Flow<List<FolderItemEntity>>

    @Query("SELECT * FROM folder_items WHERE id = :id")
    suspend fun get(id: Long): FolderItemEntity?

    @Query("SELECT COALESCE(MAX(manualOrder), -1) + 1 FROM folder_items WHERE folderId = :folderId")
    suspend fun nextOrder(folderId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<FolderItemEntity>): List<Long>

    @Update
    suspend fun updateAll(items: List<FolderItemEntity>)

    @Query("UPDATE folder_items SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long?)

    @Query("UPDATE folder_items SET launchCount = launchCount + 1, lastLaunchedAt = :now WHERE id = :id")
    suspend fun recordLaunch(id: Long, now: Long)

    @Query("DELETE FROM folder_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM folder_items WHERE app IN (:apps)")
    suspend fun deleteByApps(apps: List<String>)

    @Delete
    suspend fun deleteEntities(items: List<FolderItemEntity>)
}

@Dao
interface WidgetBindingDao {
    @Query("SELECT * FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    suspend fun get(appWidgetId: Int): WidgetBindingEntity?

    @Query("SELECT * FROM widget_bindings WHERE folderId = :folderId")
    suspend fun getForFolder(folderId: Long): List<WidgetBindingEntity>

    @Query("SELECT * FROM widget_bindings")
    suspend fun getAll(): List<WidgetBindingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(binding: WidgetBindingEntity)

    @Query("DELETE FROM widget_bindings WHERE appWidgetId IN (:ids)")
    suspend fun delete(ids: List<Int>)

    @Query("DELETE FROM widget_bindings")
    suspend fun deleteAll()
}
