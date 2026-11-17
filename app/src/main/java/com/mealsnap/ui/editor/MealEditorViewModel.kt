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
    data object Done : AnalysisStatus
}

data class EditorState(
    val loading: Boolean = true,
    /** True once the meal exists in the log (auto-logged, saved, or opened from the log). */
    val isSaved: Boolean = false,
    /** True when fields were edited since the last save. */
    val dirty: Boolean = false,
    val photoPath: String? = null,
    val name: String = "",
    val cuisine: String = "",
    val mealType: MealType = MealType.SNACK,
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiberG: Int = 0,
    val sugarG: Int = 0,
    val sodiumMg: Int = 0,
    val notes: String = "",
    val items: List<FoodItem> = emptyList(),
    val visualCues: List<String> = emptyList(),
    val hint: String = "",
    val confidence: String? = null,
    val aiEstimated: Boolean = false,
    val status: AnalysisStatus = AnalysisStatus.Idle,
    val finished: Boolean = false,
) {
    val busy: Boolean
        get() = status == AnalysisStatus.Preparing || status == AnalysisStatus.Analyzing

    val canSave: Boolean
        get() = !loading && !busy && name.isNotBlank() && calories.toIntOrNull() != null

    val canReanalyze: Boolean
        get() = photoPath != null && !busy
}

/**
 * Drives both "new meal from a photo" ([mealId] < 0, [capturePath] set), "new manual meal"
 * ([mealId] < 0, blank [capturePath]) and "view / edit an existing meal" ([mealId] >= 0).
 *
 * For a new photo the analysis starts immediately and, when it succeeds, the meal is logged
 * automatically (unless the user turned auto-logging off).
 */
class MealEditorViewModel(
    private val container: AppContainer,
    private val mealId: Long,
    private val capturePath: String,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val openedAt = System.currentTimeMillis()
    private var existing: Meal? = null
    private var ownedPhoto: String? = null
    private var saved = false
    private var analysisJob: Job? = null

    init {
        viewModelScope.launch {
            when {
                mealId >= 0 -> loadExisting()
                capturePath.isBlank() -> startManual()
                else -> startFromPhoto()
            }
        }
    }

    private suspend fun loadExisting() {
        val meal = container.mealDao.get(mealId)
        if (meal == null) {
            _state.update { it.copy(loading = false, finished = true) }
            return
        }
        existing = meal
        saved = true
        _state.update {
            it.copy(
                loading = false,
                isSaved = true,
                photoPath = meal.photoPath,
                name = meal.name,
                cuisine = meal.cuisine,
                mealType = meal.type,
                calories = meal.calories.toString(),
                protein = meal.proteinG.toString(),
                carbs = meal.carbsG.toString(),
                fat = meal.fatG.toString(),
                fiberG = meal.fiberG,
                sugarG = meal.sugarG,
                sodiumMg = meal.sodiumMg,
                notes = meal.notes,
                items = FoodItems.decode(meal.itemsJson),
                visualCues = meal.visualCues.lines().filter { line -> line.isNotBlank() },
                confidence = meal.confidence,
                aiEstimated = meal.aiEstimated,
            )
        }
    }

    private fun startManual() {
        _state.update {
            it.copy(loading = false, mealType = DayStats.mealTypeForHour(LocalTime.now().hour))
        }
    }

    private suspend fun startFromPhoto() {
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

    /** Re-checks for a newly entered API key when coming back from Settings. */
    fun onResume() {
        if (_state.value.status == AnalysisStatus.NoApiKey) analyze()
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
                        name = result.dishName.ifBlank { s.name.ifBlank { "Meal" } },
                        cuisine = result.cuisine,
                        calories = result.totalCalories.toString(),
                        protein = result.proteinG.toString(),
                        carbs = result.carbsG.toString(),
                        fat = result.fatG.toString(),
                        fiberG = result.fiberG,
                        sugarG = result.sugarG,
                        sodiumMg = result.sodiumMg,
                        notes = result.notes,
                        items = result.items,
                        visualCues = result.visualCues,
                        confidence = result.confidence,
                        aiEstimated = true,
                        status = AnalysisStatus.Done,
                        dirty = true,
                    )
                }
                if (container.settings.autoLog.first()) persist()
            } catch (e: AnalysisException) {
                _state.update { it.copy(status = AnalysisStatus.Failed(e.message ?: "Something went wrong.")) }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, dirty = true) }
    fun setMealType(value: MealType) = _state.update { it.copy(mealType = value, dirty = true) }
    fun setCalories(value: String) = _state.update { it.copy(calories = value, dirty = true) }
    fun setProtein(value: String) = _state.update { it.copy(protein = value, dirty = true) }
    fun setCarbs(value: String) = _state.update { it.copy(carbs = value, dirty = true) }
    fun setFat(value: String) = _state.update { it.copy(fat = value, dirty = true) }
    fun setHint(value: String) = _state.update { it.copy(hint = value) }

    /** Writes the current fields to the log (insert the first time, update after). */
    private suspend fun persist(): Boolean {
        val s = _state.value
        val calories = s.calories.toIntOrNull() ?: return false
        if (s.name.isBlank()) return false

        val meal = Meal(
            id = existing?.id ?: 0,
            createdAt = existing?.createdAt ?: openedAt,
            mealType = s.mealType.name,
            name = s.name.trim(),
            calories = calories,
            proteinG = s.protein.toIntOrNull() ?: 0,
            carbsG = s.carbs.toIntOrNull() ?: 0,
            fatG = s.fat.toIntOrNull() ?: 0,
            photoPath = s.photoPath,
            itemsJson = FoodItems.encode(s.items),
            notes = s.notes,
            confidence = s.confidence,
            aiEstimated = s.aiEstimated,
            fiberG = s.fiberG,
            sugarG = s.sugarG,
            sodiumMg = s.sodiumMg,
            cuisine = s.cuisine,
            visualCues = s.visualCues.joinToString("\n"),
        )
        val id = if (existing == null) {
            container.mealDao.insert(meal)
        } else {
            container.mealDao.update(meal)
            meal.id
        }
        existing = meal.copy(id = id)
        saved = true
        _state.update { it.copy(isSaved = true, dirty = false) }
        return true
    }

    fun save() {
        viewModelScope.launch {
            if (persist()) _state.update { it.copy(finished = true) }
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
        // Leaving a photo that never made it into the log: don't leave the file behind.
        if (!saved) container.photos.delete(ownedPhoto)
    }
}
