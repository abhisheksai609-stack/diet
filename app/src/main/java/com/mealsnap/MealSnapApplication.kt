package com.mealsnap

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.mealsnap.ai.FoodAnalyzer
import com.mealsnap.ai.FoodClassifier
import com.mealsnap.ai.GeminiAnalyzer
import com.mealsnap.ai.GeminiCoach
import com.mealsnap.ai.NanoRecognizer
import com.mealsnap.ai.RecognitionService
import com.mealsnap.coach.CoachDataSource
import com.mealsnap.data.AppDatabase
import com.mealsnap.data.PhotoStore
import com.mealsnap.data.SettingsRepository
import com.mealsnap.health.HealthRepository

/** Tiny hand-rolled dependency container; this app is small enough not to need Hilt. */
class AppContainer(context: Context) {
    private val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mealsnap.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    val mealDao = database.mealDao()
    val weightDao = database.weightDao()
    val settings = SettingsRepository(context)
    val photos = PhotoStore(context)
    val analyzer = FoodAnalyzer()
    val gemini = GeminiAnalyzer()
    val coach = GeminiCoach(gemini, settings)
    val health = HealthRepository(context.applicationContext)
    val coachData = CoachDataSource(mealDao, weightDao, settings, health)
    val nano = NanoRecognizer()
    val classifier = FoodClassifier(context.applicationContext)
    val recognizer = RecognitionService(gemini, analyzer, classifier, nano, settings)
}

class MealSnapApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
