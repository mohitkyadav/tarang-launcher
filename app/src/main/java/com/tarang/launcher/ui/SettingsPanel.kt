package com.tarang.launcher.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.data.LauncherSettings

/**
 * The in-app settings overlay: wallpaper preset, motion (animated/static) and background
 * (blurred/sharp). Close with Back (handled by the caller).
 */
@Composable
fun SettingsPanel(
    settings: LauncherSettings,
    onWallpaper: (Int) -> Unit,
    onAnimated: (Boolean) -> Unit,
    onBlurred: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }

    Box(
        modifier = modifier
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
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text("Settings", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)

            SectionLabel("Wallpaper")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WallpaperPresets.forEachIndexed { i, preset ->
                    PresetSwatch(
                        preset = preset,
                        selected = i == settings.wallpaperId,
                        onClick = { onWallpaper(i) },
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
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
        modifier = modifier.size(width = 84.dp, height = 50.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = preset.base, focusedContainerColor = preset.base),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(preset.blobA, preset.blobB, preset.blobC))),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(3.dp, Color.White, RoundedCornerShape(14.dp)),
                )
            }
        }
    }
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
