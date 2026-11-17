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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.mealsnap.ai.FoodItem
import com.mealsnap.data.MealType
import com.mealsnap.ui.components.formatKcal
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditorScreen(mealId: Long, photo: String, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as MealSnapApplication
    val vm: MealEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { MealEditorViewModel(app.container, mealId, photo) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) { if (state.finished) onClose() }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New meal" else "Edit meal") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete meal")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = vm::save,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    Text(if (state.isNew) "Save meal" else "Save changes")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.photoPath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = "Meal photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(20.dp)),
                )
            }

            StatusCard(state.status, onRetry = vm::analyze)

            OutlinedTextField(
                value = state.name,
                onValueChange = vm::setName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Meal name") },
                singleLine = true,
            )

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = state.mealType == type,
                        onClick = { vm.setMealType(type) },
                        label = { Text(type.label) },
                    )
                }
            }

            OutlinedTextField(
                value = state.calories,
                onValueChange = { v -> if (v.length <= 5 && v.all(Char::isDigit)) vm.setCalories(v) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Calories") },
                suffix = { Text("kcal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroField("Protein", state.protein, vm::setProtein, Modifier.weight(1f))
                MacroField("Carbs", state.carbs, vm::setCarbs, Modifier.weight(1f))
                MacroField("Fat", state.fat, vm::setFat, Modifier.weight(1f))
            }

            if (state.items.isNotEmpty()) {
                Breakdown(state.items)
            }

            if (state.photoPath != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Improve the estimate", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = state.hint,
                            onValueChange = vm::setHint,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. oat-milk latte, no sugar") },
                            minLines = 1,
                            maxLines = 3,
                        )
                        OutlinedButton(onClick = vm::analyze, enabled = state.canReanalyze) {
                            Text("Re-estimate with AI")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this meal?") },
            text = { Text("It will be removed from your log and from today's total.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
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
private fun StatusCard(status: AnalysisStatus, onRetry: () -> Unit) {
    when (status) {
        AnalysisStatus.Idle -> Unit

        AnalysisStatus.Preparing, AnalysisStatus.Analyzing -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(
                    if (status == AnalysisStatus.Preparing) "Preparing photo…" else "Estimating calories…",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        AnalysisStatus.NoApiKey -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("No API key yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Add your Anthropic API key in Settings to get automatic estimates. " +
                        "For now you can enter the calories yourself below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }

        is AnalysisStatus.Failed -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(status.message, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }

        is AnalysisStatus.Done -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "AI estimate · ${status.confidence} confidence",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (status.notes.isNotBlank()) {
                    Text(status.notes, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Estimates can be off. Adjust the numbers below if you know better.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Breakdown(items: List<FoodItem>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What's on the plate", style = MaterialTheme.typography.titleSmall)
            items.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                        if (item.portion.isNotBlank()) {
                            Text(
                                item.portion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "${formatKcal(item.calories)} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
