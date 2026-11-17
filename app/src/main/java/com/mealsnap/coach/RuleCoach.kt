package com.mealsnap.coach

import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The offline coach: plain rules over the person's own numbers. It runs on the phone with no key and no
 * internet, and is also what the app shows if Gemini isn't available. It gives general wellness tips, never
 * a diagnosis, and points to a doctor when a number is outside a normal range.
 */
object RuleCoach {

    fun tips(i: CoachInputs, max: Int = 5): List<Tip> {
        if (!i.profile.isComplete) {
            return listOf(
                Tip(
                    "Finish your profile",
                    "Add your height, weight and birth year and I'll set safe calorie, protein and step targets for you.",
                    TipKind.INFO,
                ),
            )
        }

        val all = listOfNotNull(
            heartRate(i), oxygen(i), sleep(i), calories(i), protein(i), steps(i), weight(i), energyBalance(i), water(i),
        )
        // Things to watch first, then wins, then everything else; the order inside a group is the order above.
        val ordered = all.filter { it.kind == TipKind.WATCH } + all.filter { it.kind == TipKind.GOOD } + all.filter { it.kind == TipKind.INFO }
        return ordered.take(max).ifEmpty {
            listOf(Tip("Log today's meals", "Snap your plate before you eat and I'll have more to work with.", TipKind.INFO))
        }
    }

    // ---- individual rules -----------------------------------------------------------------------

    private fun heartRate(i: CoachInputs): Tip? {
        val health = i.health ?: return null
        val today = health.today.restingHr ?: return null
        val previous = health.week.dropLast(1).mapNotNull { it.restingHr }

        return when {
            today > 100 -> Tip(
                "Resting heart rate is high",
                "Your resting heart rate is $today bpm. If it stays above 100 while you're at rest, or you feel unwell, " +
                    "please speak to a doctor. Rest, fluids and less caffeine can help in the meantime.",
                TipKind.WATCH,
            )
            today < 40 -> Tip(
                "Resting heart rate is very low",
                "Your resting heart rate is $today bpm. That can be normal for very fit people, but if you feel dizzy or faint, " +
                    "please check with a doctor.",
                TipKind.WATCH,
            )
            previous.size >= 3 && today >= mean(previous) + 7 -> Tip(
                "Resting heart rate is up",
                "$today bpm is ${(today - mean(previous)).roundToInt()} above your recent average of ${mean(previous).roundToInt()}. " +
                    "That often means poor sleep, stress, dehydration or your body fighting something. Take today a bit easier.",
                TipKind.WATCH,
            )
            previous.size >= 3 && today <= mean(previous) - 3 -> Tip(
                "Resting heart rate is trending down",
                "$today bpm is below your recent average of ${mean(previous).roundToInt()}, a good sign of recovery and improving fitness.",
                TipKind.GOOD,
            )
            else -> null
        }
    }

    private fun oxygen(i: CoachInputs): Tip? {
        val spo2 = i.health?.spo2Percent ?: return null
        return if (spo2 < 92) {
            Tip(
                "Low blood oxygen reading",
                "A reading of ${spo2.roundToInt()}% is lower than usual. Wrist readings can be off, but if it keeps showing " +
                    "below 92% or you feel short of breath, please see a doctor.",
                TipKind.WATCH,
            )
        } else {
            null
        }
    }

    private fun sleep(i: CoachInputs): Tip? {
        val health = i.health ?: return null
        val last = health.today.sleepMinutes ?: return null
        val avg = health.week.mapNotNull { it.sleepMinutes }.takeIf { it.size >= 3 }?.average()
        return when {
            last < 360 -> Tip(
                "Short night",
                "You slept ${hm(last)}. Aim for 7 to 9 hours: tonight, wind down earlier and keep screens and caffeine away " +
                    "in the last hour. Go lighter on hard training today.",
                TipKind.WATCH,
            )
            avg != null && avg < 390 -> Tip(
                "Sleep has been short this week",
                "Your average this week is ${hm(avg.toLong())}. Poor sleep raises hunger and makes weight loss harder, " +
                    "so an earlier bedtime is worth more than an extra workout.",
                TipKind.WATCH,
            )
            last in 420..570 -> Tip("Good sleep", "${hm(last)} last night is right in the healthy range. It helps appetite, mood and recovery.", TipKind.GOOD)
            else -> null
        }
    }

    private fun calories(i: CoachInputs): Tip? {
        val goal = i.goalKcal
        val eaten = i.today.calories
        val hour = i.now.hour
        return when {
            i.today.mealCount == 0 && hour >= 11 ->
                Tip("Nothing logged yet", "You haven't logged a meal today. Snap your next plate so your numbers stay accurate.", TipKind.INFO)
            eaten > goal * 1.10 -> Tip(
                "Over your goal today",
                "You're ${eaten - goal} kcal over $goal. One day won't undo your progress: keep dinner light, add a 20-minute " +
                    "walk, and carry on as normal tomorrow.",
                TipKind.INFO,
            )
            hour >= 20 && i.today.mealCount > 0 && eaten < goal * 0.6 -> Tip(
                "Don't under-eat",
                "You've had only $eaten of your $goal kcal. Eating far too little slows recovery and leads to overeating later. " +
                    "A balanced snack with protein would help.",
                TipKind.WATCH,
            )
            hour >= 18 && eaten in (goal * 0.85).roundToInt()..(goal * 1.05).roundToInt() -> Tip(
                "Right on target",
                "$eaten of $goal kcal so far today: you're right where you want to be.",
                TipKind.GOOD,
            )
            else -> null
        }
    }

    private fun protein(i: CoachInputs): Tip? {
        val target = i.macros?.proteinG ?: return null
        val days = i.nutritionWeek.filter { it.totals.mealCount > 0 }
        val average = days.takeIf { it.size >= 3 }?.map { it.totals.proteinG }?.average()
        return when {
            i.today.proteinG >= target ->
                Tip("Protein goal hit", "${i.today.proteinG} g of protein today meets your $target g target, which protects muscle.", TipKind.GOOD)
            average != null && average < target * 0.75 -> Tip(
                "Protein is low",
                "You're averaging ${average.roundToInt()} g of protein a day against a $target g target. Add eggs, Greek yogurt, " +
                    "paneer, dal, chicken, fish or tofu to each meal.",
                TipKind.INFO,
            )
            else -> null
        }
    }

    private fun steps(i: CoachInputs): Tip? {
        val health = i.health ?: return null
        val goal = BodyMetrics.stepGoal(i.profile.goal)
        val today = health.today.steps ?: return null
        val average = health.week.mapNotNull { it.steps }.takeIf { it.size >= 3 }?.average()
        return when {
            today >= goal -> Tip("Step goal reached", "$today steps today beats your $goal goal. Nice work.", TipKind.GOOD)
            i.now.hour >= 18 && today < goal * 0.5 -> Tip(
                "A walk would help",
                "You're at $today of $goal steps. A brisk 20 to 30 minute walk this evening closes most of the gap and helps sleep.",
                TipKind.INFO,
            )
            average != null && average < 5000 -> Tip(
                "Move a little more",
                "You average about ${average.roundToInt()} steps a day. A 10-minute walk after each meal adds around 3,000 steps " +
                    "and helps blood sugar.",
                TipKind.INFO,
            )
            else -> null
        }
    }

    private fun weight(i: CoachInputs): Tip? {
        val points = i.weights.filter { ChronoUnit.DAYS.between(it.date, i.now.toLocalDate()) <= 28 }
        if (points.size < 2) return null
        val first = points.first()
        val last = points.last()
        val days = ChronoUnit.DAYS.between(first.date, last.date)
        if (days < 7) return null
        val perWeek = (last.kg - first.kg) / days * 7
        val percent = perWeek / last.kg * 100

        return when (i.profile.goal) {
            Goal.LOSE -> when {
                percent < -1.0 -> Tip(
                    "Losing quite fast",
                    "You're losing about ${fmt(-perWeek)} kg a week. Faster than about 1% of body weight a week risks losing muscle: " +
                        "eat a little more protein and don't cut calories further.",
                    TipKind.WATCH,
                )
                perWeek < -0.15 -> Tip("Steady loss", "Down about ${fmt(-perWeek)} kg a week: a sustainable pace. Keep going.", TipKind.GOOD)
                perWeek > 0.2 -> Tip(
                    "Weight is creeping up",
                    "You've gained about ${fmt(perWeek)} kg a week lately. Check portions on the days you eat out, and add a walk.",
                    TipKind.INFO,
                )
                else -> null
            }
            Goal.GAIN -> when {
                perWeek in 0.1..0.5 -> Tip("Gaining steadily", "Up about ${fmt(perWeek)} kg a week, a good pace for building muscle rather than fat.", TipKind.GOOD)
                perWeek < 0 -> Tip("Not gaining yet", "Your weight is trending down. Add a snack with protein and strength train at least 3 times a week.", TipKind.INFO)
                else -> null
            }
            Goal.MAINTAIN -> if (abs(perWeek) > 0.5) {
                Tip("Weight is moving", "You're changing by about ${fmt(abs(perWeek))} kg a week while aiming to stay level. Adjust portions slightly.", TipKind.INFO)
            } else {
                null
            }
        }
    }

    private fun energyBalance(i: CoachInputs): Tip? {
        val burned = i.health?.today?.totalKcal ?: return null
        if (i.now.hour < 18 || i.today.mealCount == 0) return null
        val net = i.today.calories - burned.roundToInt()
        return Tip(
            if (net < 0) "Estimated calorie deficit" else "Estimated calorie surplus",
            "You've eaten ${i.today.calories} kcal and burned about ${burned.roundToInt()} so far, a net of " +
                "${if (net < 0) "−" else "+"}${abs(net)} kcal. Your device's estimate is approximate.",
            TipKind.INFO,
        )
    }

    private fun water(i: CoachInputs): Tip? {
        val kg = i.profile.weightKg ?: return null
        return Tip("Hydration", "Aim for roughly ${BodyMetrics.waterMl(kg)} ml of water today, more if you train or it's hot.", TipKind.INFO)
    }

    // ---- helpers --------------------------------------------------------------------------------

    private fun mean(values: List<Int>): Double = values.average()

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)

    /** "6 h 40 min" */
    fun hm(minutes: Long): String = "${minutes / 60} h ${minutes % 60} min"
}
