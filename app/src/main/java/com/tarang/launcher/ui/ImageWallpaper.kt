package com.tarang.launcher.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A user-chosen photo as the launcher background. The picked image is copied into app storage
 * (see [copyImageToInternal]) so it survives reboots and the source being removed; here we just
 * decode that local file (down-sampled) and crop-fill it, with a soft scrim so the clock, status
 * pill and tiles stay legible over bright photos. [blurred] reuses the same blur toggle as the
 * gradient wallpaper.
 */
@Composable
fun ImageWallpaper(path: String, blurred: Boolean, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { decodeSampled(path, 1920, 1080) }
    }
    Box(modifier = modifier) {
        val img = image
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().let { if (blurred) it.blurCompat(32.dp) else it },
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
        // Darken top (clock/status) and bottom (dock) a touch more than the middle for legibility.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.45f),
                    0.45f to Color.Black.copy(alpha = 0.18f),
                    1.0f to Color.Black.copy(alpha = 0.55f),
                ),
            ),
        )
    }
}

/** A small thumbnail of the chosen photo for the settings swatch (null if none / unreadable). */
@Composable
fun rememberWallpaperThumb(path: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path != null) withContext(Dispatchers.IO) { decodeSampled(path, 240, 150) } else null
    }
    return image
}

/**
 * Copies the picked image into `filesDir/wallpaper/` and returns the new absolute path (null on
 * failure). We copy rather than persist the content URI so the wallpaper keeps working after a
 * reboot or if the original is deleted. Old copies are cleared first to avoid piling up.
 */
fun copyImageToInternal(context: Context, uri: Uri): String? = runCatching {
    val dir = File(context.filesDir, "wallpaper").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val out = File(dir, "wp_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { input.copyTo(it) }
    } ?: return null
    out.absolutePath
}.getOrNull()

private fun decodeSampled(path: String, reqW: Int, reqH: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply { inSampleSize = calcInSampleSize(bounds, reqW, reqH) }
    BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
}.getOrNull()

private fun calcInSampleSize(options: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
    var sample = 1
    var halfH = options.outHeight / 2
    var halfW = options.outWidth / 2
    while (halfH / sample >= reqH && halfW / sample >= reqW) sample *= 2
    return sample
}
