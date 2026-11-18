package com.mealsnap.ai

import com.anthropic.core.jsonMapper
import com.fasterxml.jackson.databind.JsonNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the exact JSON the SDK would put on the wire, without touching the network.
 * (A live call needs a real API key, so this is what guards against request-shape mistakes.)
 */
class FoodAnalyzerRequestTest {

    private fun body(hint: String = ""): JsonNode {
        val params = FoodAnalyzer.buildParams("claude-opus-5-5", "QUJD", hint)
        val mapper = jsonMapper()
        return mapper.readTree(mapper.writeValueAsString(params._body()))
    }

    @Test
    fun sendsModelTokenLimitAndFallbackOptIn() {
        val json = body()

        assertEquals("claude-opus-5-5", json["model"].asText())
        assertEquals(6000, json["max_tokens"].asInt())
        assertTrue("server-side fallbacks must be requested", json.has("fallbacks"))
        assertEquals("low", json["output_config"]["effort"].asText())
    }

    @Test
    fun betaHeaderForFallbackIsSent() {
        val params = FoodAnalyzer.buildParams("claude-opus-5-5", "QUJD", "")
        val betaHeader = params._headers().values("anthropic-beta").joinToString(",")
        assertTrue(betaHeader, betaHeader.contains("server-side-fallback-2026-07-01"))
    }

    @Test
    fun userMessageHasJpegImageThenQuestion() {
        val content = body()["messages"][0]["content"]

        assertEquals("image", content[0]["type"].asText())
        assertEquals("base64", content[0]["source"]["type"].asText())
        assertEquals("image/jpeg", content[0]["source"]["media_type"].asText())
        assertEquals("QUJD", content[0]["source"]["data"].asText())
        assertEquals("text", content[1]["type"].asText())
        assertFalse(content[1]["text"].asText().contains("The person says"))
    }

    @Test
    fun hintIsPassedAsGroundTruth() {
        val text = body("  oat-milk latte, no sugar ")["messages"][0]["content"][1]["text"].asText()

        assertTrue(text.contains("oat-milk latte, no sugar"))
        assertTrue(text.contains("ground truth"))
    }

    @Test
    fun structuredOutputSchemaMatchesWhatTheParserExpects() {
        val format = body()["output_config"]["format"]
        assertEquals("json_schema", format["type"].asText())

        val schema = format["schema"]
        assertEquals("object", schema["type"].asText())
        assertFalse(schema["additionalProperties"].asBoolean(true))

        val required = schema["required"].map { it.asText() }.toSet()
        val properties = schema["properties"].fieldNames().asSequence().toSet()
        val expected = setOf(
            "is_food", "dish_name", "cuisine", "items", "total_calories",
            "protein_g", "carbs_g", "fat_g", "fiber_g", "sugar_g", "sodium_mg",
            "visual_cues", "confidence", "notes",
        )
        assertEquals(expected, required)
        assertEquals(expected, properties)
        assertEquals("integer", schema["properties"]["total_calories"]["type"].asText())
        assertEquals(
            setOf("high", "medium", "low"),
            schema["properties"]["confidence"]["enum"].map { it.asText() }.toSet(),
        )
        assertEquals("array", schema["properties"]["items"]["type"].asText())
        assertEquals(
            setOf("name", "portion", "calories", "protein_g", "carbs_g", "fat_g"),
            schema["properties"]["items"]["items"]["required"].map { it.asText() }.toSet(),
        )
        assertEquals("array", schema["properties"]["visual_cues"]["type"].asText())
        assertEquals("string", schema["properties"]["visual_cues"]["items"]["type"].asText())
    }

    @Test
    fun everyRequiredSchemaFieldIsKnownToTheParser() {
        // If the schema and the parser's data class drift apart, replies would silently lose fields.
        val schemaFields = body()["output_config"]["format"]["schema"]["properties"].fieldNames().asSequence().toSet()
        val parserFields = com.mealsnap.ai.FoodAnalysis.serializer().descriptor.let { d ->
            (0 until d.elementsCount).map { d.getElementName(it) }.toSet()
        }
        assertEquals(parserFields, schemaFields)
    }

    @Test
    fun aSchemaCompliantReplyParsesEndToEnd() {
        // Shape of a reply that satisfies the schema above.
        val reply = """{"is_food":true,"dish_name":"Margherita pizza slice","cuisine":"Italian",
            "items":[{"name":"Margherita pizza","portion":"1 large slice","calories":285,
                      "protein_g":12,"carbs_g":36,"fat_g":10}],
            "total_calories":285,"protein_g":12,"carbs_g":36,"fat_g":10,"fiber_g":2,"sugar_g":4,"sodium_mg":640,
            "visual_cues":["Thin crust, 14 inch pie cut in 8"],"confidence":"medium",
            "notes":"Assumed a thin-crust 14 inch pizza cut in 8."}"""

        val result = FoodAnalysisParser.parse(reply)

        assertEquals(285, result.totalCalories)
        assertEquals(1, result.items.size)
        assertEquals("Margherita pizza", result.items.single().name)
        assertEquals("Italian", result.cuisine)
    }
}
