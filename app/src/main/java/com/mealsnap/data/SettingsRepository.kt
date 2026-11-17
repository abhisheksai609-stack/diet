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

class SettingsRepository(private val context: Context) {
    private object Keys {
        val apiKey = stringPreferencesKey("anthropic_api_key")
        val dailyGoal = intPreferencesKey("daily_goal_kcal")
        val model = stringPreferencesKey("model")
        val autoLog = booleanPreferencesKey("auto_log")
    }

    val apiKey: Flow<String> = context.settingsStore.data.map { it[Keys.apiKey].orEmpty() }
    val dailyGoal: Flow<Int> = context.settingsStore.data.map { it[Keys.dailyGoal] ?: DEFAULT_GOAL }
    val model: Flow<AiModel> = context.settingsStore.data.map { AiModel.fromId(it[Keys.model]) }

    /** When on (default), a meal is saved to the log as soon as the AI has identified it. */
    val autoLog: Flow<Boolean> = context.settingsStore.data.map { it[Keys.autoLog] ?: true }

    suspend fun save(apiKey: String, dailyGoal: Int, model: AiModel, autoLog: Boolean) {
        context.settingsStore.edit { prefs: MutablePreferences ->
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
