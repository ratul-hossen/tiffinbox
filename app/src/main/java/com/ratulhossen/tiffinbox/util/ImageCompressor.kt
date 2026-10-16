package com.ratulhossen.tiffinbox.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Firebase Storage needs a paid (Blaze) plan, which this project's owner
 * isn't using, so photos (payment proofs, kitchen and food photos) are
 * compressed down and stored as a base64 string directly on the Firestore
 * document instead. Firestore caps a document at 1 MiB, so this keeps the
 * raw JPEG bytes (before base64's ~33% size increase) under [maxRawBytes]
 * by shrinking resolution first and then stepping quality down.
 *
 * Phone-camera photos are usually stored sideways with an EXIF "rotate"
 * flag - BitmapFactory ignores it, which made uploads appear rotated. The
 * flag is applied here before anything else. [cropAspect] (width / height)
 * centre-crops to a fixed frame, so every food / kitchen photo fills its
 * card the same way instead of whatever shape the original happened to be.
 */
object ImageCompressor {

    /** Food photos: 4:3, ~800px wide, small enough to load quickly in lists. */
    const val MEAL_ASPECT = 4f / 3f
    /** Kitchen cover photos: 16:9 banner. */
    const val KITCHEN_ASPECT = 16f / 9f

    suspend fun compressToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1024,
        maxRawBytes: Int = 500_000,
        cropAspect: Float? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Decode at a reduced size first (inSampleSize) so a 12MP camera
            // photo doesn't need ~48MB of memory just to be shrunk again.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null

            val upright = applyExifRotation(context, uri, decoded)
            val cropped = if (cropAspect != null) centerCrop(upright, cropAspect) else upright

            val scale = minOf(1f, maxDimension.toFloat() / maxOf(cropped.width, cropped.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    cropped,
                    (cropped.width * scale).toInt().coerceAtLeast(1),
                    (cropped.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                cropped
            }

            var quality = 82
            var bytes: ByteArray
            do {
                val output = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
                bytes = output.toByteArray()
                quality -= 10
            } while (bytes.size > maxRawBytes && quality > 20)

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Food photo preset - 4:3, 800px, ~160KB. */
    suspend fun compressMealPhoto(context: Context, uri: Uri): String? =
        compressToBase64(context, uri, maxDimension = 800, maxRawBytes = 160_000, cropAspect = MEAL_ASPECT)

    /** Kitchen cover preset - 16:9, 1280px, ~240KB. */
    suspend fun compressKitchenPhoto(context: Context, uri: Uri): String? =
        compressToBase64(context, uri, maxDimension = 1280, maxRawBytes = 240_000, cropAspect = KITCHEN_ASPECT)

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun centerCrop(bitmap: Bitmap, aspect: Float): Bitmap {
        val current = bitmap.width.toFloat() / bitmap.height
        return if (current > aspect) {
            val newWidth = (bitmap.height * aspect).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(bitmap, (bitmap.width - newWidth) / 2, 0, newWidth, bitmap.height)
        } else if (current < aspect) {
            val newHeight = (bitmap.width / aspect).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(bitmap, 0, (bitmap.height - newHeight) / 2, bitmap.width, newHeight)
        } else {
            bitmap
        }
    }
}
