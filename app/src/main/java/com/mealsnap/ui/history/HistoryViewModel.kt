package com.mealsnap.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealsnap.AppContainer
import com.mealsnap.data.DayStats
import com.mealsnap.data.DaySummary
import com.mealsnap.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HistoryUiState(
    val today: LocalDate = LocalDate.now(),
    val goal: Int = SettingsRepository.DEFAULT_GOAL,
    /** Newest day first. */
    val days: List<DaySummary> = emptyList(),
    /** Last 7 days, oldest first, zero-filled. */
    val chart: List<Pair<LocalDate, Int>> = emptyList(),
)

class HistoryViewModel(container: AppContainer) : ViewModel() {

    val state: StateFlow<HistoryUiState> =
        combine(
            container.mealDao.observeAll(),
            container.settings.dailyGoal,
            DayStats.todayFlow(),
        ) { meals, goal, today ->
            val days = DayStats.groupByDay(meals)
            HistoryUiState(today, goal, days, DayStats.lastDays(days, today, 7))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HistoryUiState(chart = DayStats.lastDays(emptyList(), LocalDate.now(), 7)),
        )
}
