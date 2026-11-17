package com.mealsnap.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealsnap.AppContainer
import com.mealsnap.data.DayStats
import com.mealsnap.data.Meal
import com.mealsnap.data.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val goal: Int = SettingsRepository.DEFAULT_GOAL,
    /** Oldest first. */
    val meals: List<Meal> = emptyList(),
) {
    val calories: Int get() = meals.sumOf { it.calories }
    val proteinG: Int get() = meals.sumOf { it.proteinG }
    val carbsG: Int get() = meals.sumOf { it.carbsG }
    val fatG: Int get() = meals.sumOf { it.fatG }
}

class TodayViewModel(container: AppContainer) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUiState> =
        combine(DayStats.todayFlow(), container.settings.dailyGoal) { date, goal -> date to goal }
            .flatMapLatest { (date, goal) ->
                container.mealDao
                    .observeBetween(DayStats.startOfDayMillis(date), DayStats.startOfDayMillis(date.plusDays(1)))
                    .map { meals -> TodayUiState(date, goal, meals) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
}
