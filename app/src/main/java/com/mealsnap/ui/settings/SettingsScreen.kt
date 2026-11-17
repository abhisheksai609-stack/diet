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
import com.mealsnap.data.GeminiChoice
import com.mealsnap.data.SettingsRepository
import com.mealsnap.ui.components.FitCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class CheckResult(val ok: Boolean, val message: String)

private const val CONSOLE_KEYS_URL = "https://console.anthropic.com/settings/keys"
private const val AI_STUDIO_KEYS_URL = "https://aistudio.google.com/apikey"

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
    var geminiKey by remember { mutableStateOf("") }
    var geminiChoice by remember { mutableStateOf(GeminiChoice.LITE) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var geminiOpen by remember { mutableStateOf(true) }
    var coachConsent by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<CheckResult>>(emptyList()) }
    var nano by remember { mutableStateOf<NanoStatus?>(null) }
    var nanoDownloading by remember { mutableStateOf(false) }
    var nanoError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apiKey = settings.apiKey.first()
        goal = settings.dailyGoal.first().toString()
        model = settings.model.first()
        autoLog = settings.autoLog.first()
        geminiKey = settings.geminiKey.first()
        geminiChoice = settings.geminiModel.first()
        coachConsent = settings.coachConsent.first()
        claudeOpen = apiKey.isNotBlank()
        nano = app.container.nano.status()
    }

    /** Reads the clipboard and hands the text to [onText] (used by both key fields). */
    fun pasteInto(onText: (String) -> Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            onText(text)
            results = emptyList()
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
                        "Photos only leave the device if you add a Google or Anthropic key below.",
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

        // --- Personal trainer privacy ----------------------------------------------------------
        FitCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Share health summary with Gemini", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Lets your AI trainer use your profile, meals and any heart-rate, sleep and weight data it can read. " +
                            "Only a text summary is sent, only when you ask for a briefing or chat, and only if you've added a Google key.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = coachConsent,
                    onCheckedChange = {
                        coachConsent = it
                        scope.launch { settings.setCoachConsent(it) }
                    },
                )
            }
        }

        // --- Optional Google Gemini ------------------------------------------------------------
        FitCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Smarter recognition with Google Gemini",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { geminiOpen = !geminiOpen }) { Text(if (geminiOpen) "Hide" else "Show") }
                }
                Text(
                    "Optional and free. With a Google AI Studio key, Gemini reads your photo, names every item on the plate " +
                        "and estimates each portion. If the free quota runs out or you're offline, MealSnap switches back to " +
                        "on-device recognition by itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (geminiOpen) {
                    Text("1. Get a free key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Sign in with your Google account, tap Create API key, then copy the key.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(onClick = { uriHandler.openUri(AI_STUDIO_KEYS_URL) }, shape = RoundedCornerShape(50)) {
                        Text("Open Google AI Studio")
                    }
                    Text("2. Paste it here", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it.trim(); results = emptyList() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Google API key") },
                        singleLine = true,
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { pasteInto { geminiKey = it } }, shape = RoundedCornerShape(50)) {
                            Text("Paste from clipboard")
                        }
                        TextButton(onClick = { showGeminiKey = !showGeminiKey }) { Text(if (showGeminiKey) "Hide key" else "Show key") }
                    }
                    Text("3. Pick a model, then Save", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.selectableGroup()) {
                        GeminiChoice.entries.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(selected = option == geminiChoice, onClick = { geminiChoice = option; results = emptyList() }, role = Role.RadioButton)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = option == geminiChoice, onClick = null)
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
                        "Privacy: with a key set, each photo is sent to Google for analysis. On Google's free tier, Google may " +
                            "use what you send to improve its products. Remove the key to stay fully on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                        onValueChange = { apiKey = it.trim(); results = emptyList() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key (sk-ant-…)") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { pasteInto { apiKey = it } }, shape = RoundedCornerShape(50)) { Text("Paste from clipboard") }
                        TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide key" else "Show key") }
                    }
                    Column(Modifier.selectableGroup()) {
                        AiModel.entries.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(selected = option == model, onClick = { model = option; results = emptyList() }, role = Role.RadioButton)
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
                    settings.save(
                        apiKey = apiKey,
                        dailyGoal = goal.toIntOrNull() ?: SettingsRepository.DEFAULT_GOAL,
                        model = model,
                        autoLog = autoLog,
                        geminiKey = geminiKey,
                        geminiModel = geminiChoice,
                    )
                    goal = settings.dailyGoal.first().toString()
                    testing = true
                    val found = mutableListOf<CheckResult>()
                    if (geminiKey.isNotBlank()) {
                        found += try {
                            val used = app.container.gemini.verifyKey(geminiKey, geminiChoice.id)
                            CheckResult(true, "Google Gemini works (using $used). Photos will be read by Gemini.")
                        } catch (e: AnalysisException) {
                            CheckResult(false, "Google Gemini: ${e.message}")
                        }
                    }
                    if (apiKey.isNotBlank()) {
                        found += try {
                            app.container.analyzer.verifyKey(apiKey, model.id)
                            CheckResult(true, "Your Claude key works.")
                        } catch (e: AnalysisException) {
                            CheckResult(false, "Claude: ${e.message}")
                        }
                    }
                    results = found.ifEmpty { listOf(CheckResult(true, "Saved. Photos are recognised on your phone.")) }
                    testing = false
                }
            },
            enabled = !testing,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (testing) "Checking…" else "Save",
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        results.forEach { result ->
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
