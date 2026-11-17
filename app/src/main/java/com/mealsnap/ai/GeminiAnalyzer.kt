package com.mealsnap.ai

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Identifies a meal photo with Google's Gemini API, using a free key from Google AI Studio.
 *
 * Plain REST over HttpsURLConnection: one POST with the photo and the prompt, authenticated by the
 * `x-goog-api-key` header. Model names change often, so the app asks Google which models the key can
 * use and falls back to the best "Flash" model if the preferred one isn't found.
 */
class GeminiAnalyzer {

    suspend fun analyze(apiKey: String, preferredModel: String, jpeg: ByteArray, hint: String): FoodAnalysis =
        withContext(Dispatchers.IO) {
            requireKey(apiKey)
            val body = buildRequest(Base64.encodeToString(jpeg, Base64.NO_WRAP), hint)
            guarded {
                val (_, response) = generateWithFallback(apiKey, preferredModel, body, ANALYZE_TIMEOUT_MS)
                parseResponse(response.body)
            }
        }

    /**
     * Checks the key and that a model really answers (a tiny text request, so it costs next to nothing).
     * Returns the model that worked. Throws [AnalysisException] with a readable reason otherwise.
     */
    suspend fun verifyKey(apiKey: String, preferredModel: String): String = withContext(Dispatchers.IO) {
        requireKey(apiKey)
        guarded {
            val list = http("GET", "$BASE/models?pageSize=100", apiKey, null, 20_000)
            if (list.status !in 200..299) throw AnalysisException(messageFor(list.status, list.body))
            val probe = buildJsonObject {
                putJsonArray("contents") {
                    add(buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", "Name one fruit. One word.") }) } })
                }
            }.toString()
            try {
                generateWithFallback(apiKey, preferredModel, probe, VERIFY_TIMEOUT_MS, knownList = list.body).first
            } catch (e: AnalysisException) {
                throw AnalysisException("Your key works, but Google's models are busy or unavailable right now (${e.message?.trimEnd('.')}). Try again in a few minutes.", e)
            }
        }
    }

    /**
     * Calls `generateContent`, moving on to the next model when one is retired (404), rate-limited (429),
     * overloaded (5xx) or silent (timeout). Model names and capacity change often, so no single name is trusted.
     * Returns the model that answered together with its response.
     */
    private fun generateWithFallback(
        apiKey: String,
        preferredModel: String,
        body: String,
        readTimeoutMs: Int,
        knownList: String? = null,
    ): Pair<String, Response> {
        val candidates = mutableListOf(preferredModel.ifBlank { DEFAULT_MODEL })
        var listed = false
        var last: AnalysisException? = null
        var index = 0
        while (index < candidates.size && index < MAX_ATTEMPTS) {
            val model = candidates[index++]
            val response = try {
                http("POST", "$BASE/models/$model:generateContent", apiKey, body, readTimeoutMs)
            } catch (_: SocketTimeoutException) {
                last = AnalysisException("Google took too long to answer.")
                null
            }
            if (response != null) {
                if (response.status in 200..299) return model to response
                val problem = AnalysisException(messageFor(response.status, response.body))
                if (!isRetryable(response.status)) throw problem
                last = problem
            }
            if (!listed && index >= candidates.size) {
                listed = true
                val list = knownList ?: listModels(apiKey)
                if (list != null) candidates += rankModels(list).filter { it !in candidates }
            }
        }
        throw last ?: AnalysisException("Google returned no answer. Please try again.")
    }

    private fun isRetryable(status: Int) = status == 404 || status == 429 || status >= 500

    private fun listModels(apiKey: String): String? =
        runCatching { http("GET", "$BASE/models?pageSize=100", apiKey, null, 20_000) }
            .getOrNull()?.takeIf { it.status in 200..299 }?.body

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) throw AnalysisException("Add your Google Gemini key in Settings first.")
    }

    private class Response(val status: Int, val body: String)

    private fun http(method: String, url: String, apiKey: String, body: String?, timeoutMs: Int): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("x-goog-api-key", apiKey.trim())
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Response(status, text)
        } finally {
            conn.disconnect()
        }
    }

    private inline fun <T> guarded(block: () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AnalysisException) {
            throw e
        } catch (e: UnknownHostException) {
            throw AnalysisException("Couldn't reach Google. Check your internet connection.", e)
        } catch (e: SocketTimeoutException) {
            throw AnalysisException("Google took too long to answer. Please try again.", e)
        } catch (e: IOException) {
            throw AnalysisException("Couldn't reach Google. Check your internet connection.", e)
        } catch (e: SerializationException) {
            throw AnalysisException("Couldn't understand Google's answer. Please try again.", e)
        } catch (e: IllegalArgumentException) {
            throw AnalysisException("Couldn't understand Google's answer. Please try again.", e)
        }
    }

    companion object {
        /** Fast and reliable on the free tier. The full "Flash" models are often busy or slow. */
        const val DEFAULT_MODEL = "gemini-flash-lite-latest"
        const val FLASH_MODEL = "gemini-flash-latest"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        private const val ANALYZE_TIMEOUT_MS = 25_000
        private const val VERIFY_TIMEOUT_MS = 15_000
        private const val MAX_ATTEMPTS = 4

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** The JSON body for one `generateContent` call. */
        internal fun buildRequest(imageBase64: String, hint: String): String {
            val question = buildString {
                append("Identify the food in this photo and log it in detail.\n\n")
                append(FoodPrompts.JSON_SHAPE)
                if (hint.isNotBlank()) {
                    append("\n\nThe person says: \"").append(hint.trim()).append("\"")
                    append("\nTreat that as ground truth where it conflicts with what you see.")
                }
            }
            return buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { add(buildJsonObject { put("text", FoodPrompts.SYSTEM) }) }
                }
                putJsonArray("contents") {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                add(
                                    buildJsonObject {
                                        putJsonObject("inline_data") {
                                            put("mime_type", "image/jpeg")
                                            put("data", imageBase64)
                                        }
                                    },
                                )
                                add(buildJsonObject { put("text", question) })
                            }
                        },
                    )
                }
                putJsonObject("generationConfig") {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                    put("maxOutputTokens", 8192)
                }
            }.toString()
        }

        /** Reads the model's JSON answer out of a `generateContent` response. */
        internal fun parseResponse(body: String): FoodAnalysis {
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                ?: throw AnalysisException("Couldn't understand Google's answer. Please try again.")

            val blocked = (root["promptFeedback"] as? JsonObject)?.get("blockReason")?.str()
            if (blocked != null) throw AnalysisException("Google declined to look at this photo ($blocked). You can enter the details manually.")

            val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
                ?: throw AnalysisException("Google returned no answer. Please try again.")
            val text = ((candidate["content"] as? JsonObject)?.get("parts") as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.get("text")?.str() }
                .joinToString("")

            if (text.isBlank()) {
                val reason = candidate["finishReason"]?.str()
                throw AnalysisException(
                    when (reason) {
                        "MAX_TOKENS" -> "The estimate was cut off. Try again, or enter the details manually."
                        "SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "IMAGE_SAFETY" ->
                            "Google declined to analyse this photo. You can enter the details manually."
                        else -> "Google returned an empty answer. Please try again."
                    },
                )
            }
            return FoodAnalysisParser.parse(text)
        }

        /** A message a person can act on for a failed HTTP call. */
        internal fun messageFor(status: Int, body: String): String {
            val error = (runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject)?.get("error") as? JsonObject
            val detail = error?.get("message")?.str().orEmpty()
            val reason = error?.get("status")?.str().orEmpty()
            return when {
                status == 400 && (detail.contains("API key", ignoreCase = true) || detail.contains("API_KEY")) ->
                    "That Google key was rejected. Check that you copied the whole key."
                status == 400 && detail.contains("location", ignoreCase = true) ->
                    "Google's Gemini API isn't available in your location with this key."
                status == 400 -> "Google rejected the request${if (detail.isNotBlank()) ": ${detail.take(160)}" else "."}"
                status == 401 -> "That Google key was rejected. Check that you copied the whole key."
                status == 403 -> "This Google key isn't allowed to use the Gemini API. Create a new key in Google AI Studio."
                status == 404 -> "That Gemini model isn't available to this key. Try the other model in Settings."
                status == 429 || reason == "RESOURCE_EXHAUSTED" ->
                    "Google's free quota is used up for now. It resets daily; meanwhile photos are identified on your phone."
                status >= 500 -> "Google's service had a problem (HTTP $status). Please try again."
                else -> "Google returned an error (HTTP $status)."
            }
        }

        private fun usableModels(listBody: String): List<String> {
            val models = ((runCatching { json.parseToJsonElement(listBody) }.getOrNull() as? JsonObject)?.get("models") as? JsonArray).orEmpty()
            return models.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = obj["name"]?.str() ?: return@mapNotNull null
                val methods = (obj["supportedGenerationMethods"] as? JsonArray).orEmpty().mapNotNull { it.str() }
                if ("generateContent" in methods) name.removePrefix("models/") else null
            }
        }

        private val excluded = listOf(
            "image", "tts", "audio", "live", "embedding", "thinking", "computer", "robotics", "learnlm", "gemma",
            "imagen", "veo", "omni", "transcribe", "nano-banana", "customtools",
        )
        private val version = Regex("""gemini-(\d+(?:\.\d+)?)-flash""")

        /**
         * Gemini "Flash" models that can read photos, best first: stable before preview, Flash-Lite before full
         * Flash (it is the one that is reliably fast and has the most generous free quota), newer before older.
         */
        internal fun rankModels(listBody: String): List<String> =
            usableModels(listBody)
                .filter { name -> excluded.none { name.contains(it) } }
                .mapNotNull { name ->
                    val v = version.find(name)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
                    Triple(name, !(name.contains("preview") || name.contains("exp")), name.contains("lite")) to v
                }
                .sortedWith(
                    compareByDescending<Pair<Triple<String, Boolean, Boolean>, Double>> { it.first.second }
                        .thenByDescending { it.first.third }
                        .thenByDescending { it.second }
                        .thenBy { it.first.first.length },
                )
                .map { it.first.first }

        private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    }
}
