package com.mealsnap.coach

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Sex(val label: String) {
    FEMALE("Female"),
    MALE("Male"),
    OTHER("Other / prefer not to say"),
}

enum class ActivityLevel(val label: String, val hint: String, val factor: Double) {
    SEDENTARY("Mostly sitting", "Desk job, little exercise", 1.2),
    LIGHT("Lightly active", "Light exercise 1 to 3 days a week", 1.375),
    MODERATE("Moderately active", "Exercise 3 to 5 days a week", 1.55),
    VERY("Very active", "Hard exercise 6 to 7 days a week", 1.725),
    EXTREME("Extremely active", "Hard daily training or a physical job", 1.9),
}

enum class Goal(val label: String) {
    LOSE("Lose weight"),
    MAINTAIN("Stay where I am"),
    GAIN("Build muscle / gain weight"),
}

data class Profile(
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val birthYear: Int? = null,
    val sex: Sex = Sex.OTHER,
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val goal: Goal = Goal.MAINTAIN,
    val targetWeightKg: Double? = null,
) {
    /** Enough to work out calorie targets. */
    val isComplete: Boolean get() = heightCm != null && weightKg != null && birthYear != null

    fun ageYears(today: LocalDate = LocalDate.now()): Int? = birthYear?.let { (today.year - it).coerceAtLeast(0) }
}

enum class BmiCategory(val label: String) {
    UNDERWEIGHT("underweight"),
    HEALTHY("in the healthy range"),
    OVERWEIGHT("overweight"),
    OBESE("in the obese range"),
}

data class CalorieGoal(
    val kcal: Int,
    val bmr: Int,
    val tdee: Int,
    /** Plain-language note when the suggestion was limited for safety, else null. */
    val note: String?,
)

data class MacroPlan(val proteinG: Int, val carbsG: Int, val fatG: Int)

data class HeartRateZone(val name: String, val fromBpm: Int, val toBpm: Int, val purpose: String)

/** Textbook body formulas. Estimates for guidance only, not medical advice. */
object BodyMetrics {

    fun bmi(weightKg: Double, heightCm: Double): Double = weightKg / (heightCm / 100.0).pow(2)

    fun bmiCategory(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory.UNDERWEIGHT
        bmi < 25.0 -> BmiCategory.HEALTHY
        bmi < 30.0 -> BmiCategory.OVERWEIGHT
        else -> BmiCategory.OBESE
    }

    /** Mifflin-St Jeor resting energy use in kcal/day. */
    fun bmr(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): Double {
        val constant = when (sex) {
            Sex.MALE -> 5.0
            Sex.FEMALE -> -161.0
            Sex.OTHER -> -78.0 // midpoint of the two
        }
        return 10 * weightKg + 6.25 * heightCm - 5 * ageYears + constant
    }

    /** Lowest daily intake the app will ever suggest, regardless of goal. */
    fun calorieFloor(sex: Sex): Int = when (sex) {
        Sex.MALE -> 1500
        Sex.FEMALE -> 1200
        Sex.OTHER -> 1350
    }

    /**
     * Suggested daily calories for the goal: maintenance, about 0.45 kg a week of loss (500 kcal below), or a
     * small surplus (300 kcal above) for gaining. Never below [calorieFloor], and no deficit is suggested for
     * someone who is already underweight. Rounded to the nearest 50.
     */
    fun calorieGoal(profile: Profile, today: LocalDate = LocalDate.now()): CalorieGoal? {
        val weight = profile.weightKg ?: return null
        val height = profile.heightCm ?: return null
        val age = profile.ageYears(today) ?: return null

        val bmr = bmr(weight, height, age, profile.sex)
        val tdee = bmr * profile.activity.factor
        var goal = profile.goal
        var note: String? = null

        if (goal == Goal.LOSE && bmiCategory(bmi(weight, height)) == BmiCategory.UNDERWEIGHT) {
            goal = Goal.MAINTAIN
            note = "Your BMI is in the underweight range, so I'm not suggesting a calorie deficit. " +
                "If you want to change your weight, please talk to a doctor or dietitian first."
        }

        var target = when (goal) {
            Goal.LOSE -> tdee - 500
            Goal.MAINTAIN -> tdee
            Goal.GAIN -> tdee + 300
        }
        val floor = calorieFloor(profile.sex)
        if (target < floor) {
            target = floor.toDouble()
            if (note == null) note = "I've kept this at $floor kcal, the lowest I'll suggest. Eating less than that isn't safe without medical supervision."
        }

        fun round50(v: Double) = (v / 50.0).roundToInt() * 50
        return CalorieGoal(round50(target), bmr.roundToInt(), tdee.roundToInt(), note)
    }

    /** Protein from body weight (more when losing or gaining), fat about 27% of calories, carbs the rest. */
    fun macroPlan(kcal: Int, weightKg: Double, goal: Goal): MacroPlan {
        val perKg = when (goal) {
            Goal.MAINTAIN -> 1.4
            Goal.LOSE, Goal.GAIN -> 1.8
        }
        val protein = min(weightKg * perKg, kcal * 0.35 / 4)
        val fat = kcal * 0.27 / 9
        val carbs = max(0.0, (kcal - protein * 4 - fat * 9) / 4)
        return MacroPlan(protein.roundToInt(), carbs.roundToInt(), fat.roundToInt())
    }

    /** Tanaka formula. */
    fun maxHeartRate(ageYears: Int): Int = (208 - 0.7 * ageYears).roundToInt()

    fun heartRateZones(maxHr: Int): List<HeartRateZone> {
        fun bpm(pct: Double) = (maxHr * pct).roundToInt()
        return listOf(
            HeartRateZone("Zone 1 · Easy", bpm(0.5), bpm(0.6), "Warm-up, recovery"),
            HeartRateZone("Zone 2 · Fat burn", bpm(0.6), bpm(0.7), "Easy steady cardio you can talk through"),
            HeartRateZone("Zone 3 · Cardio", bpm(0.7), bpm(0.8), "Builds fitness, breathing harder"),
            HeartRateZone("Zone 4 · Hard", bpm(0.8), bpm(0.9), "Intervals, short hard efforts"),
            HeartRateZone("Zone 5 · Peak", bpm(0.9), maxHr, "All-out efforts, very short"),
        )
    }

    fun waterMl(weightKg: Double): Int = ((weightKg * 35) / 50).roundToInt() * 50

    fun stepGoal(goal: Goal): Int = if (goal == Goal.LOSE) 10_000 else 8_000

    /** Rough weeks to reach the target at a safe pace (0.45 kg a week losing, 0.25 kg gaining). */
    fun weeksToTarget(profile: Profile): Int? {
        val weight = profile.weightKg ?: return null
        val target = profile.targetWeightKg ?: return null
        val diff = target - weight
        return when {
            profile.goal == Goal.LOSE && diff < 0 -> (-diff / 0.45).roundToInt().coerceAtLeast(1)
            profile.goal == Goal.GAIN && diff > 0 -> (diff / 0.25).roundToInt().coerceAtLeast(1)
            else -> null
        }
    }
}
