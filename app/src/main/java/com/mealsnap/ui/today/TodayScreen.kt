package com.mealsnap.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mealsnap.MealSnapApplication
import com.mealsnap.R
import com.mealsnap.data.DayStats
import com.mealsnap.data.MealType
import com.mealsnap.ui.components.CalorieHero
import com.mealsnap.ui.components.FitCard
import com.mealsnap.ui.components.MacroTile
import com.mealsnap.ui.components.MealGroupCard
import com.mealsnap.ui.components.SectionTitle
import com.mealsnap.ui.theme.extras
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    onOpenMeal: (Long) -> Unit,
    onTakePhoto: () -> Unit,
    onUploadPhoto: () -> Unit,
    onManual: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as MealSnapApplication
    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(app.container) } })
    val state by vm.state.collectAsStateWithLifecycle()

    TodayContent(
        state = state,
        onOpenMeal = onOpenMeal,
        onTakePhoto = onTakePhoto,
        onUploadPhoto = onUploadPhoto,
        onManual = onManual,
    )
}

/** Stateless Today screen, so it can be rendered with sample data. */
@Composable
fun TodayContent(
    state: TodayUiState,
    onOpenMeal: (Long) -> Unit,
    onTakePhoto: () -> Unit,
    onUploadPhoto: () -> Unit,
    onManual: () -> Unit,
) {
    val extras = MaterialTheme.extras
    val targets = DayStats.macroTargets(state.goal)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp, bottom = 2.dp)) {
                Text("Today", style = MaterialTheme.typography.headlineLarge)
                Text(
                    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()).format(state.date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { CalorieHero(consumed = state.calories, goal = state.goal) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroTile("Protein", state.proteinG, targets.proteinG, extras.protein, Modifier.weight(1f))
                MacroTile("Carbs", state.carbsG, targets.carbsG, extras.carbs, Modifier.weight(1f))
                MacroTile("Fat", state.fatG, targets.fatG, extras.fat, Modifier.weight(1f))
            }
        }

        if (state.meals.isEmpty()) {
            item { EmptyCard(onTakePhoto, onUploadPhoto, onManual) }
        } else {
            item { SectionTitle("Food log") }
            val byType = state.meals.groupBy { it.type }
            MealType.entries.forEach { type ->
                val meals = byType[type].orEmpty()
                if (meals.isNotEmpty()) {
                    item(key = type.name) {
                        MealGroupCard(type = type, meals = meals, onOpenMeal = onOpenMeal)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(onTakePhoto: () -> Unit, onUploadPhoto: () -> Unit, onManual: () -> Unit) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_camera),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text("Log your first meal", style = MaterialTheme.typography.titleLarge)
            Text(
                "Take a photo of your plate before you eat, or upload one you already have. " +
                    "MealSnap names the dish and counts the calories, right on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onTakePhoto, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
                Text("Take a photo")
            }
            FilledTonalButton(onClick = onUploadPhoto, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
                Text("Upload a photo")
            }
            TextButton(onClick = onManual) { Text("Enter manually") }
        }
    }
}
