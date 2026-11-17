package com.mealsnap.coach

import com.mealsnap.data.Meal
import com.mealsnap.data.MealType
import com.mealsnap.data.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CoachInputsBuilderTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 11, 17, 19, 0)
    private val today = now.toLocalDate()

    private fun meal(date: LocalDate, hour: Int, name: String, kcal: Int, protein: Int, type: MealType = MealType.LUNCH) = Meal(
        createdAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), mealType = type.name, name = name,
        calories = kcal, proteinG = protein, carbsG = 10, fatG = 5, fiberG = 2,
    )

    private val profile = Profile(heightCm = 170.0, weightKg = 70.0, birthYear = 1990, sex = Sex.FEMALE, goal = Goal.LOSE)

    @Test
    fun todaysMealsAreSummedAndListedInTimeOrder() {
        val meals = listOf(
            meal(today, 13, "Chicken biryani", 510, 24, MealType.LUNCH),
            meal(today, 8, "Idli", 160, 5, MealType.BREAKFAST),
            meal(today.minusDays(1), 12, "Dal rice", 420, 14),
        )

        val i = CoachInputsBuilder.build(profile, 1700, meals, null, emptyList(), now, zone)

        assertEquals(670, i.today.calories)
        assertEquals(29, i.today.proteinG)
        assertEquals(2, i.today.mealCount)
        assertEquals(listOf("Breakfast: Idli (160 kcal)", "Lunch: Chicken biryani (510 kcal)"), i.today.mealLines)
        assertEquals(1700, i.goalKcal)
    }

    @Test
    fun theWeekHasSevenDaysEndingTodayWithEmptyDaysIncluded() {
        val i = CoachInputsBuilder.build(profile, 1700, listOf(meal(today.minusDays(2), 12, "Dal", 400, 15)), null, emptyList(), now, zone)

        assertEquals(7, i.nutritionWeek.size)
        assertEquals(today, i.nutritionWeek.last().date)
        assertEquals(today.minusDays(6), i.nutritionWeek.first().date)
        assertEquals(400, i.nutritionWeek[4].totals.calories)
        assertEquals(0, i.nutritionWeek[5].totals.mealCount)
    }

    @Test
    fun macroTargetsComeFromTheProfileWeight() {
        val i = CoachInputsBuilder.build(profile, 1700, emptyList(), null, emptyList(), now, zone)
        assertEquals(126, i.macros!!.proteinG) // 1.8 g x 70 kg
        assertTrue(CoachInputsBuilder.build(Profile(), 1700, emptyList(), null, emptyList(), now, zone).macros == null)
    }

    @Test
    fun weighInsAreMergedOnePerDayAndTheAppsOwnEntryWins() {
        fun entry(date: LocalDate, kg: Double) = WeightEntry(timeMillis = date.atTime(7, 0).atZone(zone).toInstant().toEpochMilli(), kg = kg)
        val merged = CoachInputsBuilder.mergeWeights(
            local = listOf(entry(today, 70.2), entry(today.minusDays(7), 71.0)),
            fromHealthConnect = listOf(WeightPoint(today, 69.9), WeightPoint(today.minusDays(3), 70.6)),
            zone = zone,
        )

        assertEquals(listOf(today.minusDays(7), today.minusDays(3), today), merged.map { it.date })
        assertEquals(70.2, merged.last().kg, 0.001) // typed in the app beats Health Connect on the same day
        assertEquals(70.6, merged[1].kg, 0.001)
    }
}
