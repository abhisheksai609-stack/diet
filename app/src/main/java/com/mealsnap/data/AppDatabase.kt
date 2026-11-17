package com.mealsnap.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Meal::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao

    companion object {
        /** Adds the detailed nutrition columns; existing meals keep their data. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meals ADD COLUMN fiberG INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meals ADD COLUMN sugarG INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meals ADD COLUMN sodiumMg INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meals ADD COLUMN cuisine TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE meals ADD COLUMN visualCues TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
