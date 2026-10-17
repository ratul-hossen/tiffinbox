package com.ratulhossen.tiffinbox.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shows a chef/meal photo stored as a compressed base64 string (see
 * ImageCompressor.kt / the "Known billing constraint" section of
 * CLAUDE.md - this project deliberately avoids Firebase Storage), falling
 * back to the existing emoji placeholder when no photo has been set yet
 * (blank string, or an older doc from before this feature existed).
 *
 * Pass [initial] (first letter of the name) for kitchens/chefs: the
 * placeholder then shows a gradient background + large initial instead of
 * just an emoji, which looks much nicer on cards with no photo yet.
 */
@Composable
fun Base64Thumbnail(
    base64: String,
    emoji: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    emojiFontSize: TextUnit = 28.sp,
    /** Optional first letter of the kitchen/chef name for a prettier placeholder. */
    initial: String? = null
) {
    // Decoded off the main thread (a full photo decode in composition made
    // lists stutter) and cached, so scrolling back to a card is instant.
    val bitmap by produceState<ImageBitmap?>(initialValue = Base64ImageCache.get(base64), base64) {
        value = if (base64.isBlank()) null else Base64ImageCache.get(base64) ?: Base64ImageCache.decode(base64)
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!initial.isNullOrBlank()) {
                // Gradient background + a large letter: much nicer than an emoji
                // for kitchen cards before the chef has uploaded a photo.
                Text(
                    initial.first().uppercaseChar().toString(),
                    style = TextStyle(
                        fontSize = emojiFontSize * 1.1f,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                // Pure emoji fallback (meal photos, etc.).
                // clearAndSetSemantics{} keeps the emoji out of TalkBack — it's
                // decorative; the card's own content description is more useful.
                Text(
                    emoji,
                    style = TextStyle(fontSize = emojiFontSize),
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        }
    }
}

/** Small in-memory cache of decoded photos, keyed by the base64 string's hash + length. */
internal object Base64ImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun key(base64: String) = "${base64.length}:${base64.hashCode()}"

    fun get(base64: String): ImageBitmap? = if (base64.isBlank()) null else cache.get(key(base64))

    suspend fun decode(base64: String): ImageBitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()?.also { cache.put(key(base64), it) }
    }
}
