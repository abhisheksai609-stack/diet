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

    private val fullReply = """
        {"is_food": true, "dish_name": "Chicken rice bowl", "cuisine": "Asian fusion",
         "items": [
           {"name": "Grilled chicken", "portion": "~150 g", "calories": 250, "protein_g": 38, "carbs_g": 0, "fat_g": 10},
           {"name": "White rice", "portion": "1 cup", "calories": 200, "protein_g": 4, "carbs_g": 44, "fat_g": 0}],
         "total_calories": 450, "protein_g": 42, "carbs_g": 44, "fat_g": 10,
         "fiber_g": 2, "sugar_g": 3, "sodium_mg": 620,
         "visual_cues": ["Standard dinner plate", "Fork for scale"],
         "confidence": "high", "notes": "Assumed no added oil."}
    """.trimIndent()

    @Test
    fun parsesDetailedStructuredReply() {
        val result = FoodAnalysisParser.parse(fullReply)

        assertTrue(result.isFood)
        assertEquals("Chicken rice bowl", result.dishName)
        assertEquals("Asian fusion", result.cuisine)
        assertEquals(2, result.items.size)
        assertEquals(38, result.items[0].proteinG)
        assertEquals("1 cup", result.items[1].portion)
        assertEquals(450, result.totalCalories)
        assertEquals(42, result.proteinG)
        assertEquals(2, result.fiberG)
        assertEquals(620, result.sodiumMg)
        assertEquals(listOf("Standard dinner plate", "Fork for scale"), result.visualCues)
        assertEquals("high", result.confidence)
    }

    @Test
    fun toleratesMarkdownFenceAndUnknownKeys() {
        val text = "```json\n{\"is_food\":true,\"dish_name\":\"Toast\",\"cuisine\":\"\",\"items\":[],\"total_calories\":120," +
            "\"protein_g\":4,\"carbs_g\":20,\"fat_g\":2,\"fiber_g\":1,\"sugar_g\":2,\"sodium_mg\":150," +
            "\"visual_cues\":[],\"confidence\":\"medium\",\"notes\":\"\",\"extra\":1}\n```"

        val result = FoodAnalysisParser.parse(text)

        assertEquals("Toast", result.dishName)
        assertEquals(120, result.totalCalories)
    }

    @Test
    fun fillsTotalFromItemsWhenModelReturnsZero() {
        val json = """{"is_food":true,"dish_name":"Snack","cuisine":"","items":[
            {"name":"a","portion":"","calories":100,"protein_g":1,"carbs_g":1,"fat_g":1},
            {"name":"b","portion":"","calories":60,"protein_g":1,"carbs_g":1,"fat_g":1}],
            "total_calories":0,"protein_g":0,"carbs_g":0,"fat_g":0,"fiber_g":0,"sugar_g":0,"sodium_mg":0,
            "visual_cues":[],"confidence":"low","notes":""}""".trimIndent()

        assertEquals(160, FoodAnalysisParser.parse(json).totalCalories)
    }

    @Test
    fun clampsAbsurdValuesAndUnknownConfidence() {
        val json = """{"is_food":true,"dish_name":"x","cuisine":"","items":[],"total_calories":999999,"protein_g":-5,
            "carbs_g":5000,"fat_g":1,"fiber_g":-1,"sugar_g":2,"sodium_mg":999999,
            "visual_cues":[],"confidence":"super sure","notes":""}""".trimIndent()

        val result = FoodAnalysisParser.parse(json)

        assertEquals(10_000, result.totalCalories)
        assertEquals(0, result.proteinG)
        assertEquals(1_000, result.carbsG)
        assertEquals(0, result.fiberG)
        assertEquals(20_000, result.sodiumMg)
        assertEquals("medium", result.confidence)
    }

    @Test
    fun trimsTextAndCapsVisualCues() {
        val json = """{"is_food":true,"dish_name":"  Pad thai ","cuisine":" Thai ","items":[],"total_calories":600,
            "protein_g":20,"carbs_g":80,"fat_g":20,"fiber_g":4,"sugar_g":10,"sodium_mg":900,
            "visual_cues":[" a ","","b","c","d","e","f","g"],"confidence":"medium","notes":"  note "}""".trimIndent()

        val result = FoodAnalysisParser.parse(json)

        assertEquals("Pad thai", result.dishName)
        assertEquals("Thai", result.cuisine)
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), result.visualCues)
        assertEquals("note", result.notes)
    }

    @Test
    fun notFoodReplyIsFlagged() {
        val json = """{"is_food":false,"dish_name":"","cuisine":"","items":[],"total_calories":0,"protein_g":0,
            "carbs_g":0,"fat_g":0,"fiber_g":0,"sugar_g":0,"sodium_mg":0,"visual_cues":[],
            "confidence":"high","notes":"A desk"}""".trimIndent()

        assertFalse(FoodAnalysisParser.parse(json).isFood)
    }

    @Test
    fun foodItemsRoundTripAndSurviveGarbage() {
        val items = listOf(
            FoodItem("Apple", "1 medium", 95, 0, 25, 0),
            FoodItem("Peanut butter", "1 tbsp", 95, 4, 3, 8),
        )

        assertEquals(items, FoodItems.decode(FoodItems.encode(items)))
        assertEquals(emptyList<FoodItem>(), FoodItems.decode("not json"))
        assertEquals(emptyList<FoodItem>(), FoodItems.decode(null))
    }

    @Test
    fun itemsStoredByTheFirstVersionStillLoad() {
        // v1 stored only name / portion / calories.
        val old = """[{"name":"Toast","portion":"2 slices","calories":160}]"""

        val items = FoodItems.decode(old)

        assertEquals(1, items.size)
        assertEquals(160, items[0].calories)
        assertEquals(0, items[0].proteinG)
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
