package dev.pawelduraj.memstash.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.gif.AnimatedImageDecoder
import coil3.request.ImageRequest
import dev.pawelduraj.memstash.data.Meme

/**
 * The meme's image request. The file's modification time is part of the cache key, so a meme edited in
 * another app isn't shown from the cache. Animated memes are decoded separately from the grid's still images.
 */
@Composable
fun rememberMemeImage(uri: Uri, meme: Meme, animated: Boolean = false): ImageRequest {
    val context = LocalContext.current
    return remember(context, uri, meme.imageKey, meme.modified, animated) {
        ImageRequest.Builder(context)
            .data(uri)
            .memoryCacheKey(meme.imageKey)
            .memoryCacheKeyExtra("modified", meme.modified.toString())
            .apply {
                if (animated) {
                    decoderFactory(AnimatedImageDecoder.Factory())
                    memoryCacheKeyExtra("animated", "true")
                }
            }
            .build()
    }
}
