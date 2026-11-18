package com.mealsnap.ui.coach

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mealsnap.MealSnapApplication
import com.mealsnap.coach.ActivityLevel
import com.mealsnap.coach.BodyMetrics
import com.mealsnap.coach.Goal
import com.mealsnap.coach.Profile
import com.mealsnap.coach.Sex
import com.mealsnap.data.WeightEntry
import com.mealsnap.ui.components.FitCard
import com.mealsnap.ui.components.formatKcal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

@Composable
fun ProfileScreen(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as MealSnapApplication
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<Pair<Profile, Int>?>(null) }

    LaunchedEffect(Unit) { loaded = app.container.settings.profile.first() to app.container.settings.dailyGoal.first() }

    val data = loaded
    if (data == null) {
        Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    ProfileContent(
        initial = data.first,
        currentGoalKcal = data.second,
        onBack = onDone,
        onSave = { profile, useAsGoal ->
            scope.launch {
                val container = app.container
                val previous = container.settings.profile.first()
                container.settings.saveProfile(profile)
                // A changed weight is also a weigh-in, so the trend and the coach see it.
                if (profile.weightKg != null && profile.weightKg != previous.weightKg) {
                    container.weightDao.insert(WeightEntry(timeMillis = System.currentTimeMillis(), kg = profile.weightKg))
                }
                if (useAsGoal) BodyMetrics.calorieGoal(profile)?.let { container.settings.setDailyGoal(it.kcal) }
                onDone()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileContent(
    initial: Profile,
    currentGoalKcal: Int,
    onBack: () -> Unit,
    onSave: (Profile, Boolean) -> Unit,
) {
    var height by remember { mutableStateOf(initial.heightCm?.let { fmt(it) }.orEmpty()) }
    var weight by remember { mutableStateOf(initial.weightKg?.let { fmt(it) }.orEmpty()) }
    var birthYear by remember { mutableStateOf(initial.birthYear?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(initial.sex) }
    var activity by remember { mutableStateOf(initial.activity) }
    var goal by remember { mutableStateOf(initial.goal) }
    var target by remember { mutableStateOf(initial.targetWeightKg?.let { fmt(it) }.orEmpty()) }
    var useAsGoal by remember { mutableStateOf(true) }

    val profile = Profile(
        heightCm = num(height)?.takeIf { it in 100.0..250.0 },
        weightKg = num(weight)?.takeIf { it in 20.0..400.0 },
        birthYear = birthYear.toIntOrNull()?.takeIf { it in 1900..LocalDate.now().year - 5 },
        sex = sex,
        activity = activity,
        goal = goal,
        targetWeightKg = if (goal == Goal.MAINTAIN) null else num(target)?.takeIf { it in 20.0..400.0 },
    )
    val recommended = BodyMetrics.calorieGoal(profile)

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Your profile") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { onSave(profile, useAsGoal && recommended != null) },
                    enabled = profile.isComplete,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text("Save profile", modifier = Modifier.padding(vertical = 4.dp)) }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FitCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("About you", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Height", height, "cm", Modifier.weight(1f), decimal = true) { height = it }
                        NumberField("Weight", weight, "kg", Modifier.weight(1f), decimal = true) { weight = it }
                    }
                    NumberField("Year of birth", birthYear, "", Modifier.fillMaxWidth(), decimal = false, maxLength = 4) { birthYear = it }
                    Text("Sex (used in the calorie formula)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sex.entries.forEach { FilterChip(selected = sex == it, onClick = { sex = it }, label = { Text(it.label) }) }
                    }
                }
            }

            FitCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How active are you?", style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.selectableGroup()) {
                        ActivityLevel.entries.forEach { level ->
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = level == activity, onClick = { activity = level }, role = Role.RadioButton).padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = level == activity, onClick = null)
                                Column(Modifier.padding(start = 14.dp)) {
                                    Text(level.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(level.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            FitCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your goal", style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Goal.entries.forEach { FilterChip(selected = goal == it, onClick = { goal = it }, label = { Text(it.label) }) }
                    }
                    if (goal != Goal.MAINTAIN) {
                        NumberField("Target weight", target, "kg", Modifier.fillMaxWidth(), decimal = true) { target = it }
                    }
                }
            }

            if (recommended != null) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your targets", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "${formatKcal(recommended.kcal)} kcal a day",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        val macros = BodyMetrics.macroPlan(recommended.kcal, profile.weightKg!!, goal)
                        Text(
                            "Protein ${macros.proteinG} g · carbs ${macros.carbsG} g · fat ${macros.fatG} g\n" +
                                "Resting burn ${formatKcal(recommended.bmr)} kcal · with activity ${formatKcal(recommended.tdee)} kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        recommended.note?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Switch(checked = useAsGoal, onCheckedChange = { useAsGoal = it })
                            Text(
                                "Use this as my daily calorie goal (now ${formatKcal(currentGoalKcal)})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Text(
                "These are textbook estimates (Mifflin-St Jeor) for general guidance, not medical advice. Your profile stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    suffix: String,
    modifier: Modifier,
    decimal: Boolean,
    maxLength: Int = 6,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            if (v.length <= maxLength && v.all { it.isDigit() || (decimal && (it == '.' || it == ',')) }) onChange(v)
        },
        modifier = modifier,
        label = { Text(label) },
        suffix = if (suffix.isNotEmpty()) ({ Text(suffix) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

private fun num(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v).removeSuffix(".0")
