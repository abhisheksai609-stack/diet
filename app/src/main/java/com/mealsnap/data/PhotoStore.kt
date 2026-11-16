package com.mealsnap.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

object ImageMath {
    /** Size that fits [maxSide] on the longest edge, never upscaling. */
    fun scaledSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxSide || longest <= 0) return width to height
        val ratio = maxSide.toFloat() / longest
        return max(1, (width * ratio).roundToInt()) to max(1, (height * ratio).roundToInt())
    }

    /** Largest power-of-two subsampling that still leaves the longest edge >= [maxSide]. */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= maxSide) {
            sample *= 2
            longest /= 2
        }
        return sample
    }
}

/**
 * Handles the lifecycle of meal photos:
 * camera/gallery file in the cache -> shrunk, rotated JPEG in private storage.
 */
class PhotoStore(private val context: Context) {
    private val photoDir = File(context.filesDir, "meals").apply { mkdirs() }
    private val captureDir = File(context.cacheDir, "captures").apply { mkdirs() }

    fun newCaptureFile(): File =
        File(captureDir.apply { mkdirs() }, "capture_${System.currentTimeMillis()}.jpg")

    /** Copies a picked gallery image into the capture cache so it goes through the same pipeline. */
    suspend fun copyToCapture(uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = newCaptureFile()
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            target
        }.getOrNull()
    }

    /**
     * Rotates (per EXIF), shrinks to [maxSide] and re-encodes the capture as JPEG in private
     * storage. The source file is deleted. Returns null if the image can't be decoded.
     */
    suspend fun prepare(source: File, maxSide: Int = MAX_SIDE, quality: Int = 85): File? =
        withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = ImageMath.sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
                }
                val decoded = BitmapFactory.decodeFile(source.path, decodeOptions)
                    ?: return@withContext null

                val matrix = Matrix()
                when (
                    ExifInterface(source.path).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                }
                val (targetW, targetH) = ImageMath.scaledSize(decoded.width, decoded.height, maxSide)
                matrix.postScale(
                    targetW.toFloat() / decoded.width,
                    targetH.toFloat() / decoded.height,
                )
                val finalBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

                val out = File(photoDir.apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
                out.outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                if (finalBitmap !== decoded) decoded.recycle()
                finalBitmap.recycle()
                out
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                null
            } finally {
                source.delete()
            }
        }

    fun delete(path: String?) {
        if (!path.isNullOrBlank()) runCatching { File(path).delete() }
    }

    companion object {
        /** Anthropic recommends images of at most ~1568px on the long edge. */
        const val MAX_SIDE = 1568
    }
}
