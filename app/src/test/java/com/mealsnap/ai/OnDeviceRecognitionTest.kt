package com.mealsnap.ai

import com.mealsnap.nutrition.NutritionDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceRecognitionTest {

    // ---- classifier -------------------------------------------------------------------------

    @Test
    fun labelsParseWithTheIdAsTheIndex() {
        val labels = ClassifierLabels.parse("id,name\n0,__background__\n1,Chaudin\n2,\"Apple pie\"\n3,/g/122qyvy7\n")
        assertEquals(listOf("__background__", "Chaudin", "Apple pie", "/g/122qyvy7"), labels)
        assertFalse(ClassifierLabels.isDishName("__background__"))
        assertFalse(ClassifierLabels.isDishName("/g/122qyvy7"))
        assertTrue(ClassifierLabels.isDishName("Apple pie"))
    }

    @Test
    fun topPredictionsSkipBackgroundAndUnnamedLabels() {
        val labels = listOf("__background__", "Pizza", "/g/abc", "Hamburger", "Sushi")
        val scores = floatArrayOf(0.9f, 0.1f, 0.8f, 0.5f, 0.2f)

        val top = FoodClassifier.topPredictions(scores, labels, topK = 2)

        assertEquals(listOf("Hamburger", "Sushi"), top.map { it.label })
    }

    @Test
    fun theBundledModelFilesAreWhatTheCodeExpects() {
        val dir = java.io.File("src/main/assets")
        val labels = ClassifierLabels.parse(java.io.File(dir, FoodClassifier.LABELS_ASSET).readText())
        assertEquals(2024, labels.size) // model output size: background + 2023 dishes
        assertEquals("__background__", labels[0])
        assertTrue(labels.count { ClassifierLabels.isDishName(it) } > 1500)
        assertTrue(java.io.File(dir, FoodClassifier.MODEL_ASSET).length() > 10_000_000)
        // Every dish the classifier can name maps to *something* without crashing.
        labels.filter { ClassifierLabels.isDishName(it) }.forEach { NutritionDb.match(it) }
    }

    // Dishes people really photograph, spelled exactly as in the model's label list.
    private val commonLabels = listOf(
        "Hamburger", "Cheeseburger", "Veggie burger", "Hot dog", "Sushi", "Pad thai", "Fried rice", "Idli", "Samosa",
        "Butter chicken", "Chicken curry", "Mutton curry", "Green curry", "Hyderabadi biryani", "Aloo paratha",
        "Dal makhani", "Paneer tikka", "Palak paneer", "Chana masala", "Rajma", "Khichdi", "Upma", "Pakora", "Dhokla",
        "Gulab jamun", "Jalebi", "Kheer", "Tandoori chicken", "Raita", "Lasagne", "Ramen", "Udon", "Pancake", "Waffle",
        "Omelette", "Cheesecake", "Apple pie", "Greek salad", "Caprese salad", "Spaghetti", "Burrito", "Falafel",
        "Hummus", "Dumpling", "Fried chicken", "Sandwich", "Club sandwich", "Taco", "Quesadilla", "Nachos", "Pho",
        "Chow mein", "Cupcake", "Tempura", "Bibimbap", "Kimchi", "Macaroni and cheese", "Meatball", "Risotto", "Paella",
        "Baklava", "Gyro", "Shawarma", "French toast", "Onion ring", "Mashed potato", "Baked potato", "Coleslaw",
        "Miso soup", "Carbonara", "Corn dog", "Chicken nugget", "Tonkatsu", "Empanada", "New York-style pizza",
        "Neapolitan pizza", "Cobb salad", "Potato salad", "Beef noodle soup", "Mughlai paratha",
    )

    @Test
    fun commonDishesMapToRealFoodsNotAGenericPlate() {
        val labels = ClassifierLabels.parse(java.io.File("src/main/assets/${FoodClassifier.LABELS_ASSET}").readText())
        val lower = labels.map { it.lowercase() }.toSet()

        val missingFromModel = commonLabels.filter { it.lowercase() !in lower }
        assertTrue("these aren't in the model's label list (fix the test): $missingFromModel", missingFromModel.isEmpty())

        val generic = commonLabels.filter { NutritionDb.match(it).kind == com.mealsnap.nutrition.Match.Kind.GENERIC }
        assertTrue("common dishes with no nutrition match: $generic", generic.isEmpty())
    }

    @Test
    fun aConfidentPredictionBecomesALoggedMealWithAlternatives() {
        val rec = ClassifierMapper.toRecognition(
            listOf(Prediction("Hamburger", 0.52f), Prediction("Cheeseburger", 0.30f), Prediction("Veggie burger", 0.07f)),
        )!!

        assertEquals(Engine.CLASSIFIER, rec.engine)
        assertEquals("Hamburger", rec.analysis.dishName)
        assertEquals(455, rec.analysis.totalCalories)
        assertEquals("medium", rec.analysis.confidence)
        assertNotNull(rec.dish)
        assertEquals(listOf("Cheeseburger", "Veggie burger"), rec.alternatives.map { it.label })
        assertTrue(rec.analysis.visualCues.first().contains("52%"))
        assertTrue(rec.analysis.notes.contains("Hamburger"))
    }

    @Test
    fun anUncertainModelReturnsNothing() {
        assertNull(ClassifierMapper.toRecognition(listOf(Prediction("Pizza", 0.06f), Prediction("Sushi", 0.05f))))
        assertNull(ClassifierMapper.toRecognition(emptyList()))
        assertNull(ClassifierMapper.toRecognition(listOf(Prediction("/g/122qyvy7", 0.9f))))
    }

    @Test
    fun anUnknownDishIsFlaggedAsLowConfidence() {
        val rec = ClassifierMapper.toRecognition(listOf(Prediction("Chaudin", 0.45f)))!!
        assertEquals("Chaudin", rec.analysis.dishName)
        assertEquals("low", rec.analysis.confidence)
        assertTrue(rec.analysis.notes.contains("isn't in the built-in table"))
    }

    @Test
    fun aKnownRunnerUpBeatsAnUnknownTopLabel() {
        val rec = ClassifierMapper.toRecognition(listOf(Prediction("Chaudin", 0.30f), Prediction("Pizza", 0.25f)))!!
        assertEquals("Pizza", rec.analysis.dishName)
    }

    // ---- Gemini Nano --------------------------------------------------------------------------

    @Test
    fun nanoReplyParsesEvenWithChatterAroundIt() {
        val reply = """Sure! Here you go:
            ```json
            {"is_food": true, "dish_name": "chicken biryani", "cuisine": "Indian",
             "items": [{"name": "chicken biryani", "grams": 300}, {"name": "raita", "grams": "80 g"}]}
            ```"""

        val dish = NanoParser.parse(reply)!!

        assertTrue(dish.isFood)
        assertEquals("chicken biryani", dish.dishName)
        assertEquals(listOf(300, 80), dish.items.map { it.grams })
    }

    @Test
    fun nanoSaysNoFoodOrRamblesNonsense() {
        assertFalse(NanoParser.parse("{\"is_food\": false}")!!.isFood)
        assertNull(NanoParser.parse("I think this is a pizza."))
        assertNull(NanoParser.parse("{ not json at all }"))
    }

    @Test
    fun nanoItemsBecomeAMealFromTheNutritionTable() {
        val rec = NanoMapper.toRecognition(
            NanoDish(
                "Chicken biryani with raita", "Indian", true,
                listOf(NanoItem("chicken biryani", 300, null), NanoItem("raita", 80, null)),
            ),
        )!!

        assertEquals(Engine.NANO, rec.engine)
        assertEquals("Chicken biryani with raita", rec.analysis.dishName)
        assertEquals(2, rec.analysis.items.size)
        // 300 g biryani at 170 + 80 g raita at 55 = 554 kcal
        assertEquals(555, rec.analysis.totalCalories)
        assertNotNull(rec.dish)
    }

    @Test
    fun nanosOwnCalorieGuessIsUsedForFoodsTheTableDoesNotKnow() {
        val rec = NanoMapper.toRecognition(
            NanoDish("Mystery dish", "", true, listOf(NanoItem("zorblax", 200, 360))),
        )!!
        assertEquals(360, rec.analysis.totalCalories)
    }

    @Test
    fun aNanoDishWithoutItemsStillWorksFromItsName() {
        val rec = NanoMapper.toRecognition(NanoDish("Masala dosa", "Indian", true, emptyList()))!!
        assertEquals(400, rec.analysis.totalCalories)
        assertNull(NanoMapper.toRecognition(NanoDish("", "", true, emptyList())))
    }
}
