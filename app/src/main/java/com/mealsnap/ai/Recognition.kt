package com.mealsnap.ai

import com.mealsnap.nutrition.Dish
import com.mealsnap.nutrition.FoodEntry

/** Which part of the app (or which service) identified the food. */
enum class Engine(val label: String, val onDevice: Boolean) {
    GEMINI("Google Gemini", onDevice = false),
    CLAUDE("Claude", onDevice = false),
    NANO("Gemini Nano on your phone", onDevice = true),
    CLASSIFIER("food recognition on your phone", onDevice = true),
    DATABASE("the built-in food table", onDevice = true),
}

/** Another dish the photo might show, offered as a one-tap correction. */
data class Alternative(val label: String, val entry: FoodEntry)

/**
 * The outcome of looking at a meal photo.
 *
 * [dish] is set when the numbers come from the built-in nutrition table, which lets the portion be
 * changed without running recognition again. Claude results leave it null.
 */
data class Recognition(
    val engine: Engine,
    val analysis: FoodAnalysis,
    val dish: Dish? = null,
    val alternatives: List<Alternative> = emptyList(),
)
