package com.mealsnap.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CoachLogicTest {

    private val today = LocalDate.of(2026, 11, 17)
    private val evening = LocalDateTime.of(2026, 11, 17, 19, 30)
    private val morning = LocalDateTime.of(2026, 11, 17, 8, 0)

    private val man = Profile(heightCm = 180.0, weightKg = 80.0, birthYear = 1996, sex = Sex.MALE, activity = ActivityLevel.MODERATE, goal = Goal.LOSE, targetWeightKg = 75.0)

    // ---- body maths ---------------------------------------------------------------------------

    @Test
    fun mifflinStJeorMatchesHandCalculation() {
        // 10*80 + 6.25*180 - 5*30 + 5
        assertEquals(1780.0, BodyMetrics.bmr(80.0, 180.0, 30, Sex.MALE), 0.001)
        // 10*60 + 6.25*165 - 5*30 - 161
        assertEquals(1320.25, BodyMetrics.bmr(60.0, 165.0, 30, Sex.FEMALE), 0.001)
        assertEquals(1780.0 - 83.0, BodyMetrics.bmr(80.0, 180.0, 30, Sex.OTHER), 0.001)
    }

    @Test
    fun calorieGoalFollowsTheGoal() {
        fun goal(g: Goal) = BodyMetrics.calorieGoal(man.copy(goal = g), today)!!

        val keep = goal(Goal.MAINTAIN)
        assertEquals(1780, keep.bmr)
        assertEquals(2759, keep.tdee) // 1780 x 1.55
        assertEquals(2750, keep.kcal)
        assertEquals(2250, goal(Goal.LOSE).kcal)
        assertEquals(3050, goal(Goal.GAIN).kcal)
        assertNull(keep.note)
    }

    @Test
    fun theAppNeverSuggestsAnUnsafeCalorieGoal() {
        val small = Profile(heightCm = 150.0, weightKg = 50.0, birthYear = 1986, sex = Sex.FEMALE, activity = ActivityLevel.SEDENTARY, goal = Goal.LOSE)
        val goal = BodyMetrics.calorieGoal(small, today)!!
        assertEquals(1200, goal.kcal)
        assertNotNull(goal.note)
        assertEquals(1500, BodyMetrics.calorieFloor(Sex.MALE))
    }

    @Test
    fun noDeficitIsSuggestedForSomeoneUnderweight() {
        val thin = Profile(heightCm = 175.0, weightKg = 52.0, birthYear = 1996, sex = Sex.MALE, goal = Goal.LOSE)
        assertEquals(BmiCategory.UNDERWEIGHT, BodyMetrics.bmiCategory(BodyMetrics.bmi(52.0, 175.0)))

        val goal = BodyMetrics.calorieGoal(thin, today)!!

        assertTrue("should be maintenance, not a deficit", goal.kcal >= (goal.tdee / 50) * 50 - 50 && goal.kcal >= 1500)
        assertTrue(goal.note!!.contains("doctor"))
    }

    @Test
    fun incompleteProfilesGetNoTargets() {
        assertNull(BodyMetrics.calorieGoal(Profile(), today))
        assertFalse(Profile(heightCm = 170.0).isComplete)
        assertTrue(man.isComplete)
    }

    @Test
    fun bmiCategoriesUseTheStandardCutoffs() {
        assertEquals(24.69, BodyMetrics.bmi(80.0, 180.0), 0.01)
        assertEquals(BmiCategory.UNDERWEIGHT, BodyMetrics.bmiCategory(18.4))
        assertEquals(BmiCategory.HEALTHY, BodyMetrics.bmiCategory(18.5))
        assertEquals(BmiCategory.HEALTHY, BodyMetrics.bmiCategory(24.9))
        assertEquals(BmiCategory.OVERWEIGHT, BodyMetrics.bmiCategory(25.0))
        assertEquals(BmiCategory.OBESE, BodyMetrics.bmiCategory(30.0))
    }

    @Test
    fun macroPlanAddsUpToTheCalorieGoal() {
        val plan = BodyMetrics.macroPlan(2250, 80.0, Goal.LOSE)
        assertEquals(144, plan.proteinG) // 1.8 g per kg
        val kcal = plan.proteinG * 4 + plan.carbsG * 4 + plan.fatG * 9
        assertTrue("macros give $kcal kcal", kotlin.math.abs(kcal - 2250) <= 20)
        // Protein never exceeds 35% of calories, even for a heavy person on a low-calorie goal.
        val heavy = BodyMetrics.macroPlan(1500, 150.0, Goal.LOSE)
        assertTrue(heavy.proteinG * 4 <= 1500 * 0.35 + 4)
        assertTrue(heavy.carbsG >= 0)
    }

    @Test
    fun heartRateZonesFromAge() {
        assertEquals(187, BodyMetrics.maxHeartRate(30))
        val zones = BodyMetrics.heartRateZones(187)
        assertEquals(5, zones.size)
        assertEquals(112, zones[1].fromBpm) // 60%
        assertEquals(131, zones[1].toBpm)   // 70%
        assertEquals(187, zones.last().toBpm)
    }

    @Test
    fun smallHelpers() {
        assertEquals(2800, BodyMetrics.waterMl(80.0))
        assertEquals(10_000, BodyMetrics.stepGoal(Goal.LOSE))
        assertEquals(8_000, BodyMetrics.stepGoal(Goal.MAINTAIN))
        assertEquals(11, BodyMetrics.weeksToTarget(man)) // 5 kg at 0.45 kg a week
        assertNull(BodyMetrics.weeksToTarget(man.copy(goal = Goal.MAINTAIN)))
        assertEquals(30, man.ageYears(today))
    }

    // ---- rule-based coach ---------------------------------------------------------------------

    private fun day(offset: Int, steps: Long? = null, resting: Int? = null, sleep: Long? = null, total: Double? = null) =
        HealthDay(today.minusDays(offset.toLong()), steps = steps, restingHr = resting, sleepMinutes = sleep, totalKcal = total)

    private fun inputs(
        now: LocalDateTime = evening,
        eaten: Int = 1800,
        protein: Int = 100,
        meals: Int = 3,
        week: List<HealthDay> = emptyList(),
        weights: List<WeightPoint> = emptyList(),
        profile: Profile = man,
        extra: HealthSnapshot? = null,
        weekMeals: List<DayNutrition> = emptyList(),
    ): CoachInputs {
        val snapshot = extra ?: week.takeIf { it.isNotEmpty() }?.let { HealthSnapshot(it.last(), it) }
        val goal = BodyMetrics.calorieGoal(profile, today)?.kcal ?: 2000
        return CoachInputs(
            profile = profile, goalKcal = goal, macros = BodyMetrics.macroPlan(goal, profile.weightKg ?: 70.0, profile.goal), now = now,
            today = MealTotals(eaten, protein, 150, 60, 20, meals), nutritionWeek = weekMeals, health = snapshot, weights = weights,
        )
    }

    @Test
    fun anIncompleteProfileJustAsksForTheProfile() {
        val tips = RuleCoach.tips(inputs(profile = Profile()))
        assertEquals(1, tips.size)
        assertEquals("Finish your profile", tips.single().title)
    }

    @Test
    fun aRacingRestingHeartRatePointsToADoctor() {
        val week = listOf(day(2, resting = 60), day(1, resting = 61), day(0, resting = 104))
        val tips = RuleCoach.tips(inputs(week = week))

        assertEquals(TipKind.WATCH, tips.first().kind)
        assertEquals("Resting heart rate is high", tips.first().title)
        assertTrue(tips.first().body.contains("doctor"))
    }

    @Test
    fun aJumpAgainstYourOwnAverageIsFlagged() {
        val week = listOf(day(4, resting = 58), day(3, resting = 59), day(2, resting = 57), day(1, resting = 58), day(0, resting = 67))
        val tip = RuleCoach.tips(inputs(week = week)).first { it.title.startsWith("Resting heart rate") }
        assertEquals("Resting heart rate is up", tip.title)
        assertEquals(TipKind.WATCH, tip.kind)
    }

    @Test
    fun aFallingRestingHeartRateIsPraised() {
        val week = listOf(day(4, resting = 62), day(3, resting = 61), day(2, resting = 62), day(1, resting = 63), day(0, resting = 57))
        val tip = RuleCoach.tips(inputs(week = week)).first { it.title.startsWith("Resting heart rate") }
        assertEquals(TipKind.GOOD, tip.kind)
    }

    @Test
    fun aShortNightIsFlaggedAndAGoodNightPraised() {
        val short = RuleCoach.tips(inputs(week = listOf(day(0, sleep = 330)))).first { it.title == "Short night" }
        assertEquals(TipKind.WATCH, short.kind)
        assertTrue(short.body.contains("5 h 30 min"))

        val good = RuleCoach.tips(inputs(week = listOf(day(0, sleep = 450)))).first { it.title == "Good sleep" }
        assertEquals(TipKind.GOOD, good.kind)
    }

    @Test
    fun eatingFarTooLittleIsAWarningInTheEvening() {
        val tips = RuleCoach.tips(inputs(now = LocalDateTime.of(2026, 11, 17, 21, 0), eaten = 900))
        assertEquals("Don't under-eat", tips.first().title)
    }

    @Test
    fun beingOverTheGoalIsHandledCalmly() {
        val tip = RuleCoach.tips(inputs(eaten = 2700)).first { it.title == "Over your goal today" }
        assertTrue(tip.body.contains("One day won't undo"))
        assertEquals(TipKind.INFO, tip.kind)
    }

    @Test
    fun stepGoalsAreCelebratedOrNudged() {
        assertEquals("Step goal reached", RuleCoach.tips(inputs(week = listOf(day(0, steps = 11_000)))).first { it.title.startsWith("Step") }.title)
        val nudge = RuleCoach.tips(inputs(week = listOf(day(0, steps = 2_000)))).first { it.title == "A walk would help" }
        assertTrue(nudge.body.contains("2000 of 10000"))
    }

    @Test
    fun fastWeightLossIsAWarningAndSteadyLossIsPraised() {
        fun pts(startKg: Double, endKg: Double) = listOf(WeightPoint(today.minusDays(14), startKg), WeightPoint(today, endKg))

        val fast = RuleCoach.tips(inputs(weights = pts(82.0, 79.5))).first { it.title == "Losing quite fast" }
        assertEquals(TipKind.WATCH, fast.kind)

        val steady = RuleCoach.tips(inputs(weights = pts(80.6, 80.0))).first { it.title == "Steady loss" }
        assertEquals(TipKind.GOOD, steady.kind)
    }

    @Test
    fun tipsAreOrderedWatchThenGoodThenInfoAndCapped() {
        val week = listOf(day(2, resting = 60), day(1, resting = 60), day(0, resting = 105, steps = 12_000, sleep = 300))
        val tips = RuleCoach.tips(inputs(week = week), max = 4)

        assertEquals(4, tips.size)
        val kinds = tips.map { it.kind }
        assertEquals("watch tips come first", kinds.sortedBy { it.ordinal == TipKind.WATCH.ordinal }.size, kinds.size)
        val firstNonWatch = kinds.indexOfFirst { it != TipKind.WATCH }
        assertTrue(kinds.drop(firstNonWatch).none { it == TipKind.WATCH })
    }

    @Test
    fun thereIsAlwaysAtLeastOneTip() {
        assertTrue(RuleCoach.tips(inputs(now = morning, eaten = 0, meals = 0)).isNotEmpty())
    }

    // ---- AI context ---------------------------------------------------------------------------

    @Test
    fun theContextCarriesTheKeyNumbersAndNoNulls() {
        val week = listOf(day(1, steps = 7000, resting = 58, sleep = 400), day(0, steps = 6420, resting = 59, sleep = 400))
        val ctx = CoachContext.describe(
            inputs(week = week, weights = listOf(WeightPoint(today.minusDays(20), 80.5), WeightPoint(today, 80.0)))
                .copy(today = MealTotals(1320, 66, 130, 58, 12, 3, listOf("Breakfast: Avocado toast (420 kcal)"))),
        )

        assertTrue(ctx, ctx.contains("30 years old"))
        assertTrue(ctx, ctx.contains("180 cm"))
        assertTrue(ctx, ctx.contains("80 kg"))
        assertTrue(ctx, ctx.contains("2250 kcal a day"))
        assertTrue(ctx, ctx.contains("1320 kcal"))
        assertTrue(ctx, ctx.contains("Avocado toast"))
        assertTrue(ctx, ctx.contains("6420 steps"))
        assertTrue(ctx, ctx.contains("resting heart rate 59 bpm"))
        assertTrue(ctx, ctx.contains("WEIGHT: 80.5 kg"))
        assertFalse(ctx, ctx.contains("null"))
    }

    @Test
    fun theContextSaysSoWhenThereIsNoDeviceData() {
        val ctx = CoachContext.describe(inputs())
        assertTrue(ctx.contains("none available"))
        assertFalse(ctx.contains("null"))
        assertTrue(CoachContext.describe(inputs(profile = Profile())).contains("profile not filled in"))
    }
}
