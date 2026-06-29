package com.tarang.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.R
import com.tarang.launcher.di.AppContainer
import com.tarang.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Top-level launcher UI: an animated wallpaper behind a clean app grid, with a top bar holding the
 * clock and settings (tune) button. No content rows. Tapping a tile launches the app directly.
 */
@Composable
fun LauncherScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val viewModel: LauncherViewModel = viewModel(
        factory = LauncherViewModel.provideFactory(
            container.appRepository,
            container.favoritesStore,
            container.settingsStore,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val tuneFocus = remember { FocusRequester() }

    // Image wallpaper: a built-in browser (no external gallery app needed) returns a Uri, which we
    // copy into app storage and set as the wallpaper.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    fun applyPickedImage(uri: Uri) {
        showPicker = false
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyImageToInternal(context, uri) }
            if (path != null) viewModel.setImageWallpaper(path)
        }
    }
    val pickImage: () -> Unit = { showPicker = true }
    var showTvProbe by remember { mutableStateOf(false) }

    val focusedApp = remember(uiState.focusedPackage, uiState.allApps) {
        uiState.allApps.firstOrNull { it.packageName == uiState.focusedPackage }
    }
    val ambient: Color? by produceState<Color?>(
        initialValue = null,
        key1 = focusedApp?.packageName,
        key2 = settings.animated,
    ) {
        value = if (settings.animated) focusedApp?.let { container.iconLoader.accentColor(it) } else null
    }
    val preset = WallpaperPresets.getOrElse(settings.wallpaperId) { WallpaperPresets.first() }
    val imagePath = settings.wallpaperImagePath
    val showImage = settings.useImageWallpaper && imagePath != null && remember(imagePath) { File(imagePath).exists() }

    Box(modifier = modifier.fillMaxSize()) {
        if (showImage && imagePath != null) {
            ImageWallpaper(
                path = imagePath,
                blurred = settings.blurred,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AnimatedWallpaper(
                preset = preset,
                animated = settings.animated,
                blurred = settings.blurred,
                ambient = ambient,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The settings page takes over the whole screen (not a modal), so D-pad focus can't reach
        // the launcher behind it; the launcher isn't composed while settings is open.
        if (showSettings) {
            SettingsScreen(
                settings = settings,
                onWallpaper = viewModel::setWallpaper,
                onAnimated = viewModel::setAnimated,
                onBlurred = viewModel::setBlurred,
                onColumns = viewModel::setColumns,
                onPickImage = pickImage,
                onUseImage = { viewModel.setUseImageWallpaper(true) },
                onScanTvContent = { showTvProbe = true },
                onClose = { showSettings = false },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(onOpenSettings = { showSettings = true }, tuneFocus = tuneFocus)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isLoading -> Centered { Text("Loading apps…", color = Color.White, fontSize = 20.sp) }
                        uiState.allApps.isEmpty() -> Centered { Text("No apps found", color = Color.White, fontSize = 20.sp) }
                        else -> LauncherContent(
                            dockApps = uiState.dockApps,
                            gridApps = uiState.gridApps,
                            iconLoader = container.iconLoader,
                            onAppFocused = viewModel::onAppFocused,
                            onAppClicked = { viewModel.launchApp(it) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onReorder = viewModel::setFavoritesOrder,
                            columns = settings.columns,
                            topFocusRequester = tuneFocus,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        if (showPicker) {
            ImagePickerDialog(onPick = { applyPickedImage(it) }, onDismiss = { showPicker = false })
        }

        if (showTvProbe) {
            TvProbeDialog(onDismiss = { showTvProbe = false })
        }
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit, tuneFocus: FocusRequester) {
    val context = LocalContext.current
    val net = rememberNetStatus()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, top = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clock()
        // Status pill: Wi-Fi indicator + Android settings + launcher (tune) settings.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(percent = 50))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(onClick = { openWifiSettings(context) }, contentDescription = "Wi-Fi settings") {
                WifiIndicator(status = net, modifier = Modifier.size(22.dp))
            }
            PillButton(
                onClick = onOpenSettings,
                contentDescription = "Launcher settings",
                modifier = Modifier.focusRequester(tuneFocus),
            ) {
                Image(painterResource(R.drawable.ic_tune), contentDescription = null, modifier = Modifier.size(22.dp))
            }
            PillButton(onClick = { openAndroidSettings(context) }, contentDescription = "Android settings") {
                Image(painterResource(R.drawable.ic_settings), contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PillButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.25f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

private fun openWifiSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openAndroidSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
