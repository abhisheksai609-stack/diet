package com.mealsnap.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealsnap.AppContainer
import com.mealsnap.ai.AnalysisException
import com.mealsnap.ai.FoodItem
import com.mealsnap.ai.FoodItems
import com.mealsnap.data.DayStats
import com.mealsnap.data.Meal
import com.mealsnap.data.MealType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime

sealed interface AnalysisStatus {
    data object Idle : AnalysisStatus
    data object Preparing : AnalysisStatus
    data object Analyzing : AnalysisStatus
    data object NoApiKey : AnalysisStatus
    data class Failed(val message: String) : AnalysisStatus
    data class Done(val confidence: String, val notes: String) : AnalysisStatus
}

data class EditorState(
    val isNew: Boolean = true,
    val loading: Boolean = true,
    val photoPath: String? = null,
    val name: String = "",
    val mealType: MealType = MealType.SNACK,
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val notes: String = "",
    val items: List<FoodItem> = emptyList(),
    val hint: String = "",
    val confidence: String? = null,
    val aiEstimated: Boolean = false,
    val status: AnalysisStatus = AnalysisStatus.Idle,
    val finished: Boolean = false,
) {
    private val busy: Boolean
        get() = status == AnalysisStatus.Preparing || status == AnalysisStatus.Analyzing

    val canSave: Boolean
        get() = !loading && !busy && name.isNotBlank() && calories.toIntOrNull() != null

    val canReanalyze: Boolean
        get() = photoPath != null && !busy
}

/**
 * Drives both "new meal from a photo" ([mealId] < 0, [capturePath] set) and
 * "edit an existing meal" ([mealId] >= 0).
 */
class MealEditorViewModel(
    private val container: AppContainer,
    private val mealId: Long,
    private val capturePath: String,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState(isNew = mealId < 0))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val openedAt = System.currentTimeMillis()
    private var existing: Meal? = null
    private var ownedPhoto: String? = null
    private var saved = false
    private var analysisJob: Job? = null

    init {
        viewModelScope.launch {
            if (mealId >= 0) loadExisting() else startNew()
        }
    }

    private suspend fun loadExisting() {
        val meal = container.mealDao.get(mealId)
        if (meal == null) {
            _state.update { it.copy(loading = false, finished = true) }
            return
        }
        existing = meal
        _state.update {
            it.copy(
                isNew = false,
                loading = false,
                photoPath = meal.photoPath,
                name = meal.name,
                mealType = meal.type,
                calories = meal.calories.toString(),
                protein = meal.proteinG.toString(),
                carbs = meal.carbsG.toString(),
                fat = meal.fatG.toString(),
                notes = meal.notes,
                items = FoodItems.decode(meal.itemsJson),
                confidence = meal.confidence,
                aiEstimated = meal.aiEstimated,
            )
        }
    }

    private suspend fun startNew() {
        _state.update {
            it.copy(
                loading = false,
                mealType = DayStats.mealTypeForHour(LocalTime.now().hour),
                status = AnalysisStatus.Preparing,
            )
        }
        val prepared = container.photos.prepare(File(capturePath))
        if (prepared == null) {
            _state.update {
                it.copy(status = AnalysisStatus.Failed("Couldn't read that photo. You can still enter the meal by hand."))
            }
            return
        }
        ownedPhoto = prepared.absolutePath
        _state.update { it.copy(photoPath = prepared.absolutePath) }
        analyze()
    }

    fun analyze() {
        val photo = _state.value.photoPath ?: return
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            val apiKey = container.settings.apiKey.first()
            if (apiKey.isBlank()) {
                _state.update { it.copy(status = AnalysisStatus.NoApiKey) }
                return@launch
            }
            val model = container.settings.model.first()
            _state.update { it.copy(status = AnalysisStatus.Analyzing) }
            try {
                val bytes = withContext(Dispatchers.IO) { File(photo).readBytes() }
                val result = container.analyzer.analyze(apiKey, model.id, bytes, _state.value.hint)
                if (!result.isFood) {
                    _state.update {
                        it.copy(
                            status = AnalysisStatus.Failed(
                                "That doesn't look like food. Add a description and re-estimate, or enter it by hand.",
                            ),
                        )
                    }
                    return@launch
                }
                _state.update { s ->
                    s.copy(
                        name = result.mealName.ifBlank { s.name.ifBlank { "Meal" } },
                        calories = result.totalCalories.toString(),
                        protein = result.proteinG.toString(),
                        carbs = result.carbsG.toString(),
                        fat = result.fatG.toString(),
                        notes = result.notes,
                        items = result.items,
                        confidence = result.confidence,
                        aiEstimated = true,
                        status = AnalysisStatus.Done(result.confidence, result.notes),
                    )
                }
            } catch (e: AnalysisException) {
                _state.update { it.copy(status = AnalysisStatus.Failed(e.message ?: "Something went wrong.")) }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setMealType(value: MealType) = _state.update { it.copy(mealType = value) }
    fun setCalories(value: String) = _state.update { it.copy(calories = value) }
    fun setProtein(value: String) = _state.update { it.copy(protein = value) }
    fun setCarbs(value: String) = _state.update { it.copy(carbs = value) }
    fun setFat(value: String) = _state.update { it.copy(fat = value) }
    fun setHint(value: String) = _state.update { it.copy(hint = value) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val meal = Meal(
                id = existing?.id ?: 0,
                createdAt = existing?.createdAt ?: openedAt,
                mealType = s.mealType.name,
                name = s.name.trim(),
                calories = s.calories.toInt(),
                proteinG = s.protein.toIntOrNull() ?: 0,
                carbsG = s.carbs.toIntOrNull() ?: 0,
                fatG = s.fat.toIntOrNull() ?: 0,
                photoPath = s.photoPath,
                itemsJson = FoodItems.encode(s.items),
                notes = s.notes,
                confidence = s.confidence,
                aiEstimated = s.aiEstimated,
            )
            if (existing == null) container.mealDao.insert(meal) else container.mealDao.update(meal)
            saved = true
            _state.update { it.copy(finished = true) }
        }
    }

    fun delete() {
        val meal = existing ?: return
        viewModelScope.launch {
            container.mealDao.delete(meal)
            container.photos.delete(meal.photoPath)
            saved = true
            _state.update { it.copy(finished = true) }
        }
    }

    override fun onCleared() {
        // Leaving a new meal without saving: don't leave its photo behind.
        if (!saved) container.photos.delete(ownedPhoto)
    }
}
