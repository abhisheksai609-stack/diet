package com.mealsnap.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    companion object {
        fun fromName(name: String?): MealType =
            entries.firstOrNull { it.name == name } ?: SNACK
    }
}

@Entity(tableName = "meals", indices = [Index("createdAt")])
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis of when the meal was logged (i.e. when the photo was taken). */
    val createdAt: Long,
    /** [MealType] name. */
    val mealType: String,
    val name: String,
    val calories: Int,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
    /** Absolute path of the stored, downscaled photo (app-private storage). */
    val photoPath: String? = null,
    /** JSON list of [com.mealsnap.ai.FoodItem] from the AI breakdown. */
    val itemsJson: String = "[]",
    val notes: String = "",
    /** "high" / "medium" / "low" when the numbers came from the AI. */
    val confidence: String? = null,
    val aiEstimated: Boolean = false,
) {
    val type: MealType get() = MealType.fromName(mealType)
}
