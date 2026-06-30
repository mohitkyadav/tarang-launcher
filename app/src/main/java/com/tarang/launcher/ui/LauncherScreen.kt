package com.tarang.launcher.ui

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.R
import com.tarang.launcher.di.AppContainer
import com.tarang.launcher.home.HomeSetup
import com.tarang.launcher.viewmodel.LauncherViewModel
import kotlin.math.roundToInt
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

    val focusedPkg by viewModel.focusedPackage.collectAsStateWithLifecycle()
    val focusedApp = remember(focusedPkg, uiState.allApps) {
        uiState.allApps.firstOrNull { it.packageName == focusedPkg }
    }
    // Filter the hidden apps once (not on every recomposition) so the grid list stays stable.
    val visibleGrid = remember(uiState.gridApps, settings.hiddenApps) {
        uiState.gridApps.filterNot { it.packageName in settings.hiddenApps }
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

    // While hovering an opted-in favorite, its app artwork plays as the wallpaper; clears (back to the
    // selected wallpaper) when focus leaves the dock. Not while the settings page is up.
    var favoriteHover by remember { mutableStateOf<String?>(null) }
    val artworkApp = if (!showSettings) {
        favoriteHover?.takeIf { settings.useAppArtwork && it in settings.artworkApps }
    } else {
        null
    }

    // Record the wallpaper into a layer so the dock can re-draw it blurred as a frosted backdrop.
    val backdrop = rememberGraphicsLayer()
    val isDark = rememberIsDark(settings.theme)
    val colors = if (isDark) DarkLauncherColors else LightLauncherColors

    // "Choose Home app" is only offered when the device actually exposes a Home-app chooser (often
    // absent on Google TV, where the redirect accessibility service is the real mechanism).
    val chooseHomeApp: (() -> Unit)? = remember(context) {
        if (HomeSetup.canOpenHomeSettings(context)) {
            { HomeSetup.openHomeSettings(context) }
        } else {
            null
        }
    }

    // Idle screensaver: bump [interaction] on every key to restart the timer; when it elapses (only
    // while resumed, so it won't pop up behind another app) show the saver. The first key wakes it.
    val lifecycleOwner = LocalLifecycleOwner.current
    var interaction by remember { mutableIntStateOf(0) }
    var screensaverOn by remember { mutableStateOf(false) }
    var wakingUp by remember { mutableStateOf(false) }
    val idleMs = settings.screensaverTimeoutSec * 1000L
    LaunchedEffect(interaction, idleMs, lifecycleOwner) {
        if (idleMs <= 0L) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            kotlinx.coroutines.delay(idleMs)
            screensaverOn = true
        }
    }

    // App launch / return choreography. Launching hands the system a scale-up rooted at the tapped
    // tile (so the app window grows out of it); coming back home eases the grid in from that same
    // spot. Both are skipped when Reduce motion is on.
    val view = LocalView.current
    val enter = remember { Animatable(1f) } // 1 = home settled; 0 = zoomed-in "inside an app"
    var launchOrigin by remember { mutableStateOf(Offset(0.5f, 0.5f)) } // fractional transform origin
    var awaitingReturn by remember { mutableStateOf(false) }
    var returnTick by remember { mutableIntStateOf(0) }
    var launchTick by remember { mutableIntStateOf(0) }
    // True while a launch/return zoom is animating — used to freeze the (GPU-heavy) frosted glass and
    // the per-frame wallpaper capture so the animation itself stays smooth on weak TV hardware.
    val transitioning by remember { derivedStateOf { enter.value < 0.999f } }

    fun launchApp(packageName: String, source: Rect) {
        val animate = !settings.reduceMotion
        val options = if (animate && source.width > 0f && source.height > 0f) {
            if (view.width > 0 && view.height > 0) {
                launchOrigin = Offset(
                    (source.center.x / view.width).coerceIn(0f, 1f),
                    (source.center.y / view.height).coerceIn(0f, 1f),
                )
            }
            ActivityOptions.makeScaleUpAnimation(
                view,
                source.left.roundToInt(),
                source.top.roundToInt(),
                source.width.roundToInt(),
                source.height.roundToInt(),
            ).toBundle()
        } else {
            null
        }
        val launched = viewModel.launchApp(packageName, options)
        if (launched && animate) {
            awaitingReturn = true
            launchTick++
        }
    }

    // Coming back from a launched app: ease the grid in from the tile we left through.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingReturn) {
                awaitingReturn = false
                returnTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(launchTick) {
        if (launchTick > 0) {
            // Plays during the device's app-open latency so the click gets immediate feedback: the
            // home zooms toward the tile and fades as the app takes over. Kept short to match the
            // system's (fast) tile scale-up so the two read as one motion.
            enter.animateTo(0f, tween(durationMillis = 240, easing = FastOutLinearInEasing))
        }
    }
    LaunchedEffect(returnTick) {
        if (returnTick > 0) {
            // Return: start zoomed-in and scale back DOWN to rest while fading in. A spring starts
            // fast and settles, which reads snappier than a fixed-duration curve.
            enter.snapTo(0f)
            enter.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    CompositionLocalProvider(LocalLauncherColors provides colors) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                when {
                    // Screensaver is up: the first key wakes it and is swallowed (whole press).
                    screensaverOn -> {
                        if (e.type == KeyEventType.KeyDown) {
                            screensaverOn = false
                            wakingUp = true
                            interaction++
                        }
                        true
                    }
                    // Swallow the key-up of the press that dismissed the saver (no stray click/launch).
                    wakingUp -> {
                        if (e.type == KeyEventType.KeyUp) wakingUp = false
                        true
                    }
                    else -> {
                        if (e.type == KeyEventType.KeyDown) interaction++ // any key resets the idle timer
                        false
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    // While a launch/return zoom runs, no frosted surface is sampling the backdrop, so
                    // skip the per-frame layer capture and draw the wallpaper straight to the screen.
                    if (transitioning) {
                        drawContent()
                    } else {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    }
                },
        ) {
            Crossfade(targetState = artworkApp, animationSpec = tween(700), label = "wallpaper") { app ->
                when {
                    app != null -> AppArtworkWallpaper(
                        packageName = app,
                        blurred = settings.blurred,
                        isDark = isDark,
                        reduceMotion = settings.reduceMotion,
                        modifier = Modifier.fillMaxSize(),
                    )

                    showImage && imagePath != null -> ImageWallpaper(
                        path = imagePath,
                        blurred = settings.blurred,
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> AnimatedWallpaper(
                        preset = preset,
                        animated = settings.animated && !settings.reduceMotion,
                        blurred = settings.blurred,
                        ambient = ambient,
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
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
                favoriteApps = uiState.dockApps,
                onUseAppArtwork = viewModel::setUseAppArtwork,
                onToggleArtworkApp = viewModel::setArtworkApp,
                theme = settings.theme,
                onTheme = viewModel::setTheme,
                reduceMotion = settings.reduceMotion,
                onReduceMotion = viewModel::setReduceMotion,
                hiddenApps = uiState.allApps.filter { it.packageName in settings.hiddenApps },
                onUnhideApp = { viewModel.setAppHidden(it, false) },
                screensaverTimeoutSec = settings.screensaverTimeoutSec,
                onScreensaverTimeout = viewModel::setScreensaverTimeout,
                screensaverSource = settings.screensaverSource,
                onScreensaverSource = viewModel::setScreensaverSource,
                onOpenAccessibilitySettings = { HomeSetup.openAccessibilitySettings(context) },
                onChooseHomeApp = chooseHomeApp,
                onClose = { showSettings = false },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // p: 1 = settled, <1 = mid launch/return. Home zooms toward the launched tile
                        // on the way out and scales back DOWN to rest (with a fade) on return.
                        val p = enter.value
                        alpha = p
                        val s = 1f + (1f - p) * 0.16f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(launchOrigin.x, launchOrigin.y)
                    },
            ) {
                TopBar(
                    onOpenSettings = { showSettings = true },
                    tuneFocus = tuneFocus,
                    backdrop = backdrop,
                    glassLive = !transitioning,
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isLoading -> Centered { Text("Loading apps…", color = colors.text, fontSize = 20.sp) }
                        uiState.allApps.isEmpty() -> Centered { Text("No apps found", color = colors.text, fontSize = 20.sp) }
                        else -> LauncherContent(
                            dockApps = uiState.dockApps,
                            gridApps = visibleGrid,
                            iconLoader = container.iconLoader,
                            onAppFocused = viewModel::onAppFocused,
                            onAppClicked = { pkg, sourceBounds -> launchApp(pkg, sourceBounds) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onReorder = viewModel::setFavoritesOrder,
                            columns = settings.columns,
                            backdrop = backdrop,
                            topFocusRequester = tuneFocus,
                            onFavoriteHover = { favoriteHover = it },
                            reduceMotion = settings.reduceMotion,
                            onHideApp = { viewModel.setAppHidden(it, true) },
                            onAppInfo = { viewModel.openAppInfo(it) },
                            onUninstall = { viewModel.uninstallApp(it) },
                            glassLive = !transitioning,
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

        // Drawn last so it covers everything (grid, settings, dialogs); key handling above wakes it.
        if (screensaverOn) {
            Screensaver(
                posterPackages = uiState.dockApps.map { it.packageName },
                watchNext = uiState.watchNext,
                source = settings.screensaverSource,
                reduceMotion = settings.reduceMotion,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    }
}

@Composable
private fun TopBar(
    onOpenSettings: () -> Unit,
    tuneFocus: FocusRequester,
    backdrop: GraphicsLayer,
    glassLive: Boolean,
) {
    val context = LocalContext.current
    val net = rememberNetStatus()
    val colors = LocalLauncherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, top = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Each free-floating element is its own frosted-glass container, so text stays legible over
        // any wallpaper without scrimming the whole image.
        Clock(
            modifier = Modifier
                .frostedGlass(backdrop, RoundedCornerShape(18.dp), tint = colors.textBackdrop, live = glassLive)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // Status pill: Wi-Fi indicator + Android settings + launcher (tune) settings.
        Row(
            modifier = Modifier
                .frostedGlass(backdrop, RoundedCornerShape(percent = 50), tint = colors.textBackdrop, live = glassLive)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(onClick = { openWifiSettings(context) }, contentDescription = "Wi-Fi settings") {
                WifiIndicator(status = net, tint = colors.text, modifier = Modifier.size(22.dp))
            }
            PillButton(
                onClick = onOpenSettings,
                contentDescription = "Launcher settings",
                modifier = Modifier.focusRequester(tuneFocus),
            ) {
                Image(
                    painterResource(R.drawable.ic_tune),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(colors.text),
                )
            }
            PillButton(onClick = { openAndroidSettings(context) }, contentDescription = "Android settings") {
                Image(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(colors.text),
                )
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
    val colors = LocalLauncherColors.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = colors.text.copy(alpha = 0.22f),
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
