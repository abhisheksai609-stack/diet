package com.mealsnap

import com.mealsnap.data.DayStats
import com.mealsnap.data.Meal
import com.mealsnap.data.MealType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayStatsTest {
    private val zone = ZoneId.of("Europe/London")

    private fun meal(date: LocalDate, hour: Int, kcal: Int, id: Long = 0) = Meal(
        id = id,
        createdAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        mealType = MealType.LUNCH.name,
        name = "m$id",
        calories = kcal,
        proteinG = 10,
        carbsG = 20,
        fatG = 5,
    )

    @Test
    fun groupsMealsByLocalDayNewestFirstAndSumsTotals() {
        val d1 = LocalDate.of(2026, 11, 10)
        val d2 = LocalDate.of(2026, 11, 11)
        val days = DayStats.groupByDay(
            listOf(
                meal(d1, 19, 700, 1),
                meal(d2, 8, 300, 2),
                meal(d1, 8, 400, 3),
            ),
            zone,
        )

        assertEquals(listOf(d2, d1), days.map { it.date })
        assertEquals(1100, days[1].calories)
        assertEquals(listOf(3L, 1L), days[1].meals.map { it.id }) // oldest first inside a day
        assertEquals(20, days[1].proteinG)
    }

    @Test
    fun lateNightMealBelongsToItsOwnLocalDay() {
        val d = LocalDate.of(2026, 11, 10)
        val days = DayStats.groupByDay(listOf(meal(d, 23, 250, 1), meal(d.plusDays(1), 0, 150, 2)), zone)
        assertEquals(listOf(d.plusDays(1), d), days.map { it.date })
        assertEquals(250, days[1].calories)
    }

    @Test
    fun lastDaysZeroFillsGapsAndEndsToday() {
        val today = LocalDate.of(2026, 11, 16)
        val days = DayStats.groupByDay(listOf(meal(today, 12, 500), meal(today.minusDays(2), 12, 800)), zone)

        val chart = DayStats.lastDays(days, today, 7)

        assertEquals(7, chart.size)
        assertEquals(today.minusDays(6), chart.first().first)
        assertEquals(today to 500, chart.last())
        assertEquals(800, chart[4].second)
        assertEquals(0, chart[5].second)
    }

    @Test
    fun startOfDayHandlesDaylightSavingDay() {
        // 2026-03-29 is a 23-hour day in London
        val day = LocalDate.of(2026, 3, 29)
        val length = DayStats.startOfDayMillis(day.plusDays(1), zone) - DayStats.startOfDayMillis(day, zone)
        assertEquals(23 * 60 * 60 * 1000L, length)
    }

    @Test
    fun progressIsClampedAndSafeForZeroGoal() {
        assertEquals(0.5f, DayStats.progress(1000, 2000), 0.0001f)
        assertEquals(1f, DayStats.progress(3000, 2000), 0.0001f)
        assertEquals(0f, DayStats.progress(100, 0), 0.0001f)
    }

    @Test
    fun mealTypeFollowsTimeOfDay() {
        assertEquals(MealType.BREAKFAST, DayStats.mealTypeForHour(8))
        assertEquals(MealType.LUNCH, DayStats.mealTypeForHour(13))
        assertEquals(MealType.SNACK, DayStats.mealTypeForHour(16))
        assertEquals(MealType.DINNER, DayStats.mealTypeForHour(19))
        assertEquals(MealType.SNACK, DayStats.mealTypeForHour(2))
    }
}
