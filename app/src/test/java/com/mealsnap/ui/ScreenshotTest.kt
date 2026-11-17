package com.mealsnap.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mealsnap.ai.CoachParser
import com.mealsnap.ai.FoodItem
import com.mealsnap.ai.GeminiAnalyzer
import com.mealsnap.coach.ActivityLevel
import com.mealsnap.coach.BodyMetrics
import com.mealsnap.coach.CoachInputs
import com.mealsnap.coach.CoachLoad
import com.mealsnap.coach.DayNutrition
import com.mealsnap.coach.Goal
import com.mealsnap.coach.HealthDay
import com.mealsnap.coach.HealthSnapshot
import com.mealsnap.coach.MealTotals
import com.mealsnap.coach.Profile
import com.mealsnap.coach.RuleCoach
import com.mealsnap.coach.Sex
import com.mealsnap.coach.WeightPoint
import com.mealsnap.health.DataCheckRow
import com.mealsnap.health.HealthStatus
import com.mealsnap.ui.coach.BriefingState
import com.mealsnap.ui.coach.ChatContent
import com.mealsnap.ui.coach.ChatMessage
import com.mealsnap.ui.coach.ChatUiState
import com.mealsnap.ui.coach.CoachActions
import com.mealsnap.ui.coach.CoachContent
import com.mealsnap.ui.coach.CoachUiState
import com.mealsnap.ui.coach.ProfileContent
import java.time.LocalDateTime
import com.mealsnap.data.DayStats
import com.mealsnap.data.Meal
import com.mealsnap.data.MealType
import com.mealsnap.ui.editor.AnalysisStatus
import com.mealsnap.ui.editor.EditorActions
import com.mealsnap.ui.editor.EditorState
import com.mealsnap.ui.editor.MealEditorContent
import com.mealsnap.ui.history.HistoryContent
import com.mealsnap.ui.history.HistoryUiState
import com.mealsnap.ui.settings.SettingsScreen
import com.mealsnap.ui.theme.MealSnapTheme
import com.mealsnap.ui.today.TodayContent
import com.mealsnap.ui.today.TodayUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders the real screens with sample data and writes PNGs to app/build/screenshots.
 * Besides giving a look at the design, it proves every screen composes without crashing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 17)
    private val zone = ZoneId.systemDefault()

    private fun at(hour: Int, minute: Int = 0) =
        today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val sampleMeals = listOf(
        Meal(
            id = 1, createdAt = at(8, 15), mealType = MealType.BREAKFAST.name, name = "Avocado toast with poached egg",
            calories = 420, proteinG = 18, carbsG = 34, fatG = 24, aiEstimated = true,
        ),
        Meal(
            id = 2, createdAt = at(13, 5), mealType = MealType.LUNCH.name, name = "Chicken tikka masala with basmati rice",
            calories = 780, proteinG = 42, carbsG = 86, fatG = 28, aiEstimated = true,
        ),
        Meal(
            id = 3, createdAt = at(16, 30), mealType = MealType.SNACK.name, name = "Flat white",
            calories = 120, proteinG = 6, carbsG = 10, fatG = 6,
        ),
    )

    private val editorActions = EditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private val aiState = EditorState(
        loading = false, isSaved = true, dirty = false,
        name = "Chicken tikka masala with basmati rice", cuisine = "Indian",
        mealType = MealType.LUNCH, calories = "780", protein = "42", carbs = "86", fat = "28",
        fiberG = 6, sugarG = 9, sodiumMg = 1240, confidence = "medium", aiEstimated = true,
        status = AnalysisStatus.Done,
        notes = "Assumed a restaurant portion with about 2 tbsp of oil in the sauce.",
        items = listOf(
            FoodItem("Chicken tikka masala", "about 300 g, 1 large bowl", 520, 38, 14, 34),
            FoodItem("Basmati rice", "about 220 g cooked, 1.5 cups", 250, 5, 54, 1),
            FoodItem("Naan bread", "1 piece, ~60 g", 10, 0, 2, 0),
        ),
        visualCues = listOf(
            "Standard dinner plate, about 26 cm",
            "Sauce is glossy and orange-red, typical of tikka masala",
            "Rice fills roughly a third of the plate",
        ),
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            MealSnapTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
        rule.waitForIdle()
        val bitmap = captureRoot()
        val dir = File("build/screenshots").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name should render something", file.length() > 5_000)
    }

    /**
     * Compose's own captureToImage blocks the main thread waiting for a draw that only happens when
     * the main looper runs, so under Robolectric we draw the activity's root view ourselves.
     */
    private fun captureRoot(): Bitmap {
        ShadowLooper.idleMainLooper()
        val decor = rule.activity.window.decorView
        if (decor.width == 0 || decor.height == 0) error("root view has no size yet")
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        return bitmap
    }

    @Composable
    private fun WithChrome(route: String, content: @Composable () -> Unit) {
        MealSnapScaffold(route = route, onTab = {}, onAddMeal = {}) { padding ->
            Box(Modifier.padding(padding)) { content() }
        }
    }

    private fun todayState(meals: List<Meal>) = TodayUiState(date = today, goal = 2000, meals = meals)

    @Test
    fun todayWithMeals() = shot("01_today") {
        WithChrome(Routes.TODAY) {
            TodayContent(todayState(sampleMeals), {}, {}, {}, {})
        }
    }

    @Test
    fun todayWithMealsDark() = shot("02_today_dark", dark = true) {
        WithChrome(Routes.TODAY) {
            TodayContent(todayState(sampleMeals), {}, {}, {}, {})
        }
    }

    @Test
    fun todayEmpty() = shot("03_today_empty") {
        WithChrome(Routes.TODAY) {
            TodayContent(todayState(emptyList()), {}, {}, {}, {})
        }
    }

    @Test
    fun trends() = shot("04_trends") {
        val days = DayStats.groupByDay(
            sampleMeals + listOf(
                sampleMeals[1].copy(id = 10, createdAt = at(12).minus(86_400_000L), calories = 2300),
                sampleMeals[0].copy(id = 11, createdAt = at(8).minus(2 * 86_400_000L), calories = 1750),
                sampleMeals[1].copy(id = 12, createdAt = at(19).minus(4 * 86_400_000L), calories = 1900),
            ),
            zone,
        )
        WithChrome(Routes.HISTORY) {
            HistoryContent(
                HistoryUiState(today, 2000, days, DayStats.lastDays(days, today, 7)),
                onOpenMeal = {},
            )
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h1900dp-xxhdpi")
    fun editorAutoLoggedResult() = shot("05_editor_result") {
        MealEditorContent(aiState, editorActions)
    }

    @Test
    fun editorAnalyzing() = shot("06_editor_analyzing") {
        MealEditorContent(
            EditorState(loading = false, status = AnalysisStatus.Analyzing, photoPath = null),
            editorActions,
        )
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h1900dp-xxhdpi")
    fun editorRecognisedOnPhone() = shot("07_editor_on_device") {
        val burger = com.mealsnap.nutrition.NutritionDb.byName("Hamburger")!!
        val cheese = com.mealsnap.nutrition.NutritionDb.byName("Cheeseburger")!!
        val veggie = com.mealsnap.nutrition.NutritionDb.byName("Veggie burger")!!
        val dish = com.mealsnap.nutrition.Dish.single(burger, name = "Hamburger")
        val analysis = dish.toAnalysis(
            "medium",
            "Recognised as “Hamburger” (52% sure). Calories assume 1 burger of Hamburger (about 180 g); change the portion if yours differs.",
            listOf("“Hamburger” 52%", "“Cheeseburger” 30%", "“Veggie burger” 7%"),
        )
        MealEditorContent(
            EditorState(
                loading = false, isSaved = true, mealType = MealType.LUNCH, name = analysis.dishName,
                calories = analysis.totalCalories.toString(), protein = analysis.proteinG.toString(),
                carbs = analysis.carbsG.toString(), fat = analysis.fatG.toString(), fiberG = analysis.fiberG,
                sugarG = analysis.sugarG, sodiumMg = analysis.sodiumMg, notes = analysis.notes,
                items = analysis.items, visualCues = analysis.visualCues, confidence = "medium", aiEstimated = true,
                engine = com.mealsnap.ai.Engine.CLASSIFIER, dish = dish, portionFactor = 1f,
                alternatives = listOf(
                    com.mealsnap.ai.Alternative("Cheeseburger", cheese),
                    com.mealsnap.ai.Alternative("Veggie burger", veggie),
                ),
                status = AnalysisStatus.Done,
            ),
            editorActions,
        )
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h1300dp-xxhdpi")
    fun editorManualWithSuggestions() = shot("10_editor_manual_search") {
        MealEditorContent(
            EditorState(loading = false, mealType = MealType.DINNER, name = "biry"),
            editorActions,
        )
    }

    @Test
    fun editorFailed() = shot("08_editor_failed") {
        MealEditorContent(
            EditorState(
                loading = false,
                status = AnalysisStatus.Failed("That API key was rejected. Check that you copied the whole key."),
                mealType = MealType.DINNER,
            ),
            editorActions,
        )
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h1500dp-xxhdpi")
    fun settings() = shot("09_settings") {
        WithChrome(Routes.SETTINGS) { SettingsScreen() }
    }

    // ---- personal trainer ---------------------------------------------------------------------

    private val coachDate = today
    private val coachNow = LocalDateTime.of(2026, 11, 17, 18, 30)
    private val fitProfile = Profile(heightCm = 178.0, weightKg = 84.0, birthYear = 1992, sex = Sex.MALE, activity = ActivityLevel.MODERATE, goal = Goal.LOSE, targetWeightKg = 78.0)

    private fun hd(o: Int, steps: Long?, rhr: Int?, sleep: Long?) = HealthDay(
        coachDate.minusDays(o.toLong()), steps = steps, restingHr = rhr, sleepMinutes = sleep,
        avgHr = if (o == 0) 76 else null, minHr = if (o == 0) 54 else null, maxHr = if (o == 0) 128 else null,
        activeKcal = if (o == 0) 210.0 else null, totalKcal = if (o == 0) 1830.0 else null,
    )

    private val week = listOf(hd(6, 9100, 57, 440), hd(5, 11200, 58, 450), hd(4, 7600, 57, 420), hd(3, 8800, 58, 430), hd(2, 6400, 59, 400), hd(1, 5200, 60, 380), hd(0, 4200, 64, 340))
    private val weights = listOf(WeightPoint(coachDate.minusDays(21), 85.1), WeightPoint(coachDate.minusDays(14), 84.7), WeightPoint(coachDate.minusDays(7), 84.3), WeightPoint(coachDate, 84.0))

    private fun coachLoad(profile: Profile, withHealth: Boolean): CoachLoad {
        val goal = BodyMetrics.calorieGoal(profile, coachDate)
        val goalKcal = goal?.kcal ?: 2000
        val snapshot = if (withHealth) HealthSnapshot(week.last(), week, 84.0, 38.0, 97.0) else null
        fun n(o: Int, kcal: Int, p: Int, meals: Int) = DayNutrition(coachDate.minusDays(o.toLong()), MealTotals(kcal, p, 150, 50, 14, meals))
        val inputs = CoachInputs(
            profile = profile, goalKcal = goalKcal, macros = profile.weightKg?.let { BodyMetrics.macroPlan(goalKcal, it, profile.goal) }, now = coachNow,
            today = MealTotals(1240, 52, 170, 38, 11, 3, listOf("Breakfast: Idli with sambar (330 kcal)")),
            nutritionWeek = listOf(n(6, 2200, 118, 3), n(5, 2350, 126, 4), n(4, 2100, 102, 3), n(3, 2500, 110, 4), n(2, 2300, 95, 3), n(1, 2650, 88, 4), n(0, 1240, 52, 3)),
            health = snapshot, weights = if (profile.isComplete) weights else emptyList(),
        )
        return CoachLoad(profile, 2000, goal, inputs, snapshot, inputs.weights)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h1400dp-xxhdpi")
    fun coachFirstRun() = shot("11_coach_setup") {
        WithChrome(Routes.COACH) {
            CoachContent(CoachUiState(loading = false, load = coachLoad(Profile(), false), healthStatus = HealthStatus.NEEDS_PERMISSION), CoachActions())
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h3300dp-xxhdpi")
    fun coachWithBriefingAndDevice() = shot("12_coach_full") {
        val load = coachLoad(fitProfile, true)
        val briefingText = GeminiAnalyzer.extractText(javaClass.getResource("/gemini/coach_briefing_response.json")!!.readText())
        WithChrome(Routes.COACH) {
            CoachContent(
                CoachUiState(
                    loading = false, load = load, tips = RuleCoach.tips(load.inputs), healthStatus = HealthStatus.CONNECTED,
                    hasGeminiKey = true, consent = true, briefing = BriefingState.Ready(CoachParser.parseBriefing(briefingText)!!),
                    dataCheck = listOf(
                        DataCheckRow("Steps", 214, false, setOf("com.google.android.apps.fitbit")),
                        DataCheckRow("Heart rate", 1000, true, setOf("com.google.android.apps.fitbit")),
                        DataCheckRow("Sleep", 6, false, setOf("com.google.android.apps.fitbit")),
                        DataCheckRow("Weight", 0, false, emptySet()),
                    ),
                ),
                CoachActions(),
            )
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h2600dp-xxhdpi")
    fun coachOfflineTips() = shot("13_coach_offline") {
        val load = coachLoad(fitProfile, true)
        WithChrome(Routes.COACH) {
            CoachContent(CoachUiState(loading = false, load = load, tips = RuleCoach.tips(load.inputs), healthStatus = HealthStatus.CONNECTED, hasGeminiKey = false), CoachActions())
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h2300dp-xxhdpi")
    fun profileEditor() = shot("14_profile") {
        ProfileContent(initial = fitProfile, currentGoalKcal = 2000, onBack = {}, onSave = { _, _ -> })
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h1200dp-xxhdpi")
    fun chatConversation() = shot("15_chat") {
        val reply = CoachParser.cleanChat(GeminiAnalyzer.extractText(javaClass.getResource("/gemini/coach_chat_response.json")!!.readText()))
        ChatContent(
            ChatUiState(
                messages = listOf(
                    ChatMessage(true, "I only slept about 5.5 hours. Should I still do my leg workout today? And what should I eat for dinner?"),
                    ChatMessage(false, reply),
                ),
                consent = true,
            ),
            onBack = {}, onOpenSettings = {}, onSend = {}, onAcceptConsent = {}, onDismissConsent = {},
        )
    }
}
