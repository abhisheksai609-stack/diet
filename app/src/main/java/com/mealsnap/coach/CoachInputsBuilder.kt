package com.mealsnap.coach

import com.mealsnap.data.DayStats
import com.mealsnap.data.Meal
import com.mealsnap.data.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Gathers meals, device data and weigh-ins into the single [CoachInputs] the coach works from. */
object CoachInputsBuilder {

    fun build(
        profile: Profile,
        goalKcal: Int,
        meals: List<Meal>,
        health: HealthSnapshot?,
        weights: List<WeightPoint>,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CoachInputs {
        val today = now.toLocalDate()
        val byDay = meals.groupBy { DayStats.dateOf(it.createdAt, zone) }

        val week = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            DayNutrition(date, totals(byDay[date].orEmpty().sortedBy { it.createdAt }))
        }
        return CoachInputs(
            profile = profile,
            goalKcal = goalKcal,
            macros = profile.weightKg?.let { BodyMetrics.macroPlan(goalKcal, it, profile.goal) },
            now = now,
            today = week.last().totals,
            nutritionWeek = week,
            health = health,
            weights = weights,
        )
    }

    private fun totals(meals: List<Meal>) = MealTotals(
        calories = meals.sumOf { it.calories },
        proteinG = meals.sumOf { it.proteinG },
        carbsG = meals.sumOf { it.carbsG },
        fatG = meals.sumOf { it.fatG },
        fiberG = meals.sumOf { it.fiberG },
        mealCount = meals.size,
        mealLines = meals.map { "${it.type.label}: ${it.name} (${it.calories} kcal)" },
    )

    /**
     * Combines weigh-ins typed in the app with ones from Health Connect (for example from a smart scale), one
     * value per day (the app's own entry wins), oldest first.
     */
    fun mergeWeights(local: List<WeightEntry>, fromHealthConnect: List<WeightPoint>, zone: ZoneId = ZoneId.systemDefault()): List<WeightPoint> {
        val byDate = sortedMapOf<LocalDate, Double>()
        fromHealthConnect.forEach { byDate[it.date] = it.kg }
        local.sortedBy { it.timeMillis }.forEach { byDate[Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate()] = it.kg }
        return byDate.map { WeightPoint(it.key, it.value) }
    }
}
