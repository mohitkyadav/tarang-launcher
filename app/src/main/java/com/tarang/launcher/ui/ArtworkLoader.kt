package com.tarang.launcher.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads preview-program poster artwork for the slideshow wallpaper. Posters come back from the
 * TvProvider as either http(s) URLs or content:// URIs (which scheme an app uses varies), so this
 * handles both, decodes down-sampled, caches decoded bitmaps, and logs failures (so an empty
 * slideshow on-device is diagnosable rather than silent).
 */
object ArtworkLoader {
    private const val TAG = "ArtworkLoader"

    // Bounded by BYTES, not entries: 32 full-screen posters could hold 100+ MB on a 1 GB TV. An
    // eighth of the heap, capped at 24 MB, keeps the next few slides warm (a 720p poster is 3.7 MB).
    private const val MAX_CACHE_KB = 24 * 1024
    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8 / 1024).toInt().coerceAtMost(MAX_CACHE_KB),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.asAndroidBitmap().allocationByteCount / 1024).coerceAtLeast(1)
    }

    /** Loads [uriString] to just cover [reqW]×[reqH]; 0 means the display size. */
    suspend fun load(context: Context, uriString: String, reqW: Int = 0, reqH: Int = 0): ImageBitmap? {
        cache.get(uriString)?.let { return it }
        val screen = screenSizePx(context)
        val w = if (reqW > 0) reqW else screen.width
        val h = if (reqH > 0) reqH else screen.height
        return withContext(Dispatchers.IO) {
            val bytes = runCatching { readBytes(context, uriString) }
                .onFailure { Log.w(TAG, "fetch failed [$uriString]: ${it.javaClass.simpleName} ${it.message}") }
                .getOrNull() ?: return@withContext null
            val bmp = runCatching { decodeSampled(bytes, w, h) }.getOrNull()
            if (bmp == null) Log.w(TAG, "decode failed [$uriString]") else cache.put(uriString, bmp)
            bmp
        }
    }

    private fun readBytes(context: Context, uriString: String): ByteArray {
        val uri = Uri.parse(uriString)
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                val conn = (URL(uriString).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }
                try {
                    conn.inputStream.use(InputStream::readBytes)
                } finally {
                    conn.disconnect()
                }
            }

            else -> context.contentResolver.openInputStream(uri)?.use(InputStream::readBytes)
                ?: error("null input stream")
        }
    }

    private fun decodeSampled(bytes: ByteArray, reqW: Int, reqH: Int): ImageBitmap? =
        decodeToCover(reqW, reqH) { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
            ?.asImageBitmap()
}
