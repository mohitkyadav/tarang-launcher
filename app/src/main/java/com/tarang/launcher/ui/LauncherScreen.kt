package com.tarang.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedWallpaper(
            preset = preset,
            animated = settings.animated,
            blurred = settings.blurred,
            ambient = ambient,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(onOpenSettings = { showSettings = true }, focusRequester = tuneFocus)
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
                        topFocusRequester = tuneFocus,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (showSettings) {
            SettingsPanel(
                settings = settings,
                onWallpaper = viewModel::setWallpaper,
                onAnimated = viewModel::setAnimated,
                onBlurred = viewModel::setBlurred,
            )
            BackHandler { showSettings = false }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopBar(onOpenSettings: () -> Unit, focusRequester: FocusRequester) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, top = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clock()
        Surface(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .focusRequester(focusRequester),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color.White.copy(alpha = 0.22f),
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = "Settings",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
