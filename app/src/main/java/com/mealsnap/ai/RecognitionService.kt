package com.mealsnap.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mealsnap.data.SettingsRepository
import com.mealsnap.nutrition.Dish
import com.mealsnap.nutrition.Match
import com.mealsnap.nutrition.NutritionDb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Decides who looks at the photo. Nothing here needs an account or a key:
 *
 *  1. Google Gemini, only if the person added a (free) Google AI Studio key: best accuracy, needs internet.
 *  2. Claude, only if the person added an Anthropic key.
 *  3. If the person typed a description, the built-in food table is searched with it.
 *  4. Gemini Nano, if this phone supports it and the model is downloaded.
 *  5. The bundled food classifier, which works on every phone, offline.
 *
 * If a cloud service fails (no internet, quota used up) the next option is used and the meal's note says so.
 */
class RecognitionService(
    private val gemini: GeminiAnalyzer,
    private val claude: FoodAnalyzer,
    private val classifier: FoodClassifier,
    private val nano: NanoRecognizer,
    private val settings: SettingsRepository,
) {
    suspend fun recognize(jpeg: ByteArray, hint: String): Recognition = withContext(Dispatchers.Default) {
        val fallbackNotes = mutableListOf<String>()

        val geminiKey = settings.geminiKey.first()
        if (geminiKey.isNotBlank()) {
            try {
                val analysis = gemini.analyze(geminiKey, settings.geminiModel.first().id, jpeg, hint)
                if (!analysis.isFood) throw NotFoodException()
                return@withContext Recognition(Engine.GEMINI, analysis)
            } catch (e: NotFoodException) {
                throw e
            } catch (e: AnalysisException) {
                fallbackNotes += "Google Gemini couldn't be used (${e.message?.trimEnd('.')})."
            }
        }

        val apiKey = settings.apiKey.first()
        if (apiKey.isNotBlank()) {
            try {
                val model = settings.model.first()
                val analysis = claude.analyze(apiKey, model.id, jpeg, hint)
                if (!analysis.isFood) throw NotFoodException()
                return@withContext Recognition(Engine.CLAUDE, analysis)
            } catch (e: NotFoodException) {
                throw e
            } catch (e: AnalysisException) {
                fallbackNotes += "Claude couldn't be used (${e.message?.trimEnd('.')})."
            }
        }
        val fallbackNote = if (fallbackNotes.isEmpty()) null else fallbackNotes.joinToString(" ") + " So this was identified on your phone."

        if (hint.isNotBlank()) {
            fromDescription(hint)?.let { return@withContext it.withNote(fallbackNote) }
        }

        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: throw AnalysisException("Couldn't read that photo.")

        try {
            identifyOnDevice(bitmap, hint)?.let { return@withContext it.withNote(fallbackNote) }
        } finally {
            bitmap.recycle()
        }

        throw AnalysisException(
            "I couldn't tell what this dish is. Type its name below and pick it from the list, " +
                "or add a short description and tap Re-estimate.",
        )
    }

    private suspend fun identifyOnDevice(bitmap: Bitmap, hint: String): Recognition? {
        nano.identify(bitmap, hint)?.let { dish ->
            NanoMapper.toRecognition(dish)?.let { return it }
        }
        val predictions = try {
            classifier.classify(bitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw AnalysisException("The on-device food recognition failed to run (${e.javaClass.simpleName}).", e)
        }
        return ClassifierMapper.toRecognition(predictions)
    }

    /** "oat milk latte" or "chicken biryani": look the words up directly. */
    private fun fromDescription(description: String): Recognition? {
        val match = NutritionDb.match(description)
        if (match.kind != Match.Kind.EXACT && match.kind != Match.Kind.PARTIAL) return null
        val name = description.trim().replaceFirstChar { it.uppercase() }
        val dish = Dish.single(match.entry, name = name)
        return Recognition(
            engine = Engine.DATABASE,
            dish = dish,
            analysis = dish.toAnalysis(
                confidence = "medium",
                notes = "Based on your description “${description.trim()}”. Calories assume ${match.entry.servingLabel} " +
                    "of ${match.entry.name} (about ${match.entry.servingG} g); change the portion if yours differs.",
            ),
        )
    }

    private fun Recognition.withNote(note: String?): Recognition =
        if (note == null) this else copy(analysis = analysis.copy(notes = "$note ${analysis.notes}".trim()))
}
