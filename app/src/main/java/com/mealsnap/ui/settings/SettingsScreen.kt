package com.mealsnap.ui.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mealsnap.MealSnapApplication
import com.mealsnap.ai.AnalysisException
import com.mealsnap.data.AiModel
import com.mealsnap.data.SettingsRepository
import com.mealsnap.ui.components.FitCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Testing : KeyCheck
    data object Ok : KeyCheck
    data class Failed(val message: String) : KeyCheck
}

private const val CONSOLE_KEYS_URL = "https://console.anthropic.com/settings/keys"

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MealSnapApplication
    val settings = app.container.settings
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var apiKey by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf(SettingsRepository.DEFAULT_GOAL.toString()) }
    var model by remember { mutableStateOf(AiModel.OPUS) }
    var autoLog by remember { mutableStateOf(true) }
    var showKey by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<KeyCheck>(KeyCheck.Idle) }

    LaunchedEffect(Unit) {
        apiKey = settings.apiKey.first()
        goal = settings.dailyGoal.first().toString()
        model = settings.model.first()
        autoLog = settings.autoLog.first()
    }

    fun pasteKey() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            apiKey = text
            check = KeyCheck.Idle
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Settings",
            modifier = Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp),
            style = MaterialTheme.typography.headlineLarge,
        )

        // --- Automatic calories -------------------------------------------------------------
        FitCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Automatic calories",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    val connected = check == KeyCheck.Ok
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            when {
                                connected -> "Connected ✓"
                                apiKey.isBlank() -> "Needs setup"
                                else -> "Key added"
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "MealSnap uses Claude to recognise your food from the photo and count the calories. " +
                        "That needs an Anthropic API key, set up once:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                StepTitle("1. Get a key")
                Text(
                    "Sign in at the Anthropic Console, add a few dollars of credit under Billing " +
                        "(API access is billed separately from a Claude.ai subscription), then create a key.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = { uriHandler.openUri(CONSOLE_KEYS_URL) }, shape = RoundedCornerShape(50)) {
                    Text("Open Anthropic Console")
                }

                StepTitle("2. Paste it here")
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim(); check = KeyCheck.Idle },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key (sk-ant-…)") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = ::pasteKey, shape = RoundedCornerShape(50)) { Text("Paste from clipboard") }
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                }

                StepTitle("3. Save and test")
                Text(
                    "Each photo costs roughly 1 to 2 US cents on the default model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Goal ----------------------------------------------------------------------------
        FitCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Daily calorie goal", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = goal,
                    onValueChange = { v -> if (v.length <= 5 && v.all(Char::isDigit)) goal = v },
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("kcal") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }

        // --- Logging -------------------------------------------------------------------------
        FitCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Log meals automatically", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Save a meal to Today as soon as the photo has been identified. " +
                            "You can still edit or delete it afterwards.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoLog, onCheckedChange = { autoLog = it })
            }
        }

        // --- Model ---------------------------------------------------------------------------
        FitCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("AI model", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.selectableGroup()) {
                    AiModel.entries.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = option == model, onClick = { model = option; check = KeyCheck.Idle }, role = Role.RadioButton)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == model, onClick = null)
                            Column(Modifier.padding(start = 14.dp)) {
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
            }
        }

        // --- Save ----------------------------------------------------------------------------
        Button(
            onClick = {
                scope.launch {
                    settings.save(apiKey, goal.toIntOrNull() ?: SettingsRepository.DEFAULT_GOAL, model, autoLog)
                    goal = settings.dailyGoal.first().toString()
                    if (apiKey.isBlank()) {
                        check = KeyCheck.Failed("Saved. Add an API key to turn on automatic calories.")
                    } else {
                        check = KeyCheck.Testing
                        check = try {
                            app.container.analyzer.verifyKey(apiKey, model.id)
                            KeyCheck.Ok
                        } catch (e: AnalysisException) {
                            KeyCheck.Failed(e.message ?: "Couldn't verify the key.")
                        }
                    }
                }
            },
            enabled = check != KeyCheck.Testing,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (check == KeyCheck.Testing) "Testing…" else "Save and test",
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        when (val result = check) {
            KeyCheck.Ok -> Text(
                "Saved. You're all set: take a photo and the calories are counted for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            is KeyCheck.Failed -> Text(
                result.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Unit
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
