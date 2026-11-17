package com.mealsnap.ui.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.mealsnap.ai.NanoStatus
import com.mealsnap.data.AiModel
import com.mealsnap.data.SettingsRepository
import com.mealsnap.ui.components.FitCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Testing : KeyCheck
    data class Result(val ok: Boolean, val message: String) : KeyCheck
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
    var claudeOpen by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<KeyCheck>(KeyCheck.Idle) }
    var nano by remember { mutableStateOf<NanoStatus?>(null) }
    var nanoDownloading by remember { mutableStateOf(false) }
    var nanoError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apiKey = settings.apiKey.first()
        goal = settings.dailyGoal.first().toString()
        model = settings.model.first()
        autoLog = settings.autoLog.first()
        claudeOpen = apiKey.isNotBlank()
        nano = app.container.nano.status()
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

        // --- On-device recognition --------------------------------------------------------------
        FitCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Food recognition", style = MaterialTheme.typography.titleLarge)
                Text(
                    "MealSnap identifies your food right on your phone, with no account, key or internet needed. " +
                        "Your photos never leave the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                EngineRow(
                    title = "Food recognition",
                    subtitle = "Recognises about 2,000 dishes and looks up their calories in a built-in table.",
                    badge = "Ready",
                    good = true,
                )

                val status = nano
                EngineRow(
                    title = "Gemini Nano (Pixel AI)",
                    subtitle = when (status) {
                        NanoStatus.Available -> "Reads whole plates and estimates each item's weight."
                        NanoStatus.Downloadable -> "Optional. A one-time download lets it read whole plates with several items."
                        NanoStatus.Downloading -> "Downloading the model…"
                        is NanoStatus.Unavailable -> "Optional extra. Not available on this phone, which is fine."
                        null -> "Checking…"
                    },
                    badge = when (status) {
                        NanoStatus.Available -> "Ready"
                        NanoStatus.Downloadable -> "Not downloaded"
                        NanoStatus.Downloading -> "Downloading"
                        is NanoStatus.Unavailable -> "Unavailable"
                        null -> "…"
                    },
                    good = status == NanoStatus.Available,
                )
                if (status == NanoStatus.Downloadable || nanoDownloading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = {
                                nanoError = null
                                nanoDownloading = true
                                scope.launch {
                                    try {
                                        app.container.nano.download().collect { }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Throwable) {
                                        nanoError = e.message ?: "The download failed."
                                    }
                                    nanoDownloading = false
                                    nano = app.container.nano.status()
                                }
                            },
                            enabled = !nanoDownloading,
                            shape = RoundedCornerShape(50),
                        ) { Text(if (nanoDownloading) "Downloading…" else "Download Gemini Nano") }
                        if (nanoDownloading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    }
                    Text(
                        "It's a large file, so use Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                nanoError?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // --- Goal ------------------------------------------------------------------------------
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

        // --- Logging ---------------------------------------------------------------------------
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

        // --- Optional Claude -------------------------------------------------------------------
        FitCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "More detail with Claude (optional)",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { claudeOpen = !claudeOpen }) { Text(if (claudeOpen) "Hide" else "Show") }
                }
                Text(
                    "You don't need this. If you already have an Anthropic API key, MealSnap can send photos to Claude " +
                        "for a more detailed breakdown of mixed plates. Leave it empty to stay fully on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (claudeOpen) {
                    FilledTonalButton(onClick = { uriHandler.openUri(CONSOLE_KEYS_URL) }, shape = RoundedCornerShape(50)) {
                        Text("Open Anthropic Console")
                    }
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
                        TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide key" else "Show key") }
                    }
                    Column(Modifier.selectableGroup()) {
                        AiModel.entries.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(selected = option == model, onClick = { model = option; check = KeyCheck.Idle }, role = Role.RadioButton)
                                    .padding(vertical = 6.dp),
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
                    Text(
                        "Each photo costs roughly 1 to 2 US cents on the default model, billed by Anthropic to your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // --- Save ------------------------------------------------------------------------------
        Button(
            onClick = {
                scope.launch {
                    settings.save(apiKey, goal.toIntOrNull() ?: SettingsRepository.DEFAULT_GOAL, model, autoLog)
                    goal = settings.dailyGoal.first().toString()
                    if (apiKey.isBlank()) {
                        check = KeyCheck.Result(true, "Saved.")
                    } else {
                        check = KeyCheck.Testing
                        check = try {
                            app.container.analyzer.verifyKey(apiKey, model.id)
                            KeyCheck.Result(true, "Saved. Your Claude key works, so photos will use Claude.")
                        } catch (e: AnalysisException) {
                            KeyCheck.Result(false, "Saved, but the key didn't work: ${e.message}")
                        }
                    }
                }
            },
            enabled = check != KeyCheck.Testing,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (check == KeyCheck.Testing) "Checking key…" else "Save",
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        (check as? KeyCheck.Result)?.let { result ->
            Text(
                result.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EngineRow(title: String, subtitle: String, badge: String, good: Boolean) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = if (good) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                badge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (good) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
