package com.mealsnap

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.mealsnap.ai.FoodAnalyzer
import com.mealsnap.data.AppDatabase
import com.mealsnap.data.PhotoStore
import com.mealsnap.data.SettingsRepository

/** Tiny hand-rolled dependency container; this app is small enough not to need Hilt. */
class AppContainer(context: Context) {
    private val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mealsnap.db").build()

    val mealDao = database.mealDao()
    val settings = SettingsRepository(context)
    val photos = PhotoStore(context)
    val analyzer = FoodAnalyzer()
}

class MealSnapApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
