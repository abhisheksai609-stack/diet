package com.mealsnap.ai

import android.util.Base64
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.NotFoundException
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
open class AnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The AI looked at the photo and says it isn't food. Not worth retrying on another engine. */
class NotFoodException : AnalysisException(
    "That doesn't look like food. Add a short description and tap Re-estimate, or enter it by hand.",
)

/**
 * Sends a meal photo to Claude and returns a structured, detailed nutrition estimate: the dish
 * name, every item with its portion and macros, totals, and the visual cues it relied on.
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
        requireKey(apiKey)
        val params = buildParams(modelId, Base64.encodeToString(jpeg, Base64.NO_WRAP), hint)

        guarded {
            val client = newClient(apiKey, timeout = Duration.ofSeconds(120), retries = 2)
            val message = try {
                client.beta().messages().create(params)
            } finally {
                client.close()
            }

            if (message.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
                throw AnalysisException("The AI declined to analyse this photo. You can enter the details manually.")
            }
            if (message.stopReason().orElse(null) == BetaStopReason.MAX_TOKENS) {
                throw AnalysisException("The estimate was cut off. Try again, or enter the details manually.")
            }

            val text = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            if (text.isBlank()) throw AnalysisException("The AI returned an empty answer. Please try again.")
            FoodAnalysisParser.parse(text)
        }
    }

    /**
     * Cheap check that [apiKey] works and can use [modelId] (a metadata lookup, no tokens billed).
     * Throws [AnalysisException] with a user-facing reason when it doesn't.
     */
    suspend fun verifyKey(apiKey: String, modelId: String) = withContext(Dispatchers.IO) {
        requireKey(apiKey)
        guarded {
            val client = newClient(apiKey, timeout = Duration.ofSeconds(20), retries = 1)
            try {
                client.models().retrieve(modelId)
            } finally {
                client.close()
            }
            Unit
        }
    }

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) throw AnalysisException("Add your Anthropic API key in Settings first.")
    }

    private fun newClient(apiKey: String, timeout: Duration, retries: Int): AnthropicClient =
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey.trim())
            .timeout(timeout)
            .maxRetries(retries)
            .build()

    /** Runs [block], turning SDK and parsing failures into messages a person can act on. */
    private inline fun <T> guarded(block: () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AnalysisException) {
            throw e
        } catch (e: UnauthorizedException) {
            throw AnalysisException("That API key was rejected. Check that you copied the whole key.", e)
        } catch (e: PermissionDeniedException) {
            throw AnalysisException("This API key isn't allowed to use that model. Check your Anthropic account.", e)
        } catch (e: NotFoundException) {
            throw AnalysisException("That model isn't available to this key. Pick another model in Settings.", e)
        } catch (e: RateLimitException) {
            throw AnalysisException("Too many requests right now. Wait a moment and try again.", e)
        } catch (e: BadRequestException) {
            throw AnalysisException("The request was rejected: ${e.message ?: "bad request"}. If it mentions credits, add some in the Anthropic Console.", e)
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
                append("Identify the food in this photo and log it in detail.")
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
                .system(FoodPrompts.SYSTEM)
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


        private val INT = mapOf("type" to "integer")
        private val STRING = mapOf("type" to "string")

        private val ITEM = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("name", "portion", "calories", "protein_g", "carbs_g", "fat_g"),
            "properties" to mapOf(
                "name" to STRING,
                "portion" to STRING,
                "calories" to INT,
                "protein_g" to INT,
                "carbs_g" to INT,
                "fat_g" to INT,
            ),
        )

        private val SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf(
                "is_food", "dish_name", "cuisine", "items", "total_calories",
                "protein_g", "carbs_g", "fat_g", "fiber_g", "sugar_g", "sodium_mg",
                "visual_cues", "confidence", "notes",
            ),
            "properties" to mapOf(
                "is_food" to mapOf("type" to "boolean"),
                "dish_name" to STRING,
                "cuisine" to STRING,
                "items" to mapOf("type" to "array", "items" to ITEM),
                "total_calories" to INT,
                "protein_g" to INT,
                "carbs_g" to INT,
                "fat_g" to INT,
                "fiber_g" to INT,
                "sugar_g" to INT,
                "sodium_mg" to INT,
                "visual_cues" to mapOf("type" to "array", "items" to STRING),
                "confidence" to mapOf("type" to "string", "enum" to listOf("high", "medium", "low")),
                "notes" to STRING,
            ),
        )
    }
}
