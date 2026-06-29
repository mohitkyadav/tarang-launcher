package com.tarang.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.data.LauncherSettings
import com.tarang.launcher.data.MAX_COLUMNS
import com.tarang.launcher.data.MIN_COLUMNS

private enum class SettingsSection(val title: String) {
    APPEARANCE("Appearance"),
    DIAGNOSTICS("Diagnostics"),
}

/**
 * A full-screen, tvOS-style settings page: a section list on the left, the selected section's
 * controls on the right. Focusing a left item selects it (the detail follows focus); press RIGHT to
 * step into the controls, LEFT to come back, and Back to leave. Rendered in place of the launcher
 * (not as a modal), so D-pad focus can't leak to the grid behind it.
 */
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onWallpaper: (Int) -> Unit,
    onAnimated: (Boolean) -> Unit,
    onBlurred: (Boolean) -> Unit,
    onColumns: (Int) -> Unit,
    onPickImage: () -> Unit,
    onUseImage: () -> Unit,
    onScanTvContent: () -> Unit,
    onClose: () -> Unit,
) {
    var section by remember { mutableStateOf(SettingsSection.APPEARANCE) }
    val firstSection = remember { FocusRequester() }

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0E).copy(alpha = 0.97f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 56.dp, top = 56.dp, bottom = 56.dp),
        ) {
            // Left: section list.
            Column(modifier = Modifier.width(280.dp).fillMaxHeight()) {
                Text("Settings", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(28.dp))
                SettingsSection.entries.forEachIndexed { i, s ->
                    SectionNavRow(
                        title = s.title,
                        active = section == s,
                        onFocused = { section = s },
                        modifier = if (i == 0) Modifier.focusRequester(firstSection) else Modifier,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.width(40.dp))

            // Right: detail pane for the selected section.
            Box(modifier = Modifier.fillMaxSize()) {
                when (section) {
                    SettingsSection.APPEARANCE -> AppearancePane(
                        settings = settings,
                        onWallpaper = onWallpaper,
                        onAnimated = onAnimated,
                        onBlurred = onBlurred,
                        onColumns = onColumns,
                        onPickImage = onPickImage,
                        onUseImage = onUseImage,
                    )

                    SettingsSection.DIAGNOSTICS -> DiagnosticsPane(onScanTvContent = onScanTvContent)
                }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { firstSection.requestFocus() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionNavRow(
    title: String,
    active: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = {},
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) Color.White.copy(alpha = 0.10f) else Color.Transparent,
            focusedContainerColor = Color.White,
        ),
    ) {
        Text(
            text = title,
            color = if (focused) Color.Black else Color.White.copy(alpha = if (active) 1f else 0.7f),
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun AppearancePane(
    settings: LauncherSettings,
    onWallpaper: (Int) -> Unit,
    onAnimated: (Boolean) -> Unit,
    onBlurred: (Boolean) -> Unit,
    onColumns: (Int) -> Unit,
    onPickImage: () -> Unit,
    onUseImage: () -> Unit,
) {
    val thumb = rememberWallpaperThumb(settings.wallpaperImagePath)
    val imageActive = settings.useImageWallpaper

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PaneTitle("Appearance")

        SectionLabel("Wallpaper")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WallpaperPresets.forEachIndexed { i, preset ->
                PresetSwatch(
                    preset = preset,
                    selected = !imageActive && i == settings.wallpaperId,
                    onClick = { onWallpaper(i) },
                )
            }
            PhotoSwatch(
                thumb = thumb,
                selected = imageActive,
                onClick = {
                    when {
                        settings.wallpaperImagePath == null -> onPickImage()
                        !imageActive -> onUseImage()
                        else -> onPickImage()
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
    }
}

@Composable
private fun DiagnosticsPane(onScanTvContent: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PaneTitle("Diagnostics")

        SectionLabel("TV content")
        Text(
            "Check whether installed apps publish home-screen rows (channels & preview programs) " +
                "this launcher can read — the basis for a content carousel.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        ToggleChip("Scan TV content", active = false) { onScanTvContent() }
    }
}

@Composable
private fun PaneTitle(text: String) {
    Text(text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhotoSwatch(
    thumb: ImageBitmap?,
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
                Text("＋ Photo", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, softWrap = false)
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
