package com.tarang.launcher.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A wallpaper look: a near-black base plus three soft color blobs. */
data class WallpaperPreset(
    val name: String,
    val base: Color,
    val blobA: Color,
    val blobB: Color,
    val blobC: Color,
)

val WallpaperPresets = listOf(
    WallpaperPreset("Aurora", Color(0xFF06070B), Color(0xFF4B3DAA), Color(0xFF1E7E8C), Color(0xFF6A3DAA)),
    WallpaperPreset("Sunset", Color(0xFF0B0608), Color(0xFF7A2F6A), Color(0xFFB23A4A), Color(0xFFD9763A)),
    WallpaperPreset("Ocean", Color(0xFF05080C), Color(0xFF1E4F8C), Color(0xFF1E8C8C), Color(0xFF2A6ED9)),
    WallpaperPreset("Ember", Color(0xFF0A0605), Color(0xFF8C2A1E), Color(0xFFB2541E), Color(0xFFD99A2A)),
    WallpaperPreset("Mint", Color(0xFF050B08), Color(0xFF1E8C5A), Color(0xFF1E8C8C), Color(0xFF3AD9A0)),
)

/**
 * The launcher background: a [preset]'s near-black base with large, soft, drifting color blobs.
 *
 * Radial gradients are inherently soft (the "blur" look) and cheap to fill, so [blurred] (an extra
 * RenderEffect blur, API-31-gated) is opt-in. When [animated] is false the drift coroutine is
 * cancelled, so the wallpaper is fully static (no per-frame redraw — plan §6). When animated, one
 * blob eases toward [ambient] (the focused app's color).
 */
@Composable
fun AnimatedWallpaper(
    preset: WallpaperPreset,
    animated: Boolean,
    blurred: Boolean,
    ambient: Color?,
    modifier: Modifier = Modifier,
) {
    val drift = remember { Animatable(0f) }
    LaunchedEffect(animated) {
        if (animated) {
            drift.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Reverse),
            )
        } else {
            drift.snapTo(0.5f)
        }
    }
    val accent by animateColorAsState(targetValue = ambient ?: preset.blobC, animationSpec = tween(1400), label = "accent")

    val canvasModifier = if (blurred) modifier.blurCompat(48.dp) else modifier
    Canvas(modifier = canvasModifier) {
        val d = drift.value
        drawRect(preset.base)
        val w = size.width
        val h = size.height
        val r = size.maxDimension
        blob(Offset(w * (0.22f + 0.12f * d), h * (0.18f + 0.12f * d)), r * 0.55f, preset.blobA.copy(alpha = 0.40f))
        blob(Offset(w * (0.82f - 0.14f * d), h * (0.30f + 0.10f * d)), r * 0.50f, preset.blobB.copy(alpha = 0.34f))
        blob(Offset(w * (0.55f + 0.12f * d), h * (0.92f - 0.12f * d)), r * 0.60f, accent.copy(alpha = 0.30f))
    }
}

private fun DrawScope.blob(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

/** [Modifier.blur] is a no-op below API 31; gate it so we don't pretend to blur on older TVs. */
internal fun Modifier.blurCompat(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this
