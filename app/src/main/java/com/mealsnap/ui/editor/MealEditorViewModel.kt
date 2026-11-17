package com.mealsnap.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealsnap.AppContainer
import com.mealsnap.ai.AnalysisException
import com.mealsnap.ai.Alternative
import com.mealsnap.ai.Engine
import com.mealsnap.ai.FoodItem
import com.mealsnap.ai.FoodItems
import com.mealsnap.ai.Recognition
import com.mealsnap.data.DayStats
import com.mealsnap.data.Meal
import com.mealsnap.data.MealType
import com.mealsnap.nutrition.Dish
import com.mealsnap.nutrition.FoodEntry
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
    data class Failed(val message: String) : AnalysisStatus
    data object Done : AnalysisStatus
}

/** Portion multipliers offered for foods that come from the built-in nutrition table. */
val PortionFactors = listOf(0.5f, 0.75f, 1f, 1.5f, 2f)

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
    /** Who identified the food (null for meals typed in or opened from the log). */
    val engine: Engine? = null,
    /** The recognised dish at 1x portion, when its numbers come from the built-in table. */
    val dish: Dish? = null,
    val portionFactor: Float = 1f,
    /** Other dishes the photo might show, for one-tap correction. */
    val alternatives: List<Alternative> = emptyList(),
    /** Shown while analysing: where the work happens ("your phone" or "Claude"). */
    val workingOn: String = "your phone",
    val status: AnalysisStatus = AnalysisStatus.Idle,
    val finished: Boolean = false,
) {
    val busy: Boolean
        get() = status == AnalysisStatus.Preparing || status == AnalysisStatus.Analyzing

    val canSave: Boolean
        get() = !loading && !busy && name.isNotBlank() && calories.toIntOrNull() != null

    val canReanalyze: Boolean
        get() = photoPath != null && !busy

    /** True when there is a recognised or looked-up result to show in detail. */
    val hasResult: Boolean
        get() = (aiEstimated || dish != null) && calories.isNotBlank()
}

/**
 * Drives "new meal from a photo" ([mealId] < 0, [capturePath] set), "new manual meal"
 * ([mealId] < 0, blank [capturePath]) and "view / edit an existing meal" ([mealId] >= 0).
 *
 * For a new photo the food is identified straight away (on the phone, no key needed) and the meal is
 * logged automatically unless the user turned auto-logging off.
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

    fun analyze() {
        val photo = _state.value.photoPath ?: return
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            val usesClaude = container.settings.apiKey.first().isNotBlank()
            _state.update {
                it.copy(status = AnalysisStatus.Analyzing, workingOn = if (usesClaude) "Claude" else "your phone")
            }
            try {
                val bytes = withContext(Dispatchers.IO) { File(photo).readBytes() }
                val recognition = container.recognizer.recognize(bytes, _state.value.hint)
                applyRecognition(recognition)
                if (container.settings.autoLog.first()) persist()
            } catch (e: AnalysisException) {
                _state.update { it.copy(status = AnalysisStatus.Failed(e.message ?: "Something went wrong.")) }
            }
        }
    }

    private fun applyRecognition(rec: Recognition) {
        val a = rec.analysis
        _state.update { s ->
            s.copy(
                name = a.dishName.ifBlank { s.name.ifBlank { "Meal" } },
                cuisine = a.cuisine,
                calories = a.totalCalories.toString(),
                protein = a.proteinG.toString(),
                carbs = a.carbsG.toString(),
                fat = a.fatG.toString(),
                fiberG = a.fiberG,
                sugarG = a.sugarG,
                sodiumMg = a.sodiumMg,
                notes = a.notes,
                items = a.items,
                visualCues = a.visualCues,
                confidence = a.confidence,
                aiEstimated = true,
                engine = rec.engine,
                dish = rec.dish,
                portionFactor = 1f,
                alternatives = rec.alternatives,
                status = AnalysisStatus.Done,
                dirty = true,
            )
        }
    }

    /** Rescales a table-based result to a bigger or smaller portion. */
    fun setPortion(factor: Float) {
        val s = _state.value
        val dish = s.dish ?: return
        val a = dish.scaled(factor).toAnalysis(s.confidence ?: "medium", s.notes, s.visualCues)
        _state.update {
            it.copy(
                portionFactor = factor,
                calories = a.totalCalories.toString(),
                protein = a.proteinG.toString(),
                carbs = a.carbsG.toString(),
                fat = a.fatG.toString(),
                fiberG = a.fiberG,
                sugarG = a.sugarG,
                sodiumMg = a.sodiumMg,
                items = a.items,
                dirty = true,
            )
        }
        persistIfAlreadyLogged()
    }

    /** One-tap correction: "it's actually the cheeseburger". */
    fun selectAlternative(alternative: Alternative) {
        val s = _state.value
        val previous = s.dish?.let { d ->
            d.items.singleOrNull()?.let { Alternative(d.name, it.entry) }
        }
        val dish = Dish.single(alternative.entry, name = alternative.label)
        val notes = "Changed to “${alternative.label}”. Calories assume ${alternative.entry.servingLabel} of " +
            "${alternative.entry.name} (about ${alternative.entry.servingG} g); change the portion if yours differs."
        val a = dish.toAnalysis(s.confidence ?: "medium", notes, s.visualCues)
        _state.update {
            it.copy(
                name = alternative.label,
                calories = a.totalCalories.toString(),
                protein = a.proteinG.toString(),
                carbs = a.carbsG.toString(),
                fat = a.fatG.toString(),
                fiberG = a.fiberG,
                sugarG = a.sugarG,
                sodiumMg = a.sodiumMg,
                notes = notes,
                items = a.items,
                dish = dish,
                portionFactor = 1f,
                alternatives = (it.alternatives.filter { alt -> alt != alternative } + listOfNotNull(previous)).take(3),
                dirty = true,
            )
        }
        persistIfAlreadyLogged()
    }

    /** Fills the form from a food picked in the search suggestions. */
    fun pickFood(entry: FoodEntry) {
        val dish = Dish.single(entry)
        val notes = "From the built-in food table: ${entry.servingLabel} of ${entry.name} (about ${entry.servingG} g). " +
            "Change the portion if yours differs."
        val a = dish.toAnalysis("medium", notes)
        _state.update {
            it.copy(
                name = entry.name,
                cuisine = "",
                calories = a.totalCalories.toString(),
                protein = a.proteinG.toString(),
                carbs = a.carbsG.toString(),
                fat = a.fatG.toString(),
                fiberG = a.fiberG,
                sugarG = a.sugarG,
                sodiumMg = a.sodiumMg,
                notes = notes,
                items = a.items,
                visualCues = emptyList(),
                confidence = "medium",
                engine = Engine.DATABASE,
                dish = dish,
                portionFactor = 1f,
                alternatives = emptyList(),
                dirty = true,
            )
        }
    }

    private fun persistIfAlreadyLogged() {
        if (_state.value.isSaved) viewModelScope.launch { if (container.settings.autoLog.first()) persist() }
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
