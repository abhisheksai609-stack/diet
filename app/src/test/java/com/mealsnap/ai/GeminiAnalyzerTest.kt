package com.mealsnap.ai

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeminiAnalyzerTest {

    private fun fixture(name: String): String =
        javaClass.getResource("/gemini/$name")!!.readText()

    // ---- request ------------------------------------------------------------------------------

    @Test
    fun requestCarriesThePhotoThePromptAndJsonMode() {
        val root = Json.parseToJsonElement(GeminiAnalyzer.buildRequest("QUJD", "")).jsonObject

        val parts = root["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        val inline = parts[0].jsonObject["inline_data"]!!.jsonObject
        assertEquals("image/jpeg", inline["mime_type"]!!.jsonPrimitive.content)
        assertEquals("QUJD", inline["data"]!!.jsonPrimitive.content)

        val question = parts[1].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(question.contains("dish_name"))   // the JSON shape is spelled out in words
        assertTrue(question.contains("visual_cues"))
        assertFalse(question.contains("The person says"))

        val system = root["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(system.contains("food recognition"))

        val config = root["generationConfig"]!!.jsonObject
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
    }

    @Test
    fun aDescriptionIsPassedAsGroundTruth() {
        val body = GeminiAnalyzer.buildRequest("QUJD", "  half portion, no onions ")
        val question = Json.parseToJsonElement(body).jsonObject["contents"]!!.jsonArray[0].jsonObject["parts"]!!
            .jsonArray[1].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(question.contains("half portion, no onions"))
        assertTrue(question.contains("ground truth"))
    }

    // ---- real response ------------------------------------------------------------------------

    @Test
    fun parsesARealGeminiAnswerForAFoodPhoto() {
        // Captured from the live API for a photo of a burger with onion rings and fries.
        val a = GeminiAnalyzer.parseResponse(fixture("burger_flash_lite_response.json"))

        assertTrue(a.isFood)
        assertTrue(a.dishName.isNotBlank())
        assertTrue("the burger is one of the items", a.items.any { it.name.contains("burger", ignoreCase = true) })
        assertEquals("Fast food", a.cuisine)
        assertTrue("several items, not just one dish", a.items.size >= 3)
        assertTrue(a.items.all { it.portion.isNotBlank() && it.calories > 0 })
        assertEquals(1020, a.totalCalories)
        assertEquals(a.items.sumOf { it.calories }, a.totalCalories)
        assertTrue(a.visualCues.isNotEmpty())
        assertEquals("high", a.confidence)
        assertTrue(a.sodiumMg > 0 && a.fiberG > 0)
    }

    @Test
    fun aRefusalOrBlockedPhotoGivesAReadableError() {
        val blocked = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        expectError(blocked, "declined")

        val noText = """{"candidates":[{"finishReason":"SAFETY"}]}"""
        expectError(noText, "declined")

        val cutOff = """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[]}}]}"""
        expectError(cutOff, "cut off")

        expectError("""{"candidates":[]}""", "no answer")
        expectError("not json", "understand")
    }

    @Test
    fun aNotFoodAnswerStillParsesSoTheServiceCanReportIt() {
        val reply = """{"candidates":[{"content":{"parts":[{"text":"{\"is_food\": false, \"dish_name\": \"\", \"cuisine\": \"\", \"items\": [], \"total_calories\": 0, \"protein_g\": 0, \"carbs_g\": 0, \"fat_g\": 0, \"fiber_g\": 0, \"sugar_g\": 0, \"sodium_mg\": 0, \"visual_cues\": [], \"confidence\": \"high\", \"notes\": \"A phone screen\"}"}]}}]}"""
        assertFalse(GeminiAnalyzer.parseResponse(reply).isFood)
    }

    // ---- errors -------------------------------------------------------------------------------

    @Test
    fun httpFailuresBecomeAdviceNotStatusCodes() {
        fun msg(status: Int, body: String = "") = GeminiAnalyzer.messageFor(status, body)

        assertTrue(msg(400, """{"error":{"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""").contains("rejected"))
        assertTrue(msg(403).contains("isn't allowed"))
        assertTrue(msg(429, """{"error":{"status":"RESOURCE_EXHAUSTED"}}""").contains("quota"))
        // Real message Google returns for retired models.
        assertTrue(msg(404, """{"error":{"message":"This model models/gemini-2.5-flash is no longer available to new users.","status":"NOT_FOUND"}}""").contains("isn't available"))
        assertTrue(msg(503, """{"error":{"message":"This model is currently experiencing high demand.","status":"UNAVAILABLE"}}""").contains("problem"))
    }

    // ---- model choice -------------------------------------------------------------------------

    @Test
    fun modelRankingPrefersStableFastModelsAndSkipsUnsuitableOnes() {
        // The real list of models returned for a free key.
        val ranked = GeminiAnalyzer.rankModels(fixture("models_list.json"))

        assertTrue("expected plenty of candidates, got $ranked", ranked.size >= 5)
        assertTrue("first choice should be a stable Flash-Lite: ${ranked.first()}", ranked.first().contains("flash-lite"))
        assertFalse(ranked.first().contains("preview"))

        // Never the pro, image, speech, gemma, omni or alias entries.
        val bad = listOf("pro", "image", "tts", "gemma", "omni", "latest", "transcribe", "lyria", "antigravity", "deep-research")
        for (name in ranked) assertTrue("$name should not be offered", bad.none { name.contains(it) })

        // Fast lite models come before the busy full Flash ones, and retired 2.5 models come last.
        assertTrue(ranked.indexOf("gemini-3.1-flash-lite") < ranked.indexOf("gemini-3.8-flash"))
        assertTrue(ranked.indexOf("gemini-3.8-flash") < ranked.indexOf("gemini-2.5-flash"))
        assertNotNull(ranked.firstOrNull { it == "gemini-3.5-flash-lite" })
    }

    @Test
    fun rankingToleratesGarbage() {
        assertTrue(GeminiAnalyzer.rankModels("nonsense").isEmpty())
        assertTrue(GeminiAnalyzer.rankModels("""{"models":[{"name":"models/gemini-3-flash","supportedGenerationMethods":["embedContent"]}]}""").isEmpty())
    }

    private fun expectError(body: String, contains: String) {
        try {
            GeminiAnalyzer.parseResponse(body)
            fail("expected an AnalysisException for: $body")
        } catch (e: AnalysisException) {
            assertTrue("\"${e.message}\" should mention \"$contains\"", e.message!!.contains(contains, ignoreCase = true))
        }
    }
}
