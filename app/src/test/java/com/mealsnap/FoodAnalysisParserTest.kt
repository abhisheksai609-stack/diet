package com.mealsnap

import com.mealsnap.ai.FoodAnalysisParser
import com.mealsnap.ai.FoodItem
import com.mealsnap.ai.FoodItems
import com.mealsnap.data.ImageMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodAnalysisParserTest {

    @Test
    fun parsesStructuredReply() {
        val json = """
            {"is_food": true, "meal_name": "Chicken rice bowl",
             "items": [{"name": "Grilled chicken", "portion": "~150 g", "calories": 250},
                       {"name": "White rice", "portion": "1 cup", "calories": 200}],
             "total_calories": 450, "protein_g": 40, "carbs_g": 50, "fat_g": 8,
             "confidence": "high", "notes": "Assumed no added oil."}
        """.trimIndent()

        val result = FoodAnalysisParser.parse(json)

        assertTrue(result.isFood)
        assertEquals("Chicken rice bowl", result.mealName)
        assertEquals(2, result.items.size)
        assertEquals(450, result.totalCalories)
        assertEquals(40, result.proteinG)
        assertEquals("high", result.confidence)
    }

    @Test
    fun toleratesMarkdownFenceAndUnknownKeys() {
        val text = "```json\n{\"is_food\":true,\"meal_name\":\"Toast\",\"items\":[],\"total_calories\":120," +
            "\"protein_g\":4,\"carbs_g\":20,\"fat_g\":2,\"confidence\":\"medium\",\"notes\":\"\",\"extra\":1}\n```"

        val result = FoodAnalysisParser.parse(text)

        assertEquals("Toast", result.mealName)
        assertEquals(120, result.totalCalories)
    }

    @Test
    fun fillsTotalFromItemsWhenModelReturnsZero() {
        val json = """{"is_food":true,"meal_name":"Snack","items":[{"name":"a","portion":"","calories":100},
            {"name":"b","portion":"","calories":60}],"total_calories":0,"protein_g":0,"carbs_g":0,"fat_g":0,
            "confidence":"low","notes":""}""".trimIndent()

        assertEquals(160, FoodAnalysisParser.parse(json).totalCalories)
    }

    @Test
    fun clampsAbsurdValuesAndUnknownConfidence() {
        val json = """{"is_food":true,"meal_name":"x","items":[],"total_calories":999999,"protein_g":-5,
            "carbs_g":5000,"fat_g":1,"confidence":"super sure","notes":""}""".trimIndent()

        val result = FoodAnalysisParser.parse(json)

        assertEquals(10_000, result.totalCalories)
        assertEquals(0, result.proteinG)
        assertEquals(1_000, result.carbsG)
        assertEquals("medium", result.confidence)
    }

    @Test
    fun notFoodReplyIsFlagged() {
        val json = """{"is_food":false,"meal_name":"","items":[],"total_calories":0,"protein_g":0,"carbs_g":0,
            "fat_g":0,"confidence":"high","notes":"A desk"}""".trimIndent()

        assertFalse(FoodAnalysisParser.parse(json).isFood)
    }

    @Test
    fun foodItemsRoundTripAndSurviveGarbage() {
        val items = listOf(FoodItem("Apple", "1 medium", 95), FoodItem("Peanut butter", "1 tbsp", 95))

        assertEquals(items, FoodItems.decode(FoodItems.encode(items)))
        assertEquals(emptyList<FoodItem>(), FoodItems.decode("not json"))
        assertEquals(emptyList<FoodItem>(), FoodItems.decode(null))
    }
}

class ImageMathTest {
    @Test
    fun scalesLongestSideDownKeepingAspectRatio() {
        assertEquals(1568 to 1176, ImageMath.scaledSize(4000, 3000, 1568))
        assertEquals(1176 to 1568, ImageMath.scaledSize(3000, 4000, 1568))
    }

    @Test
    fun neverUpscales() {
        assertEquals(800 to 600, ImageMath.scaledSize(800, 600, 1568))
    }

    @Test
    fun sampleSizeKeepsLongEdgeAtOrAboveTarget() {
        assertEquals(1, ImageMath.sampleSize(1600, 1200, 1568))
        assertEquals(2, ImageMath.sampleSize(4000, 3000, 1568))
        assertEquals(4, ImageMath.sampleSize(8160, 6144, 1568)) // Pixel 50 MP mode
    }
}
