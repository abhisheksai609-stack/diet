package com.mealsnap.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One food or drink on the plate, with its own portion and nutrition. */
@Serializable
data class FoodItem(
    val name: String = "",
    val portion: String = "",
    val calories: Int = 0,
    @SerialName("protein_g") val proteinG: Int = 0,
    @SerialName("carbs_g") val carbsG: Int = 0,
    @SerialName("fat_g") val fatG: Int = 0,
)

@Serializable
data class FoodAnalysis(
    @SerialName("is_food") val isFood: Boolean = true,
    @SerialName("dish_name") val dishName: String = "",
    val cuisine: String = "",
    val items: List<FoodItem> = emptyList(),
    @SerialName("total_calories") val totalCalories: Int = 0,
    @SerialName("protein_g") val proteinG: Int = 0,
    @SerialName("carbs_g") val carbsG: Int = 0,
    @SerialName("fat_g") val fatG: Int = 0,
    @SerialName("fiber_g") val fiberG: Int = 0,
    @SerialName("sugar_g") val sugarG: Int = 0,
    @SerialName("sodium_mg") val sodiumMg: Int = 0,
    @SerialName("visual_cues") val visualCues: List<String> = emptyList(),
    val confidence: String = "medium",
    val notes: String = "",
)

object FoodAnalysisParser {
    private const val MAX_CALORIES = 10_000
    private const val MAX_MACRO = 1_000
    private const val MAX_SODIUM = 20_000
    private const val MAX_CUES = 6

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

        val items = parsed.items.map {
            it.copy(
                name = it.name.trim(),
                portion = it.portion.trim(),
                calories = it.calories.coerceIn(0, MAX_CALORIES),
                proteinG = it.proteinG.coerceIn(0, MAX_MACRO),
                carbsG = it.carbsG.coerceIn(0, MAX_MACRO),
                fatG = it.fatG.coerceIn(0, MAX_MACRO),
            )
        }
        var total = parsed.totalCalories.coerceIn(0, MAX_CALORIES)
        if (total == 0 && items.isNotEmpty()) total = items.sumOf { it.calories }.coerceAtMost(MAX_CALORIES)

        return parsed.copy(
            dishName = parsed.dishName.trim(),
            cuisine = parsed.cuisine.trim(),
            items = items,
            totalCalories = total,
            proteinG = parsed.proteinG.coerceIn(0, MAX_MACRO),
            carbsG = parsed.carbsG.coerceIn(0, MAX_MACRO),
            fatG = parsed.fatG.coerceIn(0, MAX_MACRO),
            fiberG = parsed.fiberG.coerceIn(0, MAX_MACRO),
            sugarG = parsed.sugarG.coerceIn(0, MAX_MACRO),
            sodiumMg = parsed.sodiumMg.coerceIn(0, MAX_SODIUM),
            visualCues = parsed.visualCues.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_CUES),
            confidence = parsed.confidence.lowercase().takeIf { it in setOf("high", "medium", "low") } ?: "medium",
            notes = parsed.notes.trim(),
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
