package com.mealsnap.coach

import java.time.LocalDate
import java.time.LocalDateTime

/** Everything read from the phone's Health Connect store for one calendar day. Null means "no data". */
data class HealthDay(
    val date: LocalDate,
    val steps: Long? = null,
    val activeKcal: Double? = null,
    val totalKcal: Double? = null,
    val restingHr: Int? = null,
    val avgHr: Int? = null,
    val minHr: Int? = null,
    val maxHr: Int? = null,
    /** Sleep ending on this day (last night's sleep, for today). */
    val sleepMinutes: Long? = null,
    val distanceM: Double? = null,
    val exerciseMinutes: Long? = null,
) {
    val hasAnything: Boolean
        get() = listOf(steps, activeKcal, totalKcal, restingHr, avgHr, sleepMinutes, distanceM, exerciseMinutes).any { it != null }
}

data class HealthSnapshot(
    val today: HealthDay,
    /** Oldest first, ending with today. */
    val week: List<HealthDay>,
    val latestWeightKg: Double? = null,
    val hrvMs: Double? = null,
    val spo2Percent: Double? = null,
)

data class WeightPoint(val date: LocalDate, val kg: Double)

data class MealTotals(
    val calories: Int = 0,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
    val fiberG: Int = 0,
    val mealCount: Int = 0,
    /** "Breakfast: Avocado toast (420 kcal)" lines, for the AI to refer to. */
    val mealLines: List<String> = emptyList(),
)

data class DayNutrition(val date: LocalDate, val totals: MealTotals)

/** All the facts the coach (rule-based or Gemini) works from. */
data class CoachInputs(
    val profile: Profile,
    val goalKcal: Int,
    val macros: MacroPlan?,
    val now: LocalDateTime,
    val today: MealTotals,
    /** Oldest first, ending with today; days with no meals are included with zero totals. */
    val nutritionWeek: List<DayNutrition>,
    val health: HealthSnapshot?,
    /** Oldest first. */
    val weights: List<WeightPoint>,
)

enum class TipKind { GOOD, INFO, WATCH }

data class Tip(val title: String, val body: String, val kind: TipKind = TipKind.INFO)
