package com.folderplus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, CategoryEntity::class, FolderItemEntity::class, WidgetBindingEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FolderPlusDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun categories(): CategoryDao
    abstract fun items(): FolderItemDao
    abstract fun widgetBindings(): WidgetBindingDao

    companion object {
        fun build(context: Context): FolderPlusDatabase =
            Room.databaseBuilder(context.applicationContext, FolderPlusDatabase::class.java, "folderplus.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
