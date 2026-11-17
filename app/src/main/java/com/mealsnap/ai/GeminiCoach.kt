package com.mealsnap.ai

import com.mealsnap.coach.Tip
import com.mealsnap.coach.TipKind
import com.mealsnap.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A coaching summary for right now. */
data class Briefing(
    val summary: String,
    val tips: List<Tip>,
    val workout: String,
    val mealIdea: String,
)

/**
 * The AI personal trainer. It is given a text summary of the person's own numbers (see
 * [com.mealsnap.coach.CoachContext]) and answers with Google Gemini, using the person's own free API key.
 * Nothing is sent unless the person has agreed to it in the app.
 */
class GeminiCoach(private val gemini: GeminiAnalyzer, private val settings: SettingsRepository) {

    suspend fun briefing(context: String): Briefing {
        val key = settings.geminiKey.first()
        val model = settings.geminiModel.first().id
        val text = gemini.generateText(
            apiKey = key,
            preferredModel = model,
            system = CoachPrompts.SYSTEM + "\n\nTHE PERSON'S DATA (as of now):\n" + context,
            turns = listOf(ChatTurn(true, CoachPrompts.BRIEFING_REQUEST)),
            json = true,
            maxTokens = 4096,
            temperature = 0.5,
        )
        return CoachParser.parseBriefing(text) ?: throw AnalysisException("Couldn't understand the trainer's answer. Please try again.")
    }

    suspend fun chat(context: String, history: List<ChatTurn>, question: String): String {
        val key = settings.geminiKey.first()
        val model = settings.geminiModel.first().id
        val reply = gemini.generateText(
            apiKey = key,
            preferredModel = model,
            system = CoachPrompts.SYSTEM + "\n\nTHE PERSON'S DATA (as of now):\n" + context,
            turns = history.takeLast(MAX_HISTORY) + ChatTurn(true, question),
            json = false,
            maxTokens = 1024,
            temperature = 0.6,
        )
        return CoachParser.cleanChat(reply)
    }

    private companion object {
        const val MAX_HISTORY = 12
    }
}

internal object CoachPrompts {
    const val SYSTEM = """You are MealSnap Coach, a friendly, practical personal trainer and nutrition coach inside a calorie-tracking app. You are talking to one person. You are given a summary of their own numbers: profile, targets, food eaten, activity, heart rate, sleep and weight.

Rules:
- Base every answer on the numbers you are given and quote specific figures. Never invent data. If something isn't provided, say you don't have it.
- Be encouraging, specific and brief. Use short paragraphs, or short lines starting with "- ". No markdown headings, no bold, no tables.
- Give safe, mainstream guidance: evidence-based nutrition, strength and cardio training, sleep, recovery. No extreme diets, no fasting or detox advice, and never suggest eating below the person's calorie target.
- You are not a doctor. For chest pain, dizziness, fainting, a persistently very high or low resting heart rate, low blood oxygen, injuries, pregnancy, eating disorders, medical conditions or medication questions, tell them to see a doctor.
- Suit food ideas to what the person actually eats (for example Indian dishes if their log shows them) and to what is left of today's calories and protein.
- Keep answers under 180 words unless the person asks for a plan."""

    const val BRIEFING_REQUEST = """Give me my coaching briefing for right now.
Reply with one JSON object and nothing else, with exactly these keys:
summary (string, at most two sentences on how I'm doing today),
tips (array of 3 to 5 objects, each with title (string, at most 6 words), body (string, at most 35 words) and kind ("good", "info" or "watch")),
workout (string: one specific workout suggestion for today that respects my sleep and heart-rate data, at most 40 words),
meal_idea (string: one specific idea for my next meal that fits what is left of my calories and protein, at most 35 words)."""
}

internal object CoachParser {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parseBriefing(text: String): Briefing? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = runCatching { json.parseToJsonElement(text.substring(start, end + 1)) }.getOrNull() as? JsonObject ?: return null

        fun str(key: String) = (root[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val tips = (root["tips"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = (obj["title"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val body = (obj["body"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (title.isEmpty() && body.isEmpty()) return@mapNotNull null
            val kind = when ((obj["kind"] as? JsonPrimitive)?.contentOrNull?.lowercase()) {
                "good" -> TipKind.GOOD
                "watch" -> TipKind.WATCH
                else -> TipKind.INFO
            }
            Tip(title, body, kind)
        }.take(5)

        val summary = str("summary")
        if (summary.isEmpty() && tips.isEmpty()) return null
        return Briefing(summary, tips, str("workout"), str("meal_idea"))
    }

    /** The app shows plain text, so strip any markdown the model adds despite being asked not to. */
    fun cleanChat(text: String): String =
        text.replace("**", "").replace("__", "")
            .lines().joinToString("\n") { line ->
                line.trimEnd()
                    .replace(Regex("^#{1,6}\\s*"), "")
                    .replace(Regex("^\\s*[*•]\\s+"), "- ")
            }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}
