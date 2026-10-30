package com.folderplus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, CategoryEntity::class, FolderItemEntity::class, WidgetBindingEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class FolderPlusDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun categories(): CategoryDao
    abstract fun items(): FolderItemDao
    abstract fun widgetBindings(): WidgetBindingDao

    companion object {
        /** v1 -> v2: hosted app widget per folder. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN hostedWidgetProvider TEXT")
                db.execSQL("ALTER TABLE folders ADD COLUMN hostedWidgetId INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 -> v3: optional "All" tab. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN showAllCategory INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun build(context: Context): FolderPlusDatabase =
            Room.databaseBuilder(context.applicationContext, FolderPlusDatabase::class.java, "folderplus.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
