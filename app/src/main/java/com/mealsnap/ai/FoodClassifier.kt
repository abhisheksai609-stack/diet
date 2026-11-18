package com.mealsnap.ai

import android.content.Context
import android.graphics.Bitmap
import com.mealsnap.nutrition.Dish
import com.mealsnap.nutrition.Match
import com.mealsnap.nutrition.NutritionDb
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

data class Prediction(val label: String, val score: Float)

object ClassifierLabels {
    /** Parses `id,name` rows; the list index equals the model's output index. */
    fun parse(csv: String): List<String> =
        csv.lines().drop(1).filter { it.isNotBlank() }.map { line ->
            line.substringAfter(',').trim().trim('"')
        }

    /** Labels such as "__background__" or Knowledge Graph ids ("/g/12abc") aren't dish names. */
    fun isDishName(label: String): Boolean =
        label.isNotBlank() && !label.startsWith("/") && !label.startsWith("__")
}

/**
 * Recognises roughly 2,000 dishes from a photo, entirely on the phone, using Google's AIY food
 * classifier (a quantised MobileNet, 192 x 192 input) through LiteRT. No account, key or internet.
 */
class FoodClassifier(private val context: Context) {

    private val labels: List<String> by lazy {
        ClassifierLabels.parse(context.assets.open(LABELS_ASSET).bufferedReader().use { it.readText() })
    }
    private val lock = Any()
    private var interpreter: Interpreter? = null

    private fun loadModel(): ByteBuffer =
        try {
            // Memory-map the asset (it is stored uncompressed, see noCompress in build.gradle.kts).
            context.assets.openFd(MODEL_ASSET).use { fd ->
                fd.createInputStream().channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        } catch (_: Exception) {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                put(bytes)
                rewind()
            }
        }

    fun classify(bitmap: Bitmap, topK: Int = 6): List<Prediction> = synchronized(lock) {
        val interp = interpreter ?: Interpreter(loadModel(), Interpreter.Options().setNumThreads(4)).also { interpreter = it }

        val input = interp.getInputTensor(0)
        val size = input.shape()[1]
        val isFloat = input.dataType() == DataType.FLOAT32

        val square = centerCropped(bitmap, size)
        val pixels = IntArray(size * size)
        square.getPixels(pixels, 0, size, 0, 0, size, size)
        if (square !== bitmap) square.recycle()

        val buffer = ByteBuffer.allocateDirect(size * size * 3 * (if (isFloat) 4 else 1)).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (isFloat) {
                buffer.putFloat((r - 127.5f) / 127.5f)
                buffer.putFloat((g - 127.5f) / 127.5f)
                buffer.putFloat((b - 127.5f) / 127.5f)
            } else {
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            }
        }
        buffer.rewind()

        val output = interp.getOutputTensor(0)
        val count = output.shape()[1]
        val scores = FloatArray(count)
        if (output.dataType() == DataType.FLOAT32) {
            val out = Array(1) { FloatArray(count) }
            interp.run(buffer, out)
            out[0].copyInto(scores)
        } else {
            val out = Array(1) { ByteArray(count) }
            interp.run(buffer, out)
            val params = output.quantizationParams()
            val scale = if (params.scale == 0f) 1f / 256f else params.scale
            for (i in 0 until count) scores[i] = ((out[0][i].toInt() and 0xFF) - params.zeroPoint) * scale
        }

        topPredictions(scores, labels, topK)
    }

    private fun centerCropped(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val cropped = Bitmap.createBitmap(source, x, y, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (cropped !== source && cropped !== scaled) cropped.recycle()
        return scaled
    }

    companion object {
        const val MODEL_ASSET = "food_classifier.tflite"
        const val LABELS_ASSET = "food_labels.csv"

        /** Best-scoring named dishes, highest first. Pure, so it can be unit-tested. */
        internal fun topPredictions(scores: FloatArray, labels: List<String>, topK: Int): List<Prediction> =
            scores.indices
                .filter { it < labels.size && ClassifierLabels.isDishName(labels[it]) }
                .sortedByDescending { scores[it] }
                .take(topK)
                .map { Prediction(labels[it], scores[it]) }
    }
}

/** Turns classifier predictions into a logged meal using the built-in nutrition table. */
object ClassifierMapper {
    private const val MIN_TOP_SCORE = 0.10f
    private const val MIN_CANDIDATE_SCORE = 0.04f

    private data class Candidate(val prediction: Prediction, val match: Match) {
        val rank: Double get() = prediction.score * match.trust
    }

    /** Returns null when the model isn't confident that the photo shows any particular dish. */
    fun toRecognition(predictions: List<Prediction>): Recognition? {
        val named = predictions.filter { ClassifierLabels.isDishName(it.label) }
        if (named.isEmpty() || named.first().score < MIN_TOP_SCORE) return null

        val candidates = named
            .filter { it.score >= MIN_CANDIDATE_SCORE }
            .map { Candidate(it, NutritionDb.match(it.label)) }
        val primary = candidates.maxByOrNull { it.rank } ?: return null

        val label = primary.prediction.label.replaceFirstChar { it.uppercase() }
        val entry = primary.match.entry
        val percent = (primary.prediction.score * 100).roundToInt()
        val dish = Dish.single(entry, name = label)

        val kind = primary.match.kind
        val confidence = when {
            kind == Match.Kind.GENERIC -> "low"
            primary.prediction.score >= 0.6f && kind != Match.Kind.CATEGORY -> "high"
            primary.prediction.score >= 0.3f -> "medium"
            else -> "low"
        }
        val notes = when (kind) {
            Match.Kind.EXACT, Match.Kind.PARTIAL ->
                "Recognised as “$label” ($percent% sure). Calories assume ${entry.servingLabel} of ${entry.name} " +
                    "(about ${entry.servingG} g); change the portion if yours differs."
            Match.Kind.CATEGORY ->
                "Recognised as “$label” ($percent% sure) and matched to the closest food in the built-in table, " +
                    "${entry.name}. Check the portion and numbers."
            Match.Kind.GENERIC ->
                "Recognised as “$label” ($percent% sure), but it isn't in the built-in table, so this uses a typical " +
                    "plate of food (about ${entry.servingG} g). Please check the numbers."
        }

        val alternatives = candidates
            .filter { it !== primary && it.match.kind != Match.Kind.GENERIC }
            .filter { !it.prediction.label.equals(primary.prediction.label, ignoreCase = true) }
            .sortedByDescending { it.rank }
            .distinctBy { it.prediction.label.lowercase() }
            .take(3)
            .map { Alternative(it.prediction.label.replaceFirstChar { c -> c.uppercase() }, it.match.entry) }

        val cues = named.take(3).map { "“${it.label}” ${(it.score * 100).roundToInt()}%" }

        return Recognition(
            engine = Engine.CLASSIFIER,
            analysis = dish.toAnalysis(confidence, notes, cues),
            dish = dish,
            alternatives = alternatives,
        )
    }
}
