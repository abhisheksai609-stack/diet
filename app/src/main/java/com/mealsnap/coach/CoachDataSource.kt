package com.mealsnap.coach

import com.mealsnap.data.DayStats
import com.mealsnap.data.MealDao
import com.mealsnap.data.SettingsRepository
import com.mealsnap.data.WeightDao
import com.mealsnap.health.HealthRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId

/** Everything the coach screens and the AI trainer need, gathered from the database, settings and Health Connect. */
data class CoachLoad(
    val profile: Profile,
    val goalKcal: Int,
    val recommended: CalorieGoal?,
    val inputs: CoachInputs,
    val health: HealthSnapshot?,
    val weights: List<WeightPoint>,
)

class CoachDataSource(
    private val mealDao: MealDao,
    private val weightDao: WeightDao,
    private val settings: SettingsRepository,
    private val health: HealthRepository,
) {
    suspend fun load(now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): CoachLoad {
        val profile = settings.profile.first()
        val goalKcal = settings.dailyGoal.first()
        val today = now.toLocalDate()

        val meals = mealDao.observeBetween(
            DayStats.startOfDayMillis(today.minusDays(6), zone),
            DayStats.startOfDayMillis(today.plusDays(1), zone),
        ).first()

        // Health Connect problems (permission revoked, provider updating) must never break the coach.
        val snapshot = runCatching { health.readSnapshot(7, zone) }.getOrNull()
        val deviceWeights = runCatching { health.readWeights(90, zone) }.getOrDefault(emptyList())
        val weights = CoachInputsBuilder.mergeWeights(weightDao.observeAll().first(), deviceWeights, zone)

        return CoachLoad(
            profile = profile,
            goalKcal = goalKcal,
            recommended = BodyMetrics.calorieGoal(profile, today),
            inputs = CoachInputsBuilder.build(profile, goalKcal, meals, snapshot, weights, now, zone),
            health = snapshot,
            weights = weights,
        )
    }
}
