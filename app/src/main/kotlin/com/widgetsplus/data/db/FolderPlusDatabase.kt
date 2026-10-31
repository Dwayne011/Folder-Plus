package com.widgetsplus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, CategoryEntity::class, FolderItemEntity::class, WidgetBindingEntity::class],
    version = 4,
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

        /** v3 -> v4: popup frosting level. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN frosting INTEGER NOT NULL DEFAULT 40")
            }
        }

        fun build(context: Context): FolderPlusDatabase =
            Room.databaseBuilder(context.applicationContext, FolderPlusDatabase::class.java, "widgetsplus.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
