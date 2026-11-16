package com.mealsnap.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mealsnap.MealSnapApplication
import com.mealsnap.data.AiModel
import com.mealsnap.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val app = LocalContext.current.applicationContext as MealSnapApplication
    val settings = app.container.settings
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf(SettingsRepository.DEFAULT_GOAL.toString()) }
    var model by remember { mutableStateOf(AiModel.OPUS) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apiKey = settings.apiKey.first()
        goal = settings.dailyGoal.first().toString()
        model = settings.model.first()
    }
    LaunchedEffect(saved) {
        if (saved) {
            delay(2_500)
            saved = false
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Settings",
            modifier = Modifier.statusBarsPadding().padding(top = 16.dp),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Text("Daily calorie goal", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = goal,
            onValueChange = { v -> if (v.length <= 5 && v.all(Char::isDigit)) goal = v },
            modifier = Modifier.fillMaxWidth(),
            suffix = { Text("kcal") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Text("Anthropic API key", style = MaterialTheme.typography.titleMedium)
        Text(
            "Photos are analysed by Claude. Create a key at console.anthropic.com and paste it here. " +
                "It stays on this phone and is only sent to Anthropic with each photo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("sk-ant-…") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
            },
        )

        Text("AI model", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            AiModel.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = option == model, onClick = { model = option }, role = Role.RadioButton)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == model, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            option.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    settings.save(apiKey, goal.toIntOrNull() ?: SettingsRepository.DEFAULT_GOAL, model)
                    goal = settings.dailyGoal.first().toString()
                    saved = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saved) "Saved ✓" else "Save")
        }
    }
}
