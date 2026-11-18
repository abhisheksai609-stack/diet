package com.mealsnap.coach

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns the person's numbers into a compact text summary that is given to the AI trainer along with the
 * question. Only summaries are included (no photos, no names, no raw heart-rate samples).
 */
object CoachContext {

    private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun describe(i: CoachInputs): String = buildString {
        val p = i.profile
        val age = p.ageYears(i.now.toLocalDate())

        if (p.isComplete) {
            val bmi = BodyMetrics.bmi(p.weightKg!!, p.heightCm!!)
            appendLine(
                "PERSON: ${age ?: "?"} years old, ${p.sex.label.lowercase(Locale.US).substringBefore(" /")}, ${n(p.heightCm)} cm, " +
                    "${n(p.weightKg)} kg (BMI ${n(bmi)}, ${BodyMetrics.bmiCategory(bmi).label}).",
            )
            appendLine(
                "GOAL: ${p.goal.label.lowercase(Locale.US)}" +
                    (p.targetWeightKg?.let { ", target ${n(it)} kg" }.orEmpty()) +
                    ". Activity level: ${p.activity.label.lowercase(Locale.US)}.",
            )
            val cal = BodyMetrics.calorieGoal(p, i.now.toLocalDate())
            appendLine(
                "TARGETS: ${i.goalKcal} kcal a day" +
                    (cal?.let { " (resting burn ${it.bmr}, typical daily burn ${it.tdee})" }.orEmpty()) +
                    (i.macros?.let { "; protein ${it.proteinG} g, carbs ${it.carbsG} g, fat ${it.fatG} g" }.orEmpty()) +
                    "; steps ${BodyMetrics.stepGoal(p.goal)}; water about ${BodyMetrics.waterMl(p.weightKg)} ml.",
            )
            if (age != null) {
                val max = BodyMetrics.maxHeartRate(age)
                appendLine("HEART RATE: estimated maximum $max bpm; zone 2 is ${(max * 0.6).toInt()} to ${(max * 0.7).toInt()} bpm.")
            }
        } else {
            appendLine("PERSON: profile not filled in yet, so no personal targets are available.")
        }

        appendLine("NOW: ${i.now.format(DateTimeFormatter.ofPattern("EEEE HH:mm", Locale.US))}.")

        val t = i.today
        appendLine(
            "FOOD TODAY SO FAR: ${t.calories} kcal (protein ${t.proteinG} g, carbs ${t.carbsG} g, fat ${t.fatG} g, fibre ${t.fiberG} g) " +
                "across ${t.mealCount} meal${if (t.mealCount == 1) "" else "s"}.",
        )
        t.mealLines.forEach { appendLine("  - $it") }

        val h = i.health?.today
        if (h != null && h.hasAnything) {
            appendLine(
                "ACTIVITY TODAY: " + listOfNotNull(
                    h.steps?.let { "$it steps" },
                    h.activeKcal?.let { "${it.toInt()} active kcal" },
                    h.totalKcal?.let { "${it.toInt()} kcal burned in total" },
                    h.exerciseMinutes?.let { "$it min of exercise" },
                    h.distanceM?.let { "${n(it / 1000)} km" },
                ).ifEmpty { listOf("no data") }.joinToString(", ") + ".",
            )
            appendLine(
                "HEART AND SLEEP: " + listOfNotNull(
                    h.restingHr?.let { "resting heart rate $it bpm" },
                    h.avgHr?.let { "average $it bpm today" },
                    if (h.minHr != null && h.maxHr != null) "range ${h.minHr} to ${h.maxHr} bpm" else null,
                    h.sleepMinutes?.let { "slept ${RuleCoach.hm(it)} last night" },
                    i.health.hrvMs?.let { "HRV ${it.toInt()} ms" },
                    i.health.spo2Percent?.let { "blood oxygen ${it.toInt()}%" },
                ).ifEmpty { listOf("no data") }.joinToString(", ") + ".",
            )
        } else {
            appendLine("DEVICE DATA: none available (no connected tracker data).")
        }

        if (i.nutritionWeek.any { it.totals.mealCount > 0 }) {
            appendLine(
                "LAST 7 DAYS calories eaten (${i.nutritionWeek.first().date.format(dayFormat)} to ${i.nutritionWeek.last().date.format(dayFormat)}): " +
                    i.nutritionWeek.joinToString(", ") { if (it.totals.mealCount == 0) "none" else it.totals.calories.toString() } + ".",
            )
            appendLine(
                "LAST 7 DAYS protein g: " + i.nutritionWeek.joinToString(", ") { if (it.totals.mealCount == 0) "none" else it.totals.proteinG.toString() } + ".",
            )
        }
        val week = i.health?.week.orEmpty()
        if (week.any { it.steps != null }) appendLine("LAST 7 DAYS steps: " + week.joinToString(", ") { it.steps?.toString() ?: "none" } + ".")
        if (week.any { it.restingHr != null }) appendLine("LAST 7 DAYS resting heart rate: " + week.joinToString(", ") { it.restingHr?.toString() ?: "none" } + ".")
        if (week.any { it.sleepMinutes != null }) {
            appendLine("LAST 7 DAYS sleep hours: " + week.joinToString(", ") { it.sleepMinutes?.let { m -> n(m / 60.0) } ?: "none" } + ".")
        }

        if (i.weights.size >= 2) {
            val first = i.weights.first()
            val last = i.weights.last()
            appendLine(
                "WEIGHT: ${n(first.kg)} kg on ${first.date.format(dayFormat)} to ${n(last.kg)} kg on ${last.date.format(dayFormat)} " +
                    "(${if (last.kg >= first.kg) "+" else "−"}${n(kotlin.math.abs(last.kg - first.kg))} kg).",
            )
        } else if (i.weights.size == 1) {
            appendLine("WEIGHT: ${n(i.weights.first().kg)} kg on ${i.weights.first().date.format(dayFormat)}.")
        }
    }.trimEnd()

    private fun n(v: Double): String = String.format(Locale.US, "%.1f", v).removeSuffix(".0")
}
