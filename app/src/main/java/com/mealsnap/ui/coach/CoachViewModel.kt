package com.mealsnap.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealsnap.AppContainer
import com.mealsnap.ai.AnalysisException
import com.mealsnap.ai.Briefing
import com.mealsnap.coach.CoachContext
import com.mealsnap.coach.CoachLoad
import com.mealsnap.coach.RuleCoach
import com.mealsnap.coach.Tip
import com.mealsnap.data.WeightEntry
import com.mealsnap.health.DataCheckRow
import com.mealsnap.health.HealthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BriefingState {
    data object Idle : BriefingState
    data object Loading : BriefingState
    data class Failed(val message: String) : BriefingState
    data class Ready(val briefing: Briefing) : BriefingState
}

data class CoachUiState(
    val loading: Boolean = true,
    val load: CoachLoad? = null,
    /** Offline tips computed from the person's numbers. */
    val tips: List<Tip> = emptyList(),
    val healthStatus: HealthStatus = HealthStatus.UNAVAILABLE,
    val hasGeminiKey: Boolean = false,
    val consent: Boolean = false,
    val needsConsent: Boolean = false,
    val briefing: BriefingState = BriefingState.Idle,
    val dataCheck: List<DataCheckRow>? = null,
    val checkingData: Boolean = false,
)

class CoachViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    /** Reloads meals, device data and weights. Called whenever the Coach tab comes into view. */
    fun refresh() {
        viewModelScope.launch {
            val load = container.coachData.load()
            val status = container.health.status()
            val key = container.settings.geminiKey.first().isNotBlank()
            val consent = container.settings.coachConsent.first()
            _state.update {
                it.copy(
                    loading = false,
                    load = load,
                    tips = RuleCoach.tips(load.inputs),
                    healthStatus = status,
                    hasGeminiKey = key,
                    consent = consent,
                )
            }
        }
    }

    /** Asks Gemini for a briefing; first makes sure the person has agreed to share a health summary. */
    fun requestBriefing() {
        val s = _state.value
        if (!s.hasGeminiKey || s.load == null) return
        if (!s.consent) {
            _state.update { it.copy(needsConsent = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(briefing = BriefingState.Loading, needsConsent = false) }
            val result = try {
                BriefingState.Ready(container.coach.briefing(CoachContext.describe(container.coachData.load().inputs)))
            } catch (e: AnalysisException) {
                BriefingState.Failed(e.message ?: "Couldn't get a briefing.")
            }
            _state.update { it.copy(briefing = result) }
        }
    }

    fun acceptConsent() {
        viewModelScope.launch {
            container.settings.setCoachConsent(true)
            _state.update { it.copy(consent = true, needsConsent = false) }
            requestBriefing()
        }
    }

    fun dismissConsent() = _state.update { it.copy(needsConsent = false) }

    fun logWeight(kg: Double) {
        if (kg !in 20.0..400.0) return
        viewModelScope.launch {
            container.weightDao.insert(WeightEntry(timeMillis = System.currentTimeMillis(), kg = kg))
            // Keep the profile weight in step so calorie targets follow the latest weigh-in.
            val profile = container.settings.profile.first()
            container.settings.saveProfile(profile.copy(weightKg = kg))
            refresh()
        }
    }

    /** Uses the recommended calories as the daily goal everywhere in the app. */
    fun applyRecommendedGoal() {
        val kcal = _state.value.load?.recommended?.kcal ?: return
        viewModelScope.launch {
            container.settings.setDailyGoal(kcal)
            refresh()
        }
    }

    fun runDataCheck() {
        viewModelScope.launch {
            _state.update { it.copy(checkingData = true) }
            val rows = runCatching { container.health.dataCheck() }.getOrDefault(emptyList())
            _state.update { it.copy(dataCheck = rows, checkingData = false) }
        }
    }

    val healthPermissions: Set<String> get() = container.health.permissions
}
