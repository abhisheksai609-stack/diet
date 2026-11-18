package com.mealsnap.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mealsnap.MealSnapApplication
import com.mealsnap.data.DayStats
import com.mealsnap.data.DaySummary
import com.mealsnap.ui.components.FitCard
import com.mealsnap.ui.components.MealRow
import com.mealsnap.ui.components.SectionTitle
import com.mealsnap.ui.components.WeekChart
import com.mealsnap.ui.components.formatKcal
import com.mealsnap.ui.theme.extras
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(onOpenMeal: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as MealSnapApplication
    val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(app.container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    HistoryContent(state, onOpenMeal)
}

/** Stateless Trends screen, so it can be rendered with sample data. */
@Composable
fun HistoryContent(state: HistoryUiState, onOpenMeal: (Long) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Trends",
                modifier = Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp, bottom = 2.dp),
                style = MaterialTheme.typography.headlineLarge,
            )
        }

        item {
            FitCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Last 7 days", style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat(
                            "Daily average",
                            "${formatKcal(DayStats.weeklyAverage(state.chart))} kcal",
                            Modifier.weight(1f),
                        )
                        Stat(
                            "On target",
                            "${DayStats.daysOnTarget(state.chart, state.goal)} of 7 days",
                            Modifier.weight(1f),
                        )
                    }
                    WeekChart(data = state.chart, goal = state.goal)
                    Text(
                        "Dashed line = your ${formatKcal(state.goal)} kcal goal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.days.isEmpty()) {
            item {
                Text(
                    "Nothing logged yet. Your days will show up here.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item { SectionTitle("Daily log") }
        }

        items(state.days, key = { it.date.toEpochDay() }) { day ->
            DayCard(day = day, goal = state.goal, today = state.today, onOpenMeal = onOpenMeal)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DayCard(day: DaySummary, goal: Int, today: LocalDate, onOpenMeal: (Long) -> Unit) {
    val over = day.calories > goal
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dayLabel(day.date, today),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${formatKcal(day.calories)} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (over) MaterialTheme.extras.ringOver else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { DayStats.progress(day.calories, goal) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = if (over) MaterialTheme.extras.ringOver else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Protein ${day.proteinG} g · Carbs ${day.carbsG} g · Fat ${day.fatG} g",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        day.meals.forEach { meal ->
            MealRow(meal = meal, onClick = { onOpenMeal(meal.id) })
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()).format(date)
}
