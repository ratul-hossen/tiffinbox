package com.ratulhossen.tiffinbox.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Firebase Storage needs a paid (Blaze) plan, which this project's owner
 * isn't using, so payment-proof screenshots are compressed down and stored
 * as a base64 string directly on the order document instead. Firestore caps
 * a document at 1 MiB, so this keeps the raw JPEG bytes (before base64's
 * ~33% size increase) under maxRawBytes by shrinking resolution first and
 * then stepping quality down if it's still too big.
 */
object ImageCompressor {

    suspend fun compressToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1024,
        maxRawBytes: Int = 500_000
    ): String? = withContext(Dispatchers.IO) {
        try {
            val original = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext null

            val scale = minOf(1f, maxDimension.toFloat() / maxOf(original.width, original.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                original
            }

            var quality = 80
            var bytes: ByteArray
            do {
                val output = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
                bytes = output.toByteArray()
                quality -= 15
            } while (bytes.size > maxRawBytes && quality > 10)

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
