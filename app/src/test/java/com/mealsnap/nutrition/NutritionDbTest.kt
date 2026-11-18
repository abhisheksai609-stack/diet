package com.mealsnap.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NutritionDbTest {

    private val alcohol = setOf("Beer", "Wine", "Cocktail")

    @Test
    fun everyRowParsesAndNamesAreUnique() {
        val entries = NutritionDb.entries
        assertTrue("table should be sizeable, was ${entries.size}", entries.size >= 200)
        val names = entries.map { it.name.lowercase() }
        assertEquals("duplicate names: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}", names.size, names.toSet().size)
        assertEquals(2, entries.count { it.generic })
    }

    @Test
    fun numbersAreSaneForEveryFood() {
        for (e in NutritionDb.entries) {
            assertTrue("${e.name}: kcal", e.kcal in 0.0..900.0)
            assertTrue("${e.name}: serving", e.servingG in 5..700)
            assertTrue("${e.name}: macros", listOf(e.protein, e.carbs, e.fat, e.fiber, e.sugar, e.sodiumMg).all { it >= 0 })
            assertTrue("${e.name}: macros can't exceed 100 g", e.protein + e.carbs + e.fat <= 100.5)
            assertTrue("${e.name}: sugar is part of carbs", e.sugar <= e.carbs + 0.5)
            assertTrue("${e.name}: fibre is part of carbs", e.fiber <= e.carbs + 0.5)
            val perServing = e.kcal * e.servingG / 100
            assertTrue("${e.name}: a serving of ${perServing.toInt()} kcal is implausible", perServing in 0.0..1600.0)
        }
    }

    @Test
    fun caloriesAgreeWithMacros() {
        // kcal should be close to 4 x protein + 4 x carbs + 9 x fat (alcohol is the known exception).
        val off = NutritionDb.entries.filter { it.name !in alcohol }.mapNotNull { e ->
            val estimate = 4 * e.protein + 4 * e.carbs + 9 * e.fat
            val allowed = maxOf(0.2 * e.kcal, 15.0)
            if (abs(e.kcal - estimate) > allowed) "${e.name}: table says ${e.kcal} kcal, macros give ${estimate.toInt()}" else null
        }
        assertTrue("inconsistent rows:\n" + off.joinToString("\n"), off.isEmpty())
    }

    @Test
    fun exactLabelsFindTheirFood() {
        assertMatch("Hamburger", "Hamburger", Match.Kind.EXACT)
        assertMatch("Cheeseburger", "Cheeseburger", Match.Kind.EXACT)
        assertMatch("Veggie burger", "Veggie burger", Match.Kind.EXACT)
        assertMatch("Pad thai", "Pad thai", Match.Kind.EXACT)
        assertMatch("Masala dosa", "Masala dosa", Match.Kind.EXACT)
        assertMatch("Samosa", "Samosa", Match.Kind.EXACT)
        assertMatch("Chicken biryani", "Biryani", Match.Kind.EXACT)
        assertMatch("Spaghetti", "Pasta", Match.Kind.EXACT)
        assertMatch("Tikka masala", "Chicken tikka masala", Match.Kind.EXACT)
        assertMatch("Pizza Margherita", "Pizza", Match.Kind.EXACT)
    }

    @Test
    fun pluralsAndWordOrderDontMatter() {
        assertMatch("Dumplings", "Dumplings", Match.Kind.EXACT)
        assertMatch("French fry", "French fries", Match.Kind.EXACT)
        assertMatch("tacos", "Taco", Match.Kind.EXACT)
    }

    @Test
    fun partialAndCategoryMatchesAreUsedForUnknownDishes() {
        assertMatch("Chocolate lava cake", "Cake", Match.Kind.PARTIAL)
        assertMatch("Lamb rogan josh with naan", "Mutton curry", Match.Kind.PARTIAL)
        val soup = NutritionDb.match("Cream of celery chowder")
        assertEquals(Match.Kind.CATEGORY, soup.kind)
        assertEquals("Tomato soup", soup.entry.name)
    }

    @Test
    fun shortCategoryWordsMustBeWholeWords() {
        // "veggie" contains "egg"; it must not be treated as an egg dish.
        assertFalse(NutritionDb.match("Veggie surprise").entry.name == "Boiled egg")
    }

    @Test
    fun unknownDishesFallBackToAGenericPlate() {
        val m = NutritionDb.match("Chaudin")
        assertEquals(Match.Kind.GENERIC, m.kind)
        assertTrue(m.entry.generic)
        assertEquals(Match.Kind.GENERIC, NutritionDb.match("").kind)
    }

    @Test
    fun searchSuggestsWhileTyping() {
        assertEquals("Chicken curry", NutritionDb.search("chicken cur").first().name)
        assertTrue(NutritionDb.search("biry").any { it.name == "Biryani" })
        assertTrue(NutritionDb.search("PIZ").any { it.name == "Pizza" })
        assertTrue(NutritionDb.search("").isEmpty())
        assertTrue(NutritionDb.search("zzzzqq").isEmpty())
        assertTrue("generic fallbacks are never suggested", NutritionDb.search("dish").none { it.generic })
        assertEquals(3, NutritionDb.search("a", limit = 3).size)
    }

    @Test
    fun exactNameRanksFirstInSearch() {
        assertEquals("Pizza", NutritionDb.search("pizza").first().name)
        assertEquals("Dosa", NutritionDb.search("dosa").first().name)
    }

    @Test
    fun dishMathScalesWithPortion() {
        val burger = NutritionDb.byName("Hamburger")!!
        val dish = Dish.single(burger) // 180 g at 254 kcal/100 g
        val one = dish.toAnalysis("high", "")
        assertEquals(455, one.totalCalories) // 457.2 rounded to the nearest 5
        assertEquals(1, one.items.size)
        assertEquals("about 180 g (1 burger)", one.items.single().portion)

        val double = dish.scaled(2f).toAnalysis("high", "")
        assertEquals(915, double.totalCalories)
        assertEquals("about 360 g", double.items.single().portion)

        val half = dish.scaled(0.5f).toAnalysis("high", "")
        assertEquals(230, half.totalCalories)
    }

    @Test
    fun multiItemDishSumsItsParts() {
        val rice = DishItem(NutritionDb.byName("White rice")!!, 200)
        val dal = DishItem(NutritionDb.byName("Dal")!!, 150)
        val analysis = Dish("Dal rice", items = listOf(rice, dal)).toAnalysis("medium", "n")
        assertEquals(2, analysis.items.size)
        // 200 g rice at 130 + 150 g dal at 105 = 417.5 kcal, shown to the nearest 5
        assertEquals(420, analysis.totalCalories)
        assertTrue(abs(analysis.items.sumOf { it.proteinG } - analysis.proteinG) <= 1)
    }

    private fun assertMatch(label: String, expectedFood: String, kind: Match.Kind) {
        val m = NutritionDb.match(label)
        assertNotNull(m)
        assertEquals("food for \"$label\"", expectedFood, m.entry.name)
        assertEquals("kind for \"$label\"", kind, m.kind)
    }
}
