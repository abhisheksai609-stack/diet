package com.mealsnap.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mealsnap.coach.ActivityLevel
import com.mealsnap.coach.Goal
import com.mealsnap.coach.Profile
import com.mealsnap.coach.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Models the user can pick for photo analysis (cost vs. accuracy). */
enum class AiModel(val id: String, val label: String, val hint: String) {
    OPUS("claude-opus-5-5", "Claude Opus 5.5", "Most accurate (default)"),
    SONNET("claude-sonnet-5-5", "Claude Sonnet 5.5", "Cheaper, still very good"),
    HAIKU("claude-haiku-5-5", "Claude Haiku 5.5", "Cheapest and fastest");

    companion object {
        fun fromId(id: String?): AiModel = entries.firstOrNull { it.id == id } ?: OPUS
    }
}

/** Which Gemini model to prefer (the app falls back automatically if a name isn't available to the key). */
enum class GeminiChoice(val id: String, val label: String, val hint: String) {
    LITE("gemini-flash-lite-latest", "Gemini Flash-Lite", "Fast and reliable, generous free limits (recommended)"),
    FLASH("gemini-flash-latest", "Gemini Flash", "A little more detail, but often slow or busy");

    companion object {
        fun fromId(id: String?): GeminiChoice = entries.firstOrNull { it.id == id } ?: LITE
    }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val apiKey = stringPreferencesKey("anthropic_api_key")
        val dailyGoal = intPreferencesKey("daily_goal_kcal")
        val model = stringPreferencesKey("model")
        val autoLog = booleanPreferencesKey("auto_log")
        val geminiKey = stringPreferencesKey("google_gemini_api_key")
        val geminiModel = stringPreferencesKey("google_gemini_model")
        val heightCm = doublePreferencesKey("profile_height_cm")
        val weightKg = doublePreferencesKey("profile_weight_kg")
        val birthYear = intPreferencesKey("profile_birth_year")
        val sex = stringPreferencesKey("profile_sex")
        val activity = stringPreferencesKey("profile_activity")
        val goal = stringPreferencesKey("profile_goal")
        val targetWeightKg = doublePreferencesKey("profile_target_weight_kg")
        val coachConsent = booleanPreferencesKey("coach_ai_consent")
    }

    val apiKey: Flow<String> = context.settingsStore.data.map { it[Keys.apiKey].orEmpty() }
    val dailyGoal: Flow<Int> = context.settingsStore.data.map { it[Keys.dailyGoal] ?: DEFAULT_GOAL }
    val model: Flow<AiModel> = context.settingsStore.data.map { AiModel.fromId(it[Keys.model]) }

    val geminiKey: Flow<String> = context.settingsStore.data.map { it[Keys.geminiKey].orEmpty() }
    val geminiModel: Flow<GeminiChoice> = context.settingsStore.data.map { GeminiChoice.fromId(it[Keys.geminiModel]) }

    val profile: Flow<Profile> = context.settingsStore.data.map { p ->
        Profile(
            heightCm = p[Keys.heightCm],
            weightKg = p[Keys.weightKg],
            birthYear = p[Keys.birthYear],
            sex = Sex.entries.firstOrNull { it.name == p[Keys.sex] } ?: Sex.OTHER,
            activity = ActivityLevel.entries.firstOrNull { it.name == p[Keys.activity] } ?: ActivityLevel.MODERATE,
            goal = Goal.entries.firstOrNull { it.name == p[Keys.goal] } ?: Goal.MAINTAIN,
            targetWeightKg = p[Keys.targetWeightKg],
        )
    }

    suspend fun saveProfile(profile: Profile) {
        context.settingsStore.edit { prefs: MutablePreferences ->
            fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T?) {
                if (value == null) prefs.remove(key) else prefs[key] = value
            }
            set(Keys.heightCm, profile.heightCm)
            set(Keys.weightKg, profile.weightKg)
            set(Keys.birthYear, profile.birthYear)
            prefs[Keys.sex] = profile.sex.name
            prefs[Keys.activity] = profile.activity.name
            prefs[Keys.goal] = profile.goal.name
            set(Keys.targetWeightKg, profile.targetWeightKg)
        }
    }

    /** True once the person agreed to send a health summary to Google Gemini for coaching. */
    val coachConsent: Flow<Boolean> = context.settingsStore.data.map { it[Keys.coachConsent] ?: false }

    suspend fun setCoachConsent(value: Boolean) {
        context.settingsStore.edit { it[Keys.coachConsent] = value }
    }

    /** When on (default), a meal is saved to the log as soon as the AI has identified it. */
    val autoLog: Flow<Boolean> = context.settingsStore.data.map { it[Keys.autoLog] ?: true }

    suspend fun save(
        apiKey: String,
        dailyGoal: Int,
        model: AiModel,
        autoLog: Boolean,
        geminiKey: String = "",
        geminiModel: GeminiChoice = GeminiChoice.LITE,
    ) {
        context.settingsStore.edit { prefs: MutablePreferences ->
            prefs[Keys.geminiKey] = geminiKey.trim()
            prefs[Keys.geminiModel] = geminiModel.id
            prefs[Keys.apiKey] = apiKey.trim()
            prefs[Keys.dailyGoal] = dailyGoal.coerceIn(500, 10_000)
            prefs[Keys.model] = model.id
            prefs[Keys.autoLog] = autoLog
        }
    }

    suspend fun setDailyGoal(kcal: Int) {
        context.settingsStore.edit { it[Keys.dailyGoal] = kcal.coerceIn(500, 10_000) }
    }

    companion object {
        const val DEFAULT_GOAL = 2000
    }
}
