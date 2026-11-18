package com.mealsnap.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.mealsnap.MealSnapApplication
import com.mealsnap.ai.Alternative
import com.mealsnap.ai.FoodItem
import com.mealsnap.nutrition.FoodEntry
import com.mealsnap.nutrition.NutritionDb
import com.mealsnap.data.MealType
import com.mealsnap.ui.components.FitCard
import com.mealsnap.ui.components.formatKcal
import com.mealsnap.ui.theme.extras
import java.io.File
import kotlin.math.roundToInt

/** Everything the editor UI can ask for, so the UI itself stays stateless and previewable. */
class EditorActions(
    val onClose: () -> Unit,
    val onAnalyze: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onName: (String) -> Unit,
    val onMealType: (MealType) -> Unit,
    val onCalories: (String) -> Unit,
    val onProtein: (String) -> Unit,
    val onCarbs: (String) -> Unit,
    val onFat: (String) -> Unit,
    val onHint: (String) -> Unit,
    val onSelectAlternative: (Alternative) -> Unit,
    val onPickFood: (FoodEntry) -> Unit,
    val onPortion: (Float) -> Unit,
)

@Composable
fun MealEditorScreen(
    mealId: Long,
    photo: String,
    onClose: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as MealSnapApplication
    val vm: MealEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { MealEditorViewModel(app.container, mealId, photo) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) { if (state.finished) onClose() }

    MealEditorContent(
        state = state,
        actions = EditorActions(
            onClose = onClose,
            onAnalyze = vm::analyze,
            onSave = vm::save,
            onDelete = vm::delete,
            onName = vm::setName,
            onMealType = vm::setMealType,
            onCalories = vm::setCalories,
            onProtein = vm::setProtein,
            onCarbs = vm::setCarbs,
            onFat = vm::setFat,
            onHint = vm::setHint,
            onSelectAlternative = vm::selectAlternative,
            onPickFood = vm::pickFood,
            onPortion = vm::setPortion,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditorContent(state: EditorState, actions: EditorActions) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isSaved) "Meal details" else "New meal") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = actions.onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isSaved) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete meal")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!state.busy) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val upToDate = state.isSaved && !state.dirty
                    Button(
                        onClick = { if (upToDate) actions.onClose() else actions.onSave() },
                        enabled = upToDate || state.canSave,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) {
                        Text(
                            when {
                                !state.isSaved -> "Save meal"
                                state.dirty -> "Save changes"
                                else -> "Done"
                            },
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.photoPath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = "Meal photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large),
                )
            }

            StatusCard(state, onRetry = actions.onAnalyze)

            if (!state.busy) {
                if (state.hasResult) {
                    ResultCard(state)
                    if (state.alternatives.isNotEmpty()) AlternativesCard(state.alternatives, actions.onSelectAlternative)
                    if (state.dish != null) PortionCard(state, actions.onPortion)
                    if (state.items.isNotEmpty()) ItemsCard(state.items)
                    if (state.visualCues.isNotEmpty() || state.notes.isNotBlank()) {
                        CuesCard(state.visualCues, state.notes)
                    }
                }

                EditCard(state, actions)

                if (state.photoPath != null) RefineCard(state, actions)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this meal?") },
            text = { Text("It will be removed from your log and from today's total.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatusCard(state: EditorState, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    when (val status = state.status) {
        AnalysisStatus.Idle -> Unit

        AnalysisStatus.Preparing, AnalysisStatus.Analyzing -> Surface(
            shape = MaterialTheme.shapes.large,
            color = scheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = scheme.onPrimaryContainer)
                Column {
                    Text(
                        if (status == AnalysisStatus.Preparing) "Preparing photo…" else "Identifying your food…",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onPrimaryContainer,
                    )
                    Text(
                        if (state.workingOn != "your phone") "Sending the photo to ${state.workingOn}. This takes a few seconds."
                        else "Working it out on your phone. No internet or account needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer,
                    )
                }
            }
        }

        is AnalysisStatus.Failed -> Surface(
            shape = MaterialTheme.shapes.large,
            color = scheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(status.message, style = MaterialTheme.typography.bodyLarge, color = scheme.onErrorContainer)
                if (state.photoPath != null) {
                    TextButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.textButtonColors(contentColor = scheme.onErrorContainer),
                    ) { Text("Try again") }
                }
            }
        }

        AnalysisStatus.Done -> Surface(
            shape = MaterialTheme.shapes.large,
            color = scheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    if (state.isSaved && !state.dirty) "Identified and logged ✓" else "Identified ✓",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    "By ${state.engine?.label ?: "the app"}. " +
                        "${state.confidence.orEmpty().replaceFirstChar { it.uppercase() }} confidence. " +
                        "Estimates can be off, so adjust anything below if you know better.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(state: EditorState) {
    val extras = MaterialTheme.extras
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.name.ifBlank { "Meal" }, style = MaterialTheme.typography.headlineMedium)
                if (state.cuisine.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            state.cuisine,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    formatKcal(state.calories.toIntOrNull() ?: 0),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "kcal",
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Nutrient("Protein", "${state.protein.ifBlank { "0" }} g", extras.protein)
                Nutrient("Carbs", "${state.carbs.ifBlank { "0" }} g", extras.carbs)
                Nutrient("Fat", "${state.fat.ifBlank { "0" }} g", extras.fat)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Nutrient("Fibre", "${state.fiberG} g", MaterialTheme.colorScheme.onSurface, small = true)
                Nutrient("Sugar", "${state.sugarG} g", MaterialTheme.colorScheme.onSurface, small = true)
                Nutrient("Sodium", "${state.sodiumMg} mg", MaterialTheme.colorScheme.onSurface, small = true)
            }
        }
    }
}

@Composable
private fun Nutrient(label: String, value: String, color: Color, small: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = color,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ItemsCard(items: List<FoodItem>) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("What's on the plate", style = MaterialTheme.typography.titleLarge)
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        if (item.portion.isNotBlank()) {
                            Text(
                                item.portion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "P ${item.proteinG} g · C ${item.carbsG} g · F ${item.fatG} g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("${formatKcal(item.calories)} kcal", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun CuesCard(cues: List<String>, notes: String) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How it was estimated", style = MaterialTheme.typography.titleLarge)
            cues.forEach { cue ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(cue, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (notes.isNotBlank()) {
                Text(
                    notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EditCard(state: EditorState, actions: EditorActions) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (state.aiEstimated) "Edit details" else "Meal details",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = actions.onName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Dish name") },
                supportingText = if (!state.hasResult) {
                    { Text("Start typing and pick a food to fill in the calories for you") }
                } else null,
                singleLine = true,
            )

            if (!state.hasResult) {
                val suggestions = remember(state.name) { NutritionDb.search(state.name, limit = 5) }
                suggestions.forEach { food -> FoodSuggestion(food, onClick = { actions.onPickFood(food) }) }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = state.mealType == type,
                        onClick = { actions.onMealType(type) },
                        label = { Text(type.label) },
                    )
                }
            }

            OutlinedTextField(
                value = state.calories,
                onValueChange = { v -> if (v.length <= 5 && v.all(Char::isDigit)) actions.onCalories(v) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Calories") },
                suffix = { Text("kcal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroField("Protein", state.protein, actions.onProtein, Modifier.weight(1f))
                MacroField("Carbs", state.carbs, actions.onCarbs, Modifier.weight(1f))
                MacroField("Fat", state.fat, actions.onFat, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FoodSuggestion(food: FoodEntry, onClick: () -> Unit) {
    val perServing = (food.kcal * food.servingG / 100).roundToInt()
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${food.servingLabel} · about ${food.servingG} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${formatKcal(perServing)} kcal", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AlternativesCard(alternatives: List<Alternative>, onSelect: (Alternative) -> Unit) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Not right? It might be", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                alternatives.forEach { alt ->
                    SuggestionChip(onClick = { onSelect(alt) }, label = { Text(alt.label) })
                }
            }
        }
    }
}

@Composable
private fun PortionCard(state: EditorState, onPortion: (Float) -> Unit) {
    val dish = state.dish ?: return
    val grams = dish.scaled(state.portionFactor).items.sumOf { it.grams }
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Portion", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "about $grams g",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PortionFactors.forEach { factor ->
                    FilterChip(
                        selected = state.portionFactor == factor,
                        onClick = { onPortion(factor) },
                        label = { Text(portionLabel(factor)) },
                    )
                }
            }
            Text(
                "Photos can't show how much food there is, so pick the size that matches your plate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun portionLabel(factor: Float): String = when (factor) {
    0.5f -> "½×"
    0.75f -> "¾×"
    1f -> "1×"
    1.5f -> "1½×"
    2f -> "2×"
    else -> "${factor}×"
}

@Composable
private fun MacroField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 4 && v.all(Char::isDigit)) onChange(v) },
        modifier = modifier,
        label = { Text(label) },
        suffix = { Text("g") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun RefineCard(state: EditorState, actions: EditorActions) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Not quite right?", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tell the AI what it missed and it will work it out again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.hint,
                onValueChange = actions.onHint,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. oat-milk latte, no sugar; half portion") },
                minLines = 1,
                maxLines = 3,
            )
            OutlinedButton(onClick = actions.onAnalyze, enabled = state.canReanalyze, shape = RoundedCornerShape(50)) {
                Text("Re-estimate")
            }
        }
    }
}
