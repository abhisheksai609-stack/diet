package com.mealsnap.ai

import android.graphics.Bitmap
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.mealsnap.nutrition.Dish
import com.mealsnap.nutrition.DishItem
import com.mealsnap.nutrition.FoodEntry
import com.mealsnap.nutrition.Match
import com.mealsnap.nutrition.NutritionDb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

sealed interface NanoStatus {
    data object Available : NanoStatus
    data object Downloadable : NanoStatus
    data object Downloading : NanoStatus
    data class Unavailable(val reason: String) : NanoStatus
}

data class NanoItem(val name: String, val grams: Int?, val kcal: Int?)
data class NanoDish(val dishName: String, val cuisine: String, val isFood: Boolean, val items: List<NanoItem>)

/**
 * Optional extra: Google's on-device Gemini Nano (through Android's AICore) can read a whole plate,
 * name each item and guess its weight. It only exists on supported phones, so everything here is
 * defensive: any problem simply returns null and the regular on-device recognition takes over.
 */
class NanoRecognizer {

    private val client: GenerativeModel by lazy { Generation.getClient() }

    suspend fun status(): NanoStatus = try {
        when (client.checkStatus()) {
            FeatureStatus.AVAILABLE -> NanoStatus.Available
            FeatureStatus.DOWNLOADABLE -> NanoStatus.Downloadable
            FeatureStatus.DOWNLOADING -> NanoStatus.Downloading
            else -> NanoStatus.Unavailable("This phone doesn't support on-device Gemini Nano.")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        NanoStatus.Unavailable("Gemini Nano isn't available here (${e.javaClass.simpleName}).")
    }

    /** Emits download progress from 0f to 1f (null while unknown). Completes when the model is ready. */
    fun download(): Flow<Float?> = flow {
        emit(null)
        client.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadProgress -> emit(null)
                is DownloadStatus.DownloadFailed -> throw IllegalStateException("The download failed.")
                else -> Unit
            }
        }
        emit(1f)
    }

    /** Null if Nano isn't ready, times out, fails, or doesn't see food. */
    suspend fun identify(bitmap: Bitmap, hint: String): NanoDish? = try {
        if (status() != NanoStatus.Available) {
            null
        } else {
            val request = generateContentRequest(ImagePart(bitmap), TextPart(prompt(hint))) {
                temperature = 0.1f
                candidateCount = 1
                maxOutputTokens = 500
            }
            val text = withTimeoutOrNull(TIMEOUT_MS) {
                client.generateContent(request).candidates.firstOrNull()?.text
            }
            text?.let { NanoParser.parse(it) }?.takeIf { it.isFood }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    private fun prompt(hint: String) = buildString {
        append(
            "You are a food recognition helper. Look at the photo and identify the food.\n" +
                "Reply with JSON only, no other text, in exactly this form:\n" +
                "{\"is_food\": true, \"dish_name\": \"chicken biryani\", \"cuisine\": \"Indian\", " +
                "\"items\": [{\"name\": \"chicken biryani\", \"grams\": 300}, {\"name\": \"raita\", \"grams\": 80}]}\n" +
                "List each distinct food or drink you can see, using common food names, with your best estimate of " +
                "its weight in grams as a number. If there is no food in the photo, reply {\"is_food\": false}.",
        )
        if (hint.isNotBlank()) append("\nThe person says: \"").append(hint.trim()).append("\"")
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}

/** Tolerant parser for the small model's JSON-ish reply. */
object NanoParser {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(text: String): NanoDish? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = runCatching { json.parseToJsonElement(text.substring(start, end + 1)) }.getOrNull() as? JsonObject
            ?: return null

        val isFood = (root["is_food"] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull != "false") } ?: true
        val items = (root["items"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (name.isEmpty()) null
            else NanoItem(name, number(obj["grams"])?.toInt(), number(obj["kcal"] ?: obj["calories"])?.toInt())
        }
        return NanoDish(
            dishName = (root["dish_name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(),
            cuisine = (root["cuisine"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(),
            isFood = isFood,
            items = items,
        )
    }

    /** Accepts 150, 150.5 and "150 g". */
    private fun number(element: JsonElement?): Double? =
        (element as? JsonPrimitive)?.contentOrNull
            ?.filter { it.isDigit() || it == '.' }
            ?.takeIf { it.isNotEmpty() }
            ?.toDoubleOrNull()
}

/** Turns Nano's dish and item list into a logged meal using the built-in nutrition table. */
object NanoMapper {
    fun toRecognition(dish: NanoDish): Recognition? {
        val items = dish.items.map { item ->
            val match = NutritionDb.match(item.name)
            val grams = (item.grams ?: match.entry.servingG).coerceIn(5, 1500)
            val entry = when {
                // Unknown to the table: trust Nano's own calorie figure when it gave one.
                match.kind == Match.Kind.GENERIC && item.kcal != null && item.kcal > 0 -> customEntry(item.name, item.kcal, grams)
                else -> match.entry
            }
            DishItem(entry, grams, displayName = item.name.replaceFirstChar { it.uppercase() })
        }.ifEmpty {
            if (dish.dishName.isBlank()) return null
            val entry = NutritionDb.match(dish.dishName).entry
            listOf(DishItem(entry, entry.servingG, displayName = dish.dishName.replaceFirstChar { it.uppercase() }))
        }

        val name = dish.dishName.ifBlank { items.first().displayName }.replaceFirstChar { it.uppercase() }
        val built = Dish(name = name, cuisine = dish.cuisine, items = items)
        val cues = items.map { "${it.displayName}, about ${it.grams} g" }
        return Recognition(
            engine = Engine.NANO,
            analysis = built.toAnalysis(
                confidence = "medium",
                notes = "Identified by Gemini Nano on your phone. Portions are its estimates; calories come from the " +
                    "built-in nutrition table.",
                visualCues = cues,
            ),
            dish = built,
        )
    }

    private fun customEntry(name: String, kcal: Int, grams: Int): FoodEntry {
        val per100 = kcal * 100.0 / grams
        return FoodEntry(
            name = name,
            aliases = emptyList(),
            kcal = per100,
            protein = 0.2 * per100 / 4,
            carbs = 0.5 * per100 / 4,
            fat = 0.3 * per100 / 9,
            fiber = 0.0,
            sugar = 0.0,
            sodiumMg = 0.0,
            servingG = grams,
            servingLabel = "1 portion",
        )
    }
}
