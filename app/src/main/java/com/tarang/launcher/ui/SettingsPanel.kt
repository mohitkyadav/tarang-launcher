package com.tarang.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.data.LauncherSettings
import com.tarang.launcher.data.MAX_COLUMNS
import com.tarang.launcher.data.MIN_COLUMNS

/**
 * The in-app settings overlay: wallpaper (gradient presets or a chosen photo), tiles-per-row,
 * motion (animated/static) and background (blurred/sharp). Rendered in a [Dialog] so it's a true
 * modal — D-pad focus stays inside it (no leaking to the grid behind) and Back closes it.
 */
@Composable
fun SettingsPanel(
    settings: LauncherSettings,
    onWallpaper: (Int) -> Unit,
    onAnimated: (Boolean) -> Unit,
    onBlurred: (Boolean) -> Unit,
    onColumns: (Int) -> Unit,
    onPickImage: () -> Unit,
    onUseImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val thumb = rememberWallpaperThumb(settings.wallpaperImagePath)
    val imageActive = settings.useImageWallpaper

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF141417))
                .padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Settings", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)

            SectionLabel("Wallpaper")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WallpaperPresets.forEachIndexed { i, preset ->
                    PresetSwatch(
                        preset = preset,
                        selected = !imageActive && i == settings.wallpaperId,
                        onClick = { onWallpaper(i) },
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
                PhotoSwatch(
                    thumb = thumb,
                    selected = imageActive,
                    onClick = {
                        when {
                            settings.wallpaperImagePath == null -> onPickImage() // none yet: pick
                            !imageActive -> onUseImage() // have one but inactive: just re-activate
                            else -> onPickImage() // already active: replace
                        }
                    },
                )
            }

            SectionLabel("Tiles per row")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (n in MIN_COLUMNS..MAX_COLUMNS) {
                    ToggleChip("$n", n == settings.columns) { onColumns(n) }
                }
            }

            SectionLabel("Motion")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ToggleChip("Animated", settings.animated) { onAnimated(true) }
                ToggleChip("Static", !settings.animated) { onAnimated(false) }
            }

            SectionLabel("Background")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ToggleChip("Blurred", settings.blurred) { onBlurred(true) }
                ToggleChip("Sharp", !settings.blurred) { onBlurred(false) }
            }

            Text("Press Back to close", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        }
    }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PresetSwatch(
    preset: WallpaperPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 72.dp, height = 44.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = preset.base, focusedContainerColor = preset.base),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(preset.blobA, preset.blobB, preset.blobC))),
        ) {
            if (selected) SelectedRing()
        }
    }
}

/** The "use a photo" swatch: shows the chosen image's thumbnail, or a placeholder when none is set. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhotoSwatch(
    thumb: androidx.compose.ui.graphics.ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 72.dp, height = 44.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A2A2E),
            focusedContainerColor = Color(0xFF3A3A40),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = "Photo wallpaper",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    "＋ Photo",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (selected) SelectedRing()
        }
    }
}

@Composable
private fun SelectedRing() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(3.dp, Color.White, RoundedCornerShape(14.dp)),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(percent = 50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) Color.White else Color(0xFF2A2A2E),
            focusedContainerColor = if (active) Color.White else Color(0xFF3A3A40),
        ),
    ) {
        Text(
            text = label,
            color = if (active) Color.Black else Color.White,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
