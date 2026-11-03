package com.downshift.app.ui.garage

import android.content.Context
import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The garage's WebP assets, decoded on an IO thread at the size they are drawn (the longer side at most px), so
 * nothing decodes on the main thread. Card pictures, heroes and chips stay in a small LRU cache; the turntable keeps
 * its own frames (only the shown engine's) and decodes them with [decode].
 */
object GarageImages {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(path: String, px: Int): ImageBitmap? = cache.get(key(path, px))

    suspend fun load(context: Context, path: String, px: Int): ImageBitmap? = cached(path, px) ?: withContext(Dispatchers.IO) {
        decode(context, path, px)?.also { cache.put(key(path, px), it) }
    }

    /** Decodes without caching; call it off the main thread. Null when the asset is missing or broken. */
    fun decode(context: Context, path: String, px: Int): ImageBitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.assets, path)) { decoder, info, _ ->
            val s = px.toFloat() / maxOf(info.size.width, info.size.height)
            if (s < 1f) decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.asImageBitmap()
    }.getOrNull()

    private fun key(path: String, px: Int) = "$path@$px"
}

/** The asset at [path] decoded for [px], or null until it is ready (or when it is missing). */
@Composable
fun rememberAssetImage(path: String?, px: Int): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(path, px) { mutableStateOf(path?.let { GarageImages.cached(it, px) }) }
    LaunchedEffect(path, px) {
        if (path != null && image == null) image = GarageImages.load(context, path, px)
    }
    return image
}
