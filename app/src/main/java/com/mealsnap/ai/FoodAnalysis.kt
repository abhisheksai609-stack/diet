package com.mealsnap.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class FoodItem(
    val name: String = "",
    val portion: String = "",
    val calories: Int = 0,
)

@Serializable
data class FoodAnalysis(
    @SerialName("is_food") val isFood: Boolean = true,
    @SerialName("meal_name") val mealName: String = "",
    val items: List<FoodItem> = emptyList(),
    @SerialName("total_calories") val totalCalories: Int = 0,
    @SerialName("protein_g") val proteinG: Int = 0,
    @SerialName("carbs_g") val carbsG: Int = 0,
    @SerialName("fat_g") val fatG: Int = 0,
    val confidence: String = "medium",
    val notes: String = "",
)

object FoodAnalysisParser {
    private const val MAX_CALORIES = 10_000
    private const val MAX_MACRO = 1_000

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /** Parses the model's JSON reply (tolerating a markdown fence) and sanitises the numbers. */
    fun parse(text: String): FoodAnalysis {
        val raw = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val parsed = json.decodeFromString(FoodAnalysis.serializer(), raw)

        val items = parsed.items.map { it.copy(calories = it.calories.coerceIn(0, MAX_CALORIES)) }
        var total = parsed.totalCalories.coerceIn(0, MAX_CALORIES)
        if (total == 0 && items.isNotEmpty()) total = items.sumOf { it.calories }.coerceAtMost(MAX_CALORIES)

        return parsed.copy(
            items = items,
            totalCalories = total,
            proteinG = parsed.proteinG.coerceIn(0, MAX_MACRO),
            carbsG = parsed.carbsG.coerceIn(0, MAX_MACRO),
            fatG = parsed.fatG.coerceIn(0, MAX_MACRO),
            confidence = parsed.confidence.lowercase().takeIf { it in setOf("high", "medium", "low") } ?: "medium",
        )
    }
}

object FoodItems {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(FoodItem.serializer())

    fun encode(items: List<FoodItem>): String = json.encodeToString(serializer, items)

    fun decode(text: String?): List<FoodItem> =
        if (text.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
}
