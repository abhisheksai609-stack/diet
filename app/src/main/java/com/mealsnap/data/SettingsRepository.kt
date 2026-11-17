package com.mealsnap.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    }

    val apiKey: Flow<String> = context.settingsStore.data.map { it[Keys.apiKey].orEmpty() }
    val dailyGoal: Flow<Int> = context.settingsStore.data.map { it[Keys.dailyGoal] ?: DEFAULT_GOAL }
    val model: Flow<AiModel> = context.settingsStore.data.map { AiModel.fromId(it[Keys.model]) }

    val geminiKey: Flow<String> = context.settingsStore.data.map { it[Keys.geminiKey].orEmpty() }
    val geminiModel: Flow<GeminiChoice> = context.settingsStore.data.map { GeminiChoice.fromId(it[Keys.geminiModel]) }

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

    companion object {
        const val DEFAULT_GOAL = 2000
    }
}
