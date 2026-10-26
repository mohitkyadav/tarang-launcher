package com.tarang.launcher.ui

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A user-chosen photo as the launcher background. The picked image is copied into app storage
 * (see [copyImageToInternal]) so it survives reboots and the source being removed; here we just
 * decode that local file (down-sampled) and crop-fill it, with a soft scrim so the clock, status
 * pill and tiles stay legible over bright photos.
 */
@Composable
fun ImageWallpaper(path: String, isDark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        // Decode to the display's own size: a 720p TV holds a 3.7 MB bitmap, not a 1080p+ one.
        val screen = screenSizePx(context)
        value = withContext(Dispatchers.IO) { decodeSampled(path, screen.width, screen.height) }
    }
    Box(modifier = modifier) {
        val img = image
        if (img != null) {
            // Shown at full fidelity (no scrim) — legibility is handled by the clock/pill's own
            // containers and the frosted dock, so the photo isn't washed out.
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(if (isDark) Color.Black else Color.White))
        }
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
 * Copies the picked image into `filesDir/<subdir>/` and returns the new absolute path (null on
 * failure). We copy rather than persist the content URI so the image keeps working after a reboot or
 * if the original is deleted. [subdir] keeps independent copies apart (e.g. the wallpaper vs. the
 * single Frame Art photo).
 *
 * The copy is scaled down once, here, to just cover the screen and saved as a JPEG, so every cold
 * start decodes a screen-sized file instead of a camera original. An image this code cannot decode
 * is copied as-is, as before. Old copies are deleted only after the new one is written, so a failed
 * pick never leaves the setting pointing at a deleted file.
 */
fun copyImageToInternal(context: Context, uri: Uri, subdir: String = "wallpaper"): String? = runCatching {
    val dir = File(context.filesDir, subdir).apply { mkdirs() }
    val out = File(dir, "img_${System.currentTimeMillis()}.jpg")
    val screen = screenSizePx(context)
    val scaled = runCatching {
        decodeToCover(screen.width, screen.height) { opts ->
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }.getOrNull()
    if (scaled != null) {
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        scaled.recycle()
    } else {
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return null
    }
    dir.listFiles()?.forEach { if (it != out) it.delete() }
    out.absolutePath
}.getOrNull()

private fun decodeSampled(path: String, reqW: Int, reqH: Int): ImageBitmap? = runCatching {
    decodeToCover(reqW, reqH) { opts -> BitmapFactory.decodeFile(path, opts) }?.asImageBitmap()
}.getOrNull()
