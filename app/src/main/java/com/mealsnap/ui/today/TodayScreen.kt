package com.mealsnap.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mealsnap.MealSnapApplication
import com.mealsnap.ui.components.CalorieRing
import com.mealsnap.ui.components.MacroStat
import com.mealsnap.ui.components.MealRow
import com.mealsnap.ui.components.formatKcal
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(onOpenMeal: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as MealSnapApplication
    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(app.container) } })
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 16.dp, bottom = 4.dp)) {
                Text("Today", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()).format(state.date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CalorieRing(consumed = state.calories, goal = state.goal)
                    val remaining = state.goal - state.calories
                    Text(
                        if (remaining >= 0) "${formatKcal(remaining)} kcal left" else "${formatKcal(-remaining)} kcal over",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MacroStat("Protein", state.proteinG)
                        MacroStat("Carbs", state.carbsG)
                        MacroStat("Fat", state.fatG)
                    }
                }
            }
        }

        if (state.meals.isEmpty()) {
            item {
                Text(
                    "No meals yet.\nTap “Snap meal” and photograph your plate before you eat.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.meals.reversed(), key = { it.id }) { meal ->
                MealRow(meal = meal, onClick = { onOpenMeal(meal.id) })
            }
        }
    }
}
