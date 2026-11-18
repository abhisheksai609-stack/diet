package com.mealsnap.ai

import com.mealsnap.coach.TipKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiCoachTest {

    private fun fixture(name: String) = javaClass.getResource("/gemini/$name")!!.readText()

    // ---- requests -----------------------------------------------------------------------------

    @Test
    fun aTextRequestCarriesTheSystemPromptAndTheConversation() {
        val body = GeminiAnalyzer.buildTextRequest(
            system = "You are a coach.",
            turns = listOf(ChatTurn(true, "hello"), ChatTurn(false, "hi there"), ChatTurn(true, "what should I eat?")),
            json = false, maxTokens = 1024, temperature = 0.6,
        )
        val root = Json.parseToJsonElement(body).jsonObject

        assertEquals("You are a coach.", root["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val contents = root["contents"]!!.jsonArray
        assertEquals(listOf("user", "model", "user"), contents.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertEquals("what should I eat?", contents.last().jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val config = root["generationConfig"]!!.jsonObject
        assertFalse("plain text mode has no JSON mime type", config.containsKey("responseMimeType"))
        assertEquals(1024, config["maxOutputTokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun jsonModeIsRequestedForTheBriefing() {
        val body = GeminiAnalyzer.buildTextRequest("s", listOf(ChatTurn(true, "x")), json = true, maxTokens = 4096, temperature = 0.5)
        assertEquals("application/json", Json.parseToJsonElement(body).jsonObject["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content)
    }

    @Test
    fun theSystemPromptKeepsTheTrainerSafe() {
        val p = CoachPrompts.SYSTEM
        assertTrue(p.contains("not a doctor"))
        assertTrue(p.contains("never suggest eating below"))
        assertTrue(p.contains("No extreme diets"))
        assertTrue(p.contains("Never invent data"))
    }

    // ---- real answers -------------------------------------------------------------------------

    @Test
    fun parsesARealBriefing() {
        // Captured from the live API for a fictional person.
        val briefing = CoachParser.parseBriefing(GeminiAnalyzer.extractText(fixture("coach_briefing_response.json")))!!

        assertTrue(briefing.summary.isNotBlank())
        assertTrue("3 to 5 tips, got ${briefing.tips.size}", briefing.tips.size in 3..5)
        assertTrue(briefing.tips.all { it.title.isNotBlank() && it.body.isNotBlank() })
        assertTrue("a short night should be flagged", briefing.tips.any { it.kind == TipKind.WATCH && it.body.contains("5 hours", ignoreCase = true) })
        assertTrue(briefing.workout.isNotBlank())
        assertTrue(briefing.mealIdea.isNotBlank())
    }

    @Test
    fun aRealChatAnswerIsPlainReadableText() {
        val text = CoachParser.cleanChat(GeminiAnalyzer.extractText(fixture("coach_chat_response.json")))
        assertTrue(text.contains("5 hours 40 minutes"))
        assertTrue(text.contains("doctor"))
        assertFalse(text.contains("**"))
    }

    // ---- tolerance ----------------------------------------------------------------------------

    @Test
    fun theBriefingParserCopesWithChatterAndMissingParts() {
        val reply = "Sure!\n```json\n{\"summary\": \"Doing fine.\", \"tips\": [{\"title\": \"Walk\", \"body\": \"Go walk.\", \"kind\": \"GOOD\"}, {\"body\": \"No title\"}, 42]}\n```"
        val b = CoachParser.parseBriefing(reply)!!

        assertEquals("Doing fine.", b.summary)
        assertEquals(2, b.tips.size)
        assertEquals(TipKind.GOOD, b.tips[0].kind)
        assertEquals(TipKind.INFO, b.tips[1].kind)
        assertEquals("", b.workout)
    }

    @Test
    fun nonsenseIsRejectedNotCrashed() {
        assertNull(CoachParser.parseBriefing("I think you should eat more vegetables."))
        assertNull(CoachParser.parseBriefing("{ \"summary\": \"\", \"tips\": [] }"))
        assertNull(CoachParser.parseBriefing("{ broken"))
    }

    @Test
    fun markdownIsStrippedFromChatAnswers() {
        val raw = "## Your plan\n\n**Eat more protein.**\n\n* Eggs at breakfast\n* Dal at lunch\n\n\n\nStay hydrated."
        val clean = CoachParser.cleanChat(raw)

        assertEquals("Your plan\n\nEat more protein.\n\n- Eggs at breakfast\n- Dal at lunch\n\nStay hydrated.", clean)
        assertNotNull(clean)
    }

    @Test
    fun blockedOrEmptyAnswersGiveAReadableError() {
        try {
            GeminiAnalyzer.extractText("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
            org.junit.Assert.fail("expected an error")
        } catch (e: AnalysisException) {
            assertTrue(e.message!!.contains("declined"))
        }
    }
}
