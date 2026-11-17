package com.mealsnap.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class MacroTargets(val proteinG: Int, val carbsG: Int, val fatG: Int)

data class DaySummary(val date: LocalDate, val meals: List<Meal>) {
    val calories: Int get() = meals.sumOf { it.calories }
    val proteinG: Int get() = meals.sumOf { it.proteinG }
    val carbsG: Int get() = meals.sumOf { it.carbsG }
    val fatG: Int get() = meals.sumOf { it.fatG }
}

/** Pure helpers for turning a flat list of meals into per-day numbers. */
object DayStats {

    fun dateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDayMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Newest day first; meals inside a day are oldest first. */
    fun groupByDay(meals: List<Meal>, zone: ZoneId = ZoneId.systemDefault()): List<DaySummary> =
        meals.groupBy { dateOf(it.createdAt, zone) }
            .map { (date, dayMeals) -> DaySummary(date, dayMeals.sortedBy { it.createdAt }) }
            .sortedByDescending { it.date }

    /** Calories for each of the last [count] days ending today, oldest first, zero-filled. */
    fun lastDays(summaries: List<DaySummary>, today: LocalDate, count: Int): List<Pair<LocalDate, Int>> {
        val byDate = summaries.associate { it.date to it.calories }
        return (count - 1 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            day to (byDate[day] ?: 0)
        }
    }

    /** Emits today's date now and again whenever it changes (checked every 30 s), so midnight rolls over. */
    fun todayFlow(zone: ZoneId = ZoneId.systemDefault()): Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(zone))
            delay(30_000)
        }
    }.distinctUntilChanged()

    /** Average calories over the days in [chart] that have something logged. */
    fun weeklyAverage(chart: List<Pair<LocalDate, Int>>): Int {
        val logged = chart.map { it.second }.filter { it > 0 }
        return if (logged.isEmpty()) 0 else logged.average().roundToInt()
    }

    /** Number of days in [chart] with food logged that finished at or under [goal]. */
    fun daysOnTarget(chart: List<Pair<LocalDate, Int>>, goal: Int): Int =
        chart.count { it.second in 1..goal }

    /** Daily macro targets derived from the calorie goal (25% protein, 45% carbs, 30% fat). */
    fun macroTargets(goalKcal: Int): MacroTargets = MacroTargets(
        proteinG = (goalKcal * 0.25 / 4).roundToInt(),
        carbsG = (goalKcal * 0.45 / 4).roundToInt(),
        fatG = (goalKcal * 0.30 / 9).roundToInt(),
    )

    fun progress(consumed: Int, goal: Int): Float =
        if (goal <= 0) 0f else (consumed.toFloat() / goal).coerceIn(0f, 1f)

    fun mealTypeForHour(hour: Int): MealType = when (hour) {
        in 4..10 -> MealType.BREAKFAST
        in 11..14 -> MealType.LUNCH
        in 15..16 -> MealType.SNACK
        in 17..21 -> MealType.DINNER
        else -> MealType.SNACK
    }
}
