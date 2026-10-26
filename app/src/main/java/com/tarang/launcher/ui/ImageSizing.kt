package com.tarang.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The display size in landscape pixels (the launcher is locked to landscape). A full-screen image
 * never needs more pixels than this: a 1280×720 TV gets 1280×720, a 4K panel gets its own size.
 */
fun screenSizePx(context: Context): IntSize {
    val dm = context.resources.displayMetrics
    return IntSize(max(dm.widthPixels, dm.heightPixels), minOf(dm.widthPixels, dm.heightPixels))
}

/**
 * Decodes an image so it just covers [reqW]×[reqH] (the [androidx.compose.ui.layout.ContentScale.Crop]
 * fill). [decode] is called twice: once with `inJustDecodeBounds` for the size, then with an
 * `inSampleSize` that keeps both sides at or above the target. Power-of-two sampling alone can leave
 * a photo far larger than the screen (6016×3900 samples to 3008×1950, about 23 MB, for a 720p TV),
 * so the result is then scaled down to the exact cover size.
 */
fun decodeToCover(reqW: Int, reqH: Int, decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0 || reqW <= 0 || reqH <= 0) return null
    var sample = 1
    while (w / (sample * 2) >= reqW && h / (sample * 2) >= reqH) sample *= 2
    val sampled = decode(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val cover = max(reqW.toFloat() / sampled.width, reqH.toFloat() / sampled.height)
    if (cover >= 1f) return sampled
    val scaled = Bitmap.createScaledBitmap(
        sampled,
        (sampled.width * cover).roundToInt().coerceAtLeast(1),
        (sampled.height * cover).roundToInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== sampled) sampled.recycle()
    return scaled
}
