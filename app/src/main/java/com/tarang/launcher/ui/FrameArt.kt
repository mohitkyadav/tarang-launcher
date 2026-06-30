package com.tarang.launcher.ui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tarang.launcher.R
import com.tarang.launcher.data.FrameClockPosition
import com.tarang.launcher.data.FrameClockSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private const val FADE_MS = 1600
private const val FLOAT_PERIOD_MS = 36_000 // one full figure-8 loop — very slow on purpose

// Slight overscan so the drift never reveals an edge. Applied even when still, so the still wallpaper
// and the drifting frame share the exact same framing — entering frame mode then has no scale "jump".
private const val FRAME_BASE_SCALE = 1.06f

/**
 * Frame Art: a full-screen, chrome-free "painting" — a slow cross-fading slideshow of the photos in
 * the chosen device folder. [LauncherScreen] traps the next key to dismiss it. The single-photo and
 * "current wallpaper" sources are handled by [LauncherScreen] directly (it shows [ImageWallpaper] or
 * the normal wallpaper); the optional [FrameClock] overlay and [frameParallax] motion are shared by
 * all of them.
 */
@Composable
fun FrameSlideshow(
    folderId: String,
    intervalSec: Int,
    drift: Boolean,
    driftAmount: () -> Float,
    cycle: Boolean,
    shuffle: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Re-keyed on [shuffle] so flipping it re-orders immediately; a fresh shuffle each time the
    // slideshow (re)starts gives a different random order on every entry into Frame Art.
    val photos by produceState<List<Uri>>(initialValue = emptyList(), folderId, shuffle) {
        val list = withContext(Dispatchers.IO) { framePhotoUris(context, folderId) }
        value = if (shuffle) list.shuffled() else list
    }

    var index by remember(photos) { mutableIntStateOf(0) }
    val intervalMs = (intervalSec.coerceAtLeast(5)) * 1000L
    // Only advance when [cycle] is on (e.g. full frame mode, or an animated home wallpaper); a still
    // wallpaper holds one photo and runs no timer.
    if (cycle && photos.size > 1) {
        LaunchedEffect(photos, intervalMs) {
            while (true) {
                delay(intervalMs)
                index = (index + 1) % photos.size
            }
        }
    }

    val image by produceState<ImageBitmap?>(initialValue = null, photos, index) {
        if (photos.isEmpty()) {
            value = null
        } else {
            // Reuse the artwork loader: it decodes content:// URIs down-sampled and LRU-caches them.
            value = ArtworkLoader.load(context, photos[index % photos.size].toString(), 1920, 1080) ?: value
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = image, animationSpec = tween(FADE_MS), label = "framePhoto") { img ->
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().frameParallax(drift, driftAmount),
                )
            }
        }
    }
}

/**
 * The "living painting" motion: a very slow, smooth parallax float — the image drifts a few pixels
 * along a figure-8 path and rides on a small base zoom so the drift never reveals an edge. When
 * [enabled] is false NO animation is composed at all (the infinite transition is disposed), so a
 * still wallpaper costs nothing and doesn't keep the GPU awake — important on weak TV hardware.
 */
@Composable
fun Modifier.frameParallax(enabled: Boolean, amount: () -> Float = { 1f }): Modifier =
    if (enabled) {
        val transition = rememberInfiniteTransition(label = "frameFloat")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(FLOAT_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "frameFloatPhase",
        )
        this.graphicsLayer {
            // [amount] (0..1, read here at draw time) eases the drift in with the frame transition and,
            // crucially, back out on exit — so pressing a key glides the image back to centre instead
            // of snapping from its current drifted offset to (0,0) in a single frame.
            val a = amount()
            val amp = size.minDimension * 0.012f
            translationX = sin(phase) * amp * a
            translationY = sin(phase * 2f) * (amp * 0.55f) * a
            scaleX = FRAME_BASE_SCALE
            scaleY = FRAME_BASE_SCALE
        }
    } else {
        // Same base scale as the drifting version, so a still wallpaper and the frame share framing.
        this.graphicsLayer {
            scaleX = FRAME_BASE_SCALE
            scaleY = FRAME_BASE_SCALE
        }
    }

@OptIn(ExperimentalTextApi::class)
private fun interWeight(w: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

/** A hairline Inter for the frame clock — thinner than the UI's text for an elegant, gallery look. */
private val FrameClockFont = FontFamily(interWeight(200), interWeight(300), interWeight(400))

/**
 * A beautiful, quiet clock for Frame Art: a large hairline time over the date, lifted off the canvas
 * by a soft floating shadow (so it reads as a nearer plane than the slowly drifting art) and placed
 * per [position] at the chosen [size]. A gentle scrim keeps it legible over any picture. Always
 * 24-hour; ticks on the minute boundary (no seconds — restless isn't classy).
 */
@Composable
fun FrameClock(
    position: FrameClockPosition,
    size: FrameClockSize,
    showDate: Boolean,
    reveal: () -> Float = { 1f },
    modifier: Modifier = Modifier,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }

    var now by remember { mutableStateOf(Date(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date(System.currentTimeMillis())
            val millis = System.currentTimeMillis()
            delay(60_000L - millis % 60_000L) // tick on the next minute boundary
        }
    }

    // A bigger, softer, offset shadow lifts the clock well above the surface — the "floating" depth
    // cue. Combined with the still clock over the drifting art, it reads as a nearer plane.
    val lift = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 10f), blurRadius = 40f)
    val (timeSp, dateSp) = when (size) {
        FrameClockSize.SMALL -> 64f to 18f
        FrameClockSize.MEDIUM -> 100f to 25f
        FrameClockSize.LARGE -> 128f to 32f
    }
    val align = when (position) {
        FrameClockPosition.BOTTOM_LEFT -> Alignment.BottomStart
        FrameClockPosition.BOTTOM_CENTER -> Alignment.BottomCenter
        FrameClockPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        FrameClockPosition.CENTER -> Alignment.Center
    }
    val colAlign = when (position) {
        FrameClockPosition.BOTTOM_RIGHT -> Alignment.End
        FrameClockPosition.BOTTOM_CENTER, FrameClockPosition.CENTER -> Alignment.CenterHorizontally
        else -> Alignment.Start
    }
    val pad = when (position) {
        FrameClockPosition.BOTTOM_LEFT -> PaddingValues(start = 72.dp, bottom = 64.dp)
        FrameClockPosition.BOTTOM_RIGHT -> PaddingValues(end = 72.dp, bottom = 64.dp)
        FrameClockPosition.BOTTOM_CENTER -> PaddingValues(bottom = 64.dp)
        FrameClockPosition.CENTER -> PaddingValues(0.dp)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Legibility scrim: a bottom gradient for the bottom positions; a faint overall dim for centre.
        // It only FADES in (alpha) — never scales — so its sharp rectangular edges never slide into view.
        val scrim = Modifier.fillMaxSize().graphicsLayer { alpha = reveal() }
        if (position == FrameClockPosition.CENTER) {
            Box(modifier = scrim.background(Color.Black.copy(alpha = 0.12f)))
        } else {
            Box(
                modifier = scrim.background(
                    Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f)),
                ),
            )
        }
        // The text fades + scales slightly from its own centre (its shadow is soft, so no hard edges).
        Column(
            modifier = Modifier
                .align(align)
                .padding(pad)
                .graphicsLayer {
                    val r = reveal()
                    alpha = r
                    val s = 0.94f + 0.06f * r
                    scaleX = s
                    scaleY = s
                },
            horizontalAlignment = colAlign,
        ) {
            Text(
                text = timeFormat.format(now),
                color = Color.White,
                fontFamily = FrameClockFont,
                fontWeight = FontWeight.W200,
                fontSize = timeSp.sp,
                style = TextStyle(shadow = lift),
            )
            if (showDate) {
                Text(
                    text = dateFormat.format(now),
                    color = Color.White.copy(alpha = 0.92f),
                    fontFamily = FrameClockFont,
                    fontWeight = FontWeight.W300,
                    fontSize = dateSp.sp,
                    style = TextStyle(shadow = lift),
                )
            }
        }
    }
}

/** All image URIs in a MediaStore bucket (folder), newest first. Empty without photo permission. */
fun framePhotoUris(context: Context, bucketId: String, limit: Int = 300): List<Uri> {
    if (!hasImagePermission(context)) return emptyList()
    return runCatching {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val args = arrayOf(bucketId)
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val out = mutableListOf<Uri>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && out.size < limit) {
                out += ContentUris.withAppendedId(collection, cursor.getLong(idCol))
            }
        }
        out
    }.getOrDefault(emptyList())
}
