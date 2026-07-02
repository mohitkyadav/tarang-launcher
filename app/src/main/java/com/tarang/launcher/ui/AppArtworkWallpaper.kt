package com.tarang.launcher.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.tarang.launcher.data.TvArtwork
import kotlinx.coroutines.delay

private const val SLIDE_MS = 8000L // how long each poster shows
private const val FADE_MS = 1200 // cross-fade between posters

/**
 * Plays an app's published poster artwork as a slow, cross-fading slideshow with a gentle Ken-Burns
 * drift — shown as the wallpaper while an opted-in favorite is hovered. Falls back to a dark
 * gradient if the app has no readable posters (also see [ArtworkLoader] logs).
 */
@Composable
fun AppArtworkWallpaper(
    packageName: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val context = LocalContext.current
    // Shuffle so a re-hover doesn't always open on the same poster.
    val uris by produceState<List<String>>(initialValue = emptyList(), key1 = packageName) {
        value = TvArtwork.posterUris(context, packageName).shuffled()
    }

    if (uris.isEmpty()) {
        val fallback = if (isDark) listOf(Color(0xFF0C0C10), Color(0xFF17171D)) else listOf(Color(0xFFECECF1), Color(0xFFDDDDE4))
        Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(fallback)))
        return
    }

    var index by remember(packageName) { mutableIntStateOf(0) }
    // Reduce motion: hold a single poster (no auto-advance, no Ken-Burns drift).
    if (!reduceMotion) {
        LaunchedEffectSlideshow(size = uris.size) { index = (index + 1) % uris.size }
    }

    // Keep the previous bitmap on screen until the next one finishes loading (no black flashes).
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = packageName, key2 = index) {
        value = ArtworkLoader.load(context, uris[index % uris.size]) ?: value
    }

    val kb = rememberInfiniteTransition(label = "kenburns")
    val drift by kb.animateFloat(
        initialValue = 1f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "kbScale",
    )
    val scale = if (reduceMotion) 1f else drift

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = image, animationSpec = tween(FADE_MS), label = "poster") { img ->
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                )
            }
        }
        // No full-image scrim — the poster shows at full fidelity; the clock/pill have their own
        // containers and the dock is frosted, so text stays legible without washing the artwork.
    }
}

@Composable
private fun LaunchedEffectSlideshow(size: Int, onAdvance: () -> Unit) {
    if (size <= 1) return
    // Only advance (and decode the next poster) while the launcher is on-screen.
    RunWhileStarted(size) {
        while (true) {
            delay(SLIDE_MS)
            onAdvance()
        }
    }
}
