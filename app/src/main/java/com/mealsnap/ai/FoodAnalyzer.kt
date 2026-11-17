package com.mealsnap.ai

import android.util.Base64
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.time.Duration

/** An error whose [message] is safe to show to the user as-is. */
class AnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Sends a meal photo to Claude and returns a structured calorie / macro estimate.
 *
 * Uses the official Anthropic Java SDK. Structured outputs guarantee the reply matches
 * [SCHEMA], and the server-side refusal fallback is switched on so a (very unlikely) safety
 * refusal is retried on another model instead of failing the request.
 */
class FoodAnalyzer {

    suspend fun analyze(
        apiKey: String,
        modelId: String,
        jpeg: ByteArray,
        hint: String = "",
    ): FoodAnalysis = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AnalysisException("Add your Anthropic API key in Settings first.")

        val params = buildParams(modelId, Base64.encodeToString(jpeg, Base64.NO_WRAP), hint)

        try {
            val client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey.trim())
                .timeout(Duration.ofSeconds(120))
                .maxRetries(2)
                .build()
            val message = try {
                client.beta().messages().create(params)
            } finally {
                client.close()
            }

            if (message.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
                throw AnalysisException("The AI declined to analyse this photo. You can enter the calories manually.")
            }
            if (message.stopReason().orElse(null) == BetaStopReason.MAX_TOKENS) {
                throw AnalysisException("The estimate was cut off. Try again, or enter the calories manually.")
            }

            val text = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            if (text.isBlank()) throw AnalysisException("The AI returned an empty answer. Please try again.")
            FoodAnalysisParser.parse(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AnalysisException) {
            throw e
        } catch (e: UnauthorizedException) {
            throw AnalysisException("Your API key was rejected. Check it in Settings.", e)
        } catch (e: PermissionDeniedException) {
            throw AnalysisException("This API key isn't allowed to use that model. Check your Anthropic account.", e)
        } catch (e: RateLimitException) {
            throw AnalysisException("Too many requests right now. Wait a moment and try again.", e)
        } catch (e: BadRequestException) {
            throw AnalysisException("The request was rejected: ${e.message ?: "bad request"}", e)
        } catch (e: AnthropicServiceException) {
            throw AnalysisException("The AI service had a problem (HTTP ${e.statusCode()}). Please try again.", e)
        } catch (e: AnthropicIoException) {
            throw AnalysisException("Couldn't reach the AI service. Check your internet connection.", e)
        } catch (e: SerializationException) {
            throw AnalysisException("Couldn't understand the AI's answer. Please try again.", e)
        } catch (e: IllegalArgumentException) {
            throw AnalysisException("Couldn't understand the AI's answer. Please try again.", e)
        }
    }

    companion object {
        /** Builds the request. Separate from [analyze] so it can be unit-tested without a network. */
        internal fun buildParams(modelId: String, imageBase64: String, hint: String): MessageCreateParams {
            val image = BetaImageBlockParam.builder()
                .source(
                    BetaBase64ImageSource.builder()
                        .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                        .data(imageBase64)
                        .build(),
                )
                .build()

            val question = buildString {
                append("Estimate the calories and macros of the food in this photo.")
                if (hint.isNotBlank()) {
                    append("\n\nThe person says: \"").append(hint.trim()).append("\"")
                    append("\nTreat that as ground truth where it conflicts with what you see.")
                }
            }

            return MessageCreateParams.builder()
                .model(modelId)
                .maxTokens(6000L)
                .addBeta("server-side-fallback-2026-07-01")
                .fallbacksDefault()
                .system(SYSTEM_PROMPT)
                .outputConfig(
                    BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.LOW)
                        .format(
                            BetaJsonOutputFormat.builder()
                                .schema(
                                    BetaJsonOutputFormat.Schema.builder().apply {
                                        SCHEMA.forEach { (key, value) -> putAdditionalProperty(key, JsonValue.from(value)) }
                                    }.build(),
                                )
                                .build(),
                        )
                        .build(),
                )
                .addUserMessageOfContentBlockParams(
                    listOf(
                        BetaContentBlockParam.ofImage(image),
                        BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(question).build()),
                    ),
                )
                .build()
        }

        private const val SYSTEM_PROMPT ="""You are the nutrition estimator inside a personal calorie-tracking app. The user photographs their food just before eating it, and you estimate what is on the plate.

How to estimate:
- Identify every distinct food and drink item you can see.
- Judge portion sizes from visual cues: plate or bowl size, cutlery, cups, packaging, hands, and how full the plate is.
- Estimate calories (kcal) for the portion shown, not for a standard serving. Include visible cooking fats, sauces and dressings.
- Give protein, carbohydrate and fat in grams for the whole meal.
- Give one central, realistic estimate, not a range. Round calories to the nearest 5 and grams to whole numbers.
- total_calories must equal the sum of the item calories.
- meal_name is a short, natural name such as "Chicken burrito bowl".
- confidence is "high" for simple, clearly visible foods, "medium" when portions or ingredients are uncertain, and "low" when much is hidden or unclear.
- notes is one short sentence stating the main assumption (for example "Assumed about 1 tbsp of olive oil on the salad").
- If the image contains no food or drink, set is_food to false, meal_name to an empty string, items to an empty list and every number to 0.
- If the user adds a description, treat it as ground truth over what you infer from the image."""

        private val INT = mapOf("type" to "integer")
        private val STRING = mapOf("type" to "string")

        private val SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf(
                "is_food", "meal_name", "items", "total_calories",
                "protein_g", "carbs_g", "fat_g", "confidence", "notes",
            ),
            "properties" to mapOf(
                "is_food" to mapOf("type" to "boolean"),
                "meal_name" to STRING,
                "items" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to listOf("name", "portion", "calories"),
                        "properties" to mapOf(
                            "name" to STRING,
                            "portion" to STRING,
                            "calories" to INT,
                        ),
                    ),
                ),
                "total_calories" to INT,
                "protein_g" to INT,
                "carbs_g" to INT,
                "fat_g" to INT,
                "confidence" to mapOf("type" to "string", "enum" to listOf("high", "medium", "low")),
                "notes" to STRING,
            ),
        )
    }
}
