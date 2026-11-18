package com.mealsnap.ui.coach

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mealsnap.MealSnapApplication
import com.mealsnap.coach.BodyMetrics
import com.mealsnap.coach.HealthDay
import com.mealsnap.coach.RuleCoach
import com.mealsnap.coach.Tip
import com.mealsnap.coach.TipKind
import com.mealsnap.coach.WeightPoint
import com.mealsnap.health.HealthStatus
import com.mealsnap.ui.components.FitCard
import com.mealsnap.ui.components.GradientRing
import com.mealsnap.ui.components.SectionTitle
import com.mealsnap.ui.components.formatKcal
import com.mealsnap.ui.theme.extras
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Everything the Coach screen can ask for, so the screen itself stays stateless and previewable. */
class CoachActions(
    val onEditProfile: () -> Unit = {},
    val onApplyGoal: () -> Unit = {},
    val onGetBriefing: () -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onAllowHealth: () -> Unit = {},
    val onOpenHealthConnect: () -> Unit = {},
    val onDataCheck: () -> Unit = {},
    val onLogWeight: (Double) -> Unit = {},
    val onAcceptConsent: () -> Unit = {},
    val onDismissConsent: () -> Unit = {},
)

@Composable
fun CoachScreen(onEditProfile: () -> Unit, onOpenChat: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MealSnapApplication
    val vm: CoachViewModel = viewModel(factory = viewModelFactory { initializer { CoachViewModel(app.container) } })
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.refresh()
    }

    CoachContent(
        state = state,
        actions = CoachActions(
            onEditProfile = onEditProfile,
            onApplyGoal = vm::applyRecommendedGoal,
            onGetBriefing = vm::requestBriefing,
            onOpenChat = onOpenChat,
            onOpenSettings = onOpenSettings,
            onAllowHealth = { permissionLauncher.launch(vm.healthPermissions) },
            onOpenHealthConnect = {
                runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
            },
            onDataCheck = vm::runDataCheck,
            onLogWeight = vm::logWeight,
            onAcceptConsent = vm::acceptConsent,
            onDismissConsent = vm::dismissConsent,
        ),
    )
}

@Composable
fun CoachContent(state: CoachUiState, actions: CoachActions) {
    var showWeightDialog by remember { mutableStateOf(false) }
    val load = state.load

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp, bottom = 2.dp)) {
                Text("Coach", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Your personal trainer, using your own numbers",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (load == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            return@LazyColumn
        }

        if (!load.profile.isComplete) {
            item { ProfilePromptCard(actions.onEditProfile) }
        } else {
            item { BriefingCard(state, actions) }
            item { NumbersCard(state, actions) }
        }
        item { HealthCard(state, actions) }
        if (load.profile.isComplete) {
            item { WeightCard(load.weights, load.profile.targetWeightKg) { showWeightDialog = true } }
            item { ChatCard(state.hasGeminiKey, actions) }
        }
        item {
            Text(
                "General wellness guidance based on your numbers. It is not medical advice, so speak to a doctor about " +
                    "any health concern.",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.needsConsent) ConsentDialog(actions.onAcceptConsent, actions.onDismissConsent)
    if (showWeightDialog) {
        WeightDialog(
            initialKg = load?.profile?.weightKg,
            onSave = { showWeightDialog = false; actions.onLogWeight(it) },
            onDismiss = { showWeightDialog = false },
        )
    }
}

// ---- cards ------------------------------------------------------------------------------------

@Composable
private fun ProfilePromptCard(onEditProfile: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Set up your profile", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "Tell me your height, weight, age and goal and I'll work out safe calorie, protein, heart-rate and step targets, " +
                    "then coach you using your meals, sleep and heart rate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onEditProfile, shape = RoundedCornerShape(50)) { Text("Set up profile") }
        }
    }
}

@Composable
private fun BriefingCard(state: CoachUiState, actions: CoachActions) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's briefing", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                val gemini = state.briefing is BriefingState.Ready
                Pill(if (gemini) "Google Gemini" else "From your numbers")
            }

            when (val b = state.briefing) {
                BriefingState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("Your trainer is looking at your day…", style = MaterialTheme.typography.bodyLarge)
                }
                is BriefingState.Ready -> {
                    val brief = b.briefing
                    if (brief.summary.isNotBlank()) Text(brief.summary, style = MaterialTheme.typography.bodyLarge)
                    brief.tips.forEach { TipRow(it) }
                    if (brief.workout.isNotBlank()) LabeledBlock("Today's workout", brief.workout)
                    if (brief.mealIdea.isNotBlank()) LabeledBlock("Next meal idea", brief.mealIdea)
                    OutlinedButton(onClick = actions.onGetBriefing, shape = RoundedCornerShape(50)) { Text("Refresh briefing") }
                }
                else -> {
                    if (b is BriefingState.Failed) {
                        Text(b.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    state.tips.forEach { TipRow(it) }
                    if (state.hasGeminiKey) {
                        FilledTonalButton(onClick = actions.onGetBriefing, shape = RoundedCornerShape(50)) { Text("Get my AI briefing") }
                        Text(
                            "Sends a summary of your profile, meals and health data to Google Gemini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Text(
                            "These tips come from simple rules on your own numbers. Add a free Google key for a personal AI " +
                                "briefing, a workout and meal idea for today, and a chat with your trainer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(onClick = actions.onOpenSettings, shape = RoundedCornerShape(50)) { Text("Add a free Google key") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipRow(tip: Tip) {
    val color = when (tip.kind) {
        TipKind.GOOD -> MaterialTheme.colorScheme.primary
        TipKind.WATCH -> MaterialTheme.extras.ringOver
        TipKind.INFO -> MaterialTheme.colorScheme.outline
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 7.dp).size(10.dp).background(color, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tip.title, style = MaterialTheme.typography.titleMedium)
            Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LabeledBlock(label: String, text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun NumbersCard(state: CoachUiState, actions: CoachActions) {
    val load = state.load ?: return
    val profile = load.profile
    val rec = load.recommended ?: return
    val bmi = BodyMetrics.bmi(profile.weightKg!!, profile.heightCm!!)
    val macros = load.inputs.macros

    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your numbers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = actions.onEditProfile) { Text("Edit profile") }
            }
            StatRow("BMI", "${"%.1f".format(Locale.US, bmi)} · ${BodyMetrics.bmiCategory(bmi).label}")
            StatRow("Resting burn", "${formatKcal(rec.bmr)} kcal a day")
            StatRow("Daily burn with your activity", "${formatKcal(rec.tdee)} kcal")
            StatRow("Suggested intake", "${formatKcal(rec.kcal)} kcal for \"${profile.goal.label.lowercase(Locale.US)}\"")
            if (macros != null) {
                StatRow("Protein · carbs · fat", "${macros.proteinG} g · ${macros.carbsG} g · ${macros.fatG} g")
            }
            StatRow("Water", "about ${formatKcal(BodyMetrics.waterMl(profile.weightKg))} ml")
            StatRow("Steps", "${formatKcal(BodyMetrics.stepGoal(profile.goal))} a day")
            BodyMetrics.weeksToTarget(profile)?.let { StatRow("To reach ${"%.0f".format(Locale.US, profile.targetWeightKg)} kg", "about $it weeks at a safe pace") }

            rec.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rec.kcal != load.goalKcal) {
                FilledTonalButton(onClick = actions.onApplyGoal, shape = RoundedCornerShape(50)) {
                    Text("Use ${formatKcal(rec.kcal)} kcal as my daily goal")
                }
                Text(
                    "Your daily goal is currently ${formatKcal(load.goalKcal)} kcal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1.2f))
    }
}

// ---- health data ------------------------------------------------------------------------------

@Composable
private fun HealthCard(state: CoachUiState, actions: CoachActions) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Health data", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Pill(
                    when (state.healthStatus) {
                        HealthStatus.CONNECTED -> "Connected"
                        HealthStatus.NEEDS_PERMISSION -> "Not allowed yet"
                        HealthStatus.NEEDS_UPDATE -> "Update needed"
                        HealthStatus.UNAVAILABLE -> "Unavailable"
                    },
                )
            }

            when (state.healthStatus) {
                HealthStatus.UNAVAILABLE -> Text(
                    "Health Connect isn't available on this phone, so heart rate, sleep and steps can't be read. " +
                        "You can still log weight and meals.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HealthStatus.NEEDS_UPDATE -> Text(
                    "Health Connect needs an update in the Google Play Store before MealSnap can read your health data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HealthStatus.NEEDS_PERMISSION -> {
                    Text(
                        "Connect your Fitbit Air (or any tracker that shares with Health Connect) to coach you with your real " +
                            "heart rate, sleep and activity. It all stays on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "1. Pair the Fitbit Air in the Google Health app.\n" +
                            "2. In that app, turn on sharing with Health Connect (look for Health Connect in its settings).\n" +
                            "3. Tap Allow health data below and choose what MealSnap may read.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = actions.onAllowHealth, shape = RoundedCornerShape(50)) { Text("Allow health data") }
                    TextButton(onClick = actions.onOpenHealthConnect) { Text("Open Health Connect") }
                }
                HealthStatus.CONNECTED -> {
                    val today = state.load?.health?.today
                    val goal = state.load?.profile?.goal?.let { BodyMetrics.stepGoal(it) } ?: 8000
                    HealthTiles(today, goal)
                    state.load?.health?.today?.let { d ->
                        if (d.avgHr != null && d.minHr != null && d.maxHr != null) {
                            Text(
                                "Heart rate today: average ${d.avgHr}, range ${d.minHr} to ${d.maxHr} bpm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (today == null || !today.hasAnything) {
                        Text(
                            "Connected, but nothing has arrived yet. Open the Google Health app so your Fitbit Air syncs, and make " +
                                "sure sharing with Health Connect is on. Then check what's arriving below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = actions.onDataCheck, enabled = !state.checkingData, shape = RoundedCornerShape(50)) {
                            Text(if (state.checkingData) "Checking…" else "Check what's arriving")
                        }
                        TextButton(onClick = actions.onOpenHealthConnect) { Text("Manage") }
                    }
                    state.dataCheck?.let { DataCheckTable(it) }
                }
            }
        }
    }
}

@Composable
private fun HealthTiles(day: HealthDay?, stepGoal: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val steps = day?.steps
        Tile(
            Modifier.weight(1f), "Steps", steps?.let { formatKcal(it.toInt()) } ?: "–", "of ${formatKcal(stepGoal)}",
            progress = steps?.let { (it.toFloat() / stepGoal).coerceIn(0f, 1f) },
        )
        Tile(Modifier.weight(1f), "Resting heart rate", day?.restingHr?.toString() ?: "–", "bpm")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(Modifier.weight(1f), "Sleep", day?.sleepMinutes?.let { RuleCoach.hm(it) } ?: "–", "last night")
        Tile(Modifier.weight(1f), "Active energy", day?.activeKcal?.let { formatKcal(it.roundToInt()) } ?: "–", "kcal")
    }
}

@Composable
private fun Tile(modifier: Modifier, label: String, value: String, unit: String, progress: Float? = null) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.background) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge)
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (progress != null) GradientRing(progress, Modifier.size(36.dp), 5.dp)
        }
    }
}

@Composable
private fun DataCheckTable(rows: List<com.mealsnap.health.DataCheckRow>) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Received in the last 7 days", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (rows.isEmpty()) {
                Text("Nothing could be read. Check the permissions in Health Connect.", style = MaterialTheme.typography.bodyMedium)
            }
            rows.forEach { row ->
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (row.count == 0) "nothing yet" else "${row.count}${if (row.moreAvailable) "+" else ""} records",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (row.count == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (row.sources.isNotEmpty()) {
                        Text(
                            "from " + row.sources.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---- weight -----------------------------------------------------------------------------------

@Composable
private fun WeightCard(weights: List<WeightPoint>, target: Double?, onLog: () -> Unit) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Weight", style = MaterialTheme.typography.titleLarge)
            val last = weights.lastOrNull()
            if (last == null) {
                Text(
                    "Log your weight now and then, and I'll show your trend and coach your pace.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("%.1f".format(Locale.US, last.kg), style = MaterialTheme.typography.displaySmall)
                    Text("kg", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val first = weights.first()
                    if (first !== last) {
                        val diff = last.kg - first.kg
                        Text(
                            "  ${if (diff >= 0) "+" else "−"}${"%.1f".format(Locale.US, kotlin.math.abs(diff))} kg since ${first.date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                if (weights.size >= 2) WeightChart(weights, target)
                if (target != null) {
                    Text(
                        "Target ${"%.1f".format(Locale.US, target).removeSuffix(".0")} kg · ${"%.1f".format(Locale.US, kotlin.math.abs(last.kg - target)).removeSuffix(".0")} kg to go",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(onClick = onLog, shape = RoundedCornerShape(50)) { Text("Log weight") }
        }
    }
}

@Composable
private fun WeightChart(points: List<WeightPoint>, target: Double?) {
    val line = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.outline
    val dot = MaterialTheme.extras.ringStart
    // Scale to the person's own weigh-ins so the trend is visible; the target line only appears when it's in range.
    val values = points.map { it.kg }
    val padding = maxOf(0.4, (values.max() - values.min()) * 0.15)
    val low = values.min() - padding
    val high = values.max() + padding
    val firstDay = points.first().date.toEpochDay()
    val span = (points.last().date.toEpochDay() - firstDay).coerceAtLeast(1)

    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        fun x(p: WeightPoint) = ((p.date.toEpochDay() - firstDay).toFloat() / span) * (size.width - 16.dp.toPx()) + 8.dp.toPx()
        fun y(kg: Double) = (size.height - ((kg - low) / (high - low)).toFloat() * size.height)

        if (target != null && target in low..high) {
            val ty = y(target)
            drawLine(targetColor, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 12f)))
        }
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p), y(p.kg)) else path.lineTo(x(p), y(p.kg)) }
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { drawCircle(dot, 4.dp.toPx(), Offset(x(it), y(it.kg))) }
    }
}

// ---- chat card and dialogs ----------------------------------------------------------------------

@Composable
private fun ChatCard(hasKey: Boolean, actions: CoachActions) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ask your trainer", style = MaterialTheme.typography.titleLarge)
            Text(
                "Ask anything: a workout for today, what to eat next, how your week is going. It answers using your own meals, " +
                    "heart rate, sleep and weight.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasKey) {
                Button(onClick = actions.onOpenChat, shape = RoundedCornerShape(50)) { Text("Open chat") }
            } else {
                Text(
                    "Chat uses Google Gemini and needs a free Google key.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = actions.onOpenSettings, shape = RoundedCornerShape(50)) { Text("Add a free Google key") }
            }
        }
    }
}

@Composable
fun ConsentDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share a health summary with Gemini?") },
        text = {
            Text(
                "To coach you, MealSnap sends Google Gemini a text summary of your profile (age, height, weight, goal), recent " +
                    "meals, and any steps, heart rate, sleep and weight data it can read. No photos or name are sent. On Google's " +
                    "free tier, Google may use what you send to improve its products. You can turn this off any time in Settings.",
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Share and continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun WeightDialog(initialKg: Double?, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialKg?.let { "%.1f".format(Locale.US, it) }.orEmpty()) }
    val kg = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log your weight") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() || it == '.' || it == ',' }) text = v },
                suffix = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { kg?.let(onSave) }, enabled = kg != null && kg in 20.0..400.0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
