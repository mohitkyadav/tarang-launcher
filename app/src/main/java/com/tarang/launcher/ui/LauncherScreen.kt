package com.tarang.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.tarang.launcher.data.FrameSource
import com.tarang.launcher.data.LauncherSettings
import com.tarang.launcher.data.LauncherStyle
import com.tarang.launcher.data.WeatherUnit
import com.tarang.launcher.di.AppContainer
import com.tarang.launcher.home.HomeSetup
import com.tarang.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Cap the frosted-glass backdrop re-capture at ~20fps (an animated wallpaper would otherwise drive it
// at 60fps; the blurred backdrop doesn't need that, and the capture is the heaviest per-frame cost).
private const val BACKDROP_CAPTURE_INTERVAL_NS = 50_000_000L

// If a started app hasn't covered the launcher after this long, give up on the launch splash and
// come back home (it would otherwise sit key-locked on top of the launcher).
private const val LAUNCH_COVER_TIMEOUT_MS = 8_000L

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
            container.appListCache,
            container.iconLoader,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsOrNull by viewModel.settings.collectAsStateWithLifecycle()
    // Hold the (black) first frame until the real settings land — a few ms — rather than render
    // default settings (wrong wallpaper/theme) and visibly swap. Never null again after that.
    val settings = settingsOrNull ?: return
    var showSettings by remember { mutableStateOf(false) }
    val tuneFocus = remember { FocusRequester() }

    // tvOS-style navigation sounds; the setting gates them inside UiSounds.
    val sounds = container.uiSounds
    SideEffect { sounds.enabled = settings.navSounds }

    // Image wallpaper: a built-in browser (no external gallery app needed) returns a Uri, which we
    // copy into app storage and set as the wallpaper.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var pickerForFrame by remember { mutableStateOf(false) } // the photo picker is shared: wallpaper vs. frame photo
    var showFolderPicker by remember { mutableStateOf(false) }
    fun applyPickedImage(uri: Uri) {
        val forFrame = pickerForFrame
        showPicker = false
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                copyImageToInternal(context, uri, subdir = if (forFrame) "frame" else "wallpaper")
            }
            if (path != null) {
                if (forFrame) viewModel.setFrameImage(path) else viewModel.setImageWallpaper(path)
            }
        }
    }
    val pickImage: () -> Unit = { pickerForFrame = false; showPicker = true }
    val pickFramePhoto: () -> Unit = { pickerForFrame = true; showPicker = true }
    val pickFrameFolder: () -> Unit = { showFolderPicker = true }
    var showTvProbe by remember { mutableStateOf(false) }

    // TTFD beacon: once real content (not the loading placeholder) has rendered, tell the system the
    // launcher is fully drawn — logcat then prints "Fully drawn …", making cold starts measurable.
    var reportedDrawn by remember { mutableStateOf(false) }
    val contentReady = !uiState.isLoading && uiState.allApps.isNotEmpty()
    LaunchedEffect(contentReady) {
        if (contentReady && !reportedDrawn) {
            reportedDrawn = true
            runCatching { (context as? Activity)?.reportFullyDrawn() }
        }
    }

    // Filter the hidden apps once (not on every recomposition) so the grid list stays stable.
    val visibleGrid = remember(uiState.gridApps, settings.hiddenApps) {
        uiState.gridApps.filterNot { it.packageName in settings.hiddenApps }
    }
    // The Metro Start screen is one flat tile grid: favorites first, then the rest. This ordering is
    // shared with launchApp so the launched tile's index (the scatter origin) lines up with the grid.
    val metroApps = remember(uiState.dockApps, visibleGrid) { uiState.dockApps + visibleGrid }
    val preset = WallpaperPresets.getOrElse(settings.wallpaperId) { WallpaperPresets.first() }
    val imagePath = settings.wallpaperImagePath
    val showImage = settings.useImageWallpaper && imagePath != null && remember(imagePath) { File(imagePath).exists() }

    // While hovering an opted-in favorite, its app artwork plays as the wallpaper; clears (back to the
    // selected wallpaper) when focus leaves the dock. Not while the settings page is up.
    var favoriteHover by remember { mutableStateOf<String?>(null) }
    // Frame Art ("painting") mode. Declared here so the wallpaper selection below falls back to the
    // base wallpaper while it's on (no hover artwork bleeding into the frame).
    var frameOn by remember { mutableStateOf(false) }
    // Owns D-pad focus while Frame Art is showing (see the capture layer below), so the grid behind it
    // can't be navigated or clicked (which would silently launch an app).
    val frameCaptureFocus = remember { FocusRequester() }
    // Frame Art photo paging: the folder slideshow reports its photo count here and the root key
    // handler pushes Left/Right paging requests down (see the key handler below).
    val frameNav = remember { FrameNavState() }
    val artworkApp = if (!showSettings && !frameOn) {
        favoriteHover?.takeIf { settings.useAppArtwork && it in settings.artworkApps }
    } else {
        null
    }

    // Record the wallpaper into a layer so the dock can re-draw it blurred as a frosted backdrop.
    val backdrop = rememberGraphicsLayer()
    // Wall-clock of the last backdrop capture (a plain holder, not state — read/written in draw without
    // triggering recomposition), used to throttle the capture to ~20fps for an animated wallpaper.
    val lastBackdropCapture = remember { longArrayOf(0L) }
    val isDark = rememberIsDark(settings.theme)
    val colors = if (isDark) DarkLauncherColors else LightLauncherColors


    // One weather fetch shared by the home top bar and the Frame Art clock (enabled if either surface
    // wants it); each surface then shows it per its own toggle. Now-playing media for the top-bar chip
    // (null when off, no notification access, or nothing playing).
    val weather = rememberWeather(
        enabled = settings.weatherOnHome || settings.frameWeather,
        fahrenheit = settings.weatherUnit == WeatherUnit.FAHRENHEIT,
        lat = settings.weatherLat,
        lon = settings.weatherLon,
    )
    val nowPlaying = rememberNowPlaying(settings.nowPlaying)

    // "Choose Home app" is only offered when the device actually exposes a Home-app chooser (often
    // absent on Google TV, where the redirect accessibility service is the real mechanism).
    val chooseHomeApp: (() -> Unit)? = remember(context) {
        if (HomeSetup.canOpenHomeSettings(context)) {
            { HomeSetup.openHomeSettings(context) }
        } else {
            null
        }
    }

    // Frame Art auto-start: bump [interaction] on every key to restart the idle timer; when it elapses
    // (only while resumed, so it won't kick in behind another app) enter Frame Art. The next key exits.
    val lifecycleOwner = LocalLifecycleOwner.current
    var interaction by remember { mutableIntStateOf(0) }
    var wakingUp by remember { mutableStateOf(false) }
    val autoStartMs = settings.frameAutoStartSec * 1000L
    LaunchedEffect(interaction, autoStartMs, lifecycleOwner) {
        if (autoStartMs <= 0L) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            kotlinx.coroutines.delay(autoStartMs)
            frameOn = true
        }
    }

    // Smooth frame transition: 0 = launcher chrome present, 1 = full Frame Art (chrome gone, big clock
    // shown). [frameOn] is the target; this animates toward it so the chrome can scale up + slide out
    // and the clock fade in, then reverse on exit.
    // The two chrome layers leave/return on their own timelines for a layered feel — the dock leads,
    // the top bar trails. The master [frameProgress] drives the clock + wallpaper crossfade and all the
    // gating (chromePresent / frameSettled / focus trap); the other two only shape the motion.
    val frameProgress = remember { Animatable(0f) }
    val dockProgress = remember { Animatable(0f) }
    val topBarProgress = remember { Animatable(0f) }
    // The frosted glass "clears" (blur fades to 0) before a launch or a Frame Art entry, then re-focuses
    // gradually on the way back. Only meaningful when glass blur is on. Exposed via LocalGlassBlurAlpha
    // as a stable lambda, so it drives the draw without recomposing anything.
    val blurFade = remember { Animatable(1f) }
    val glassBlurAlpha = remember { { blurFade.value } }
    LaunchedEffect(frameOn) {
        if (frameOn) {
            // Entry: clear the glass first (if blur is on), then dissolve into the painting.
            if (settings.glassBlur) blurFade.animateTo(0f, tween(300))
            launch { frameProgress.animateTo(1f, frameMasterSpec()) }
            launch { dockProgress.animateTo(1f, frameDockSpec()) }
            launch { topBarProgress.animateTo(1f, frameTopBarSpec()) }
        } else {
            // Exit: bring the chrome back, then re-focus the glass gradually (over 650ms) at the end.
            launch { frameProgress.animateTo(0f, frameMasterSpec()) }
            launch { dockProgress.animateTo(0f, frameDockSpec()) }
            launch { topBarProgress.animateTo(0f, frameTopBarSpec()) }.join()
            blurFade.animateTo(1f, tween(650))
        }
    }
    val frameSettled by remember { derivedStateOf { frameProgress.value > 0.999f } }
    val chromePresent by remember { derivedStateOf { frameProgress.value < 0.999f } }
    val framePartly by remember { derivedStateOf { frameProgress.value > 0.001f } }
    // Mid-transition: used to drop the glass edge-refraction while the chrome is moving (cheaper, and
    // the bevel is imperceptible in motion).
    val frameMoving by remember { derivedStateOf { frameProgress.value > 0.001f && frameProgress.value < 0.999f } }

    // When Frame Art turns on, pull focus onto the capture layer so nothing behind it stays focused
    // (otherwise D-pad keys reach the grid — moving focus, or launching the focused app on OK). On
    // exit the grid recomposes from scratch and reclaims focus via its own first-card requester.
    LaunchedEffect(frameOn) {
        if (frameOn) runCatching { frameCaptureFocus.requestFocus() }
    }

    // Is Frame Art configured to a real source (folder/single)? The "current wallpaper" source has no
    // art of its own — it just shows the wallpaper full-screen — so there's nothing to overlay there.
    val frameArtConfigured = (settings.frameSource == FrameSource.FOLDER && settings.frameFolderId != null) ||
        (settings.frameSource == FrameSource.SINGLE && settings.frameImagePath != null)
    val frameArtIsWallpaper = settings.useFrameArtWallpaper && frameArtConfigured

    // The drift is composed the whole time Frame Art is present (not just when settled); its amplitude
    // follows the transition progress, read at draw time, so the float eases in on entry and glides
    // back to centre on exit rather than snapping when a key flips it off. At rest (chrome up) it's
    // disposed entirely — a still wallpaper costs nothing. Cycling still only runs when fully settled.
    val motionOn = settings.frameMotion
    val artDrift = framePartly && motionOn
    val artDriftAmount: () -> Float = { frameProgress.value }
    val artCycle = frameSettled

    // App launch / return choreography. The system grows the app window out of the tapped tile while the
    // launcher chrome drops/rises away on its own staggered timelines — the dock leads, the top bar
    // trails. 0 = home, 1 = launched (chrome gone).
    val dockLaunch = remember { Animatable(0f) }
    val topBarLaunch = remember { Animatable(0f) }
    // launchInFlight covers the brief hold between the tap and the app actually starting; awaitingReturn
    // covers the time the app is up (a launcher resume then means "returned"). Keeping them separate
    // stops a stray resume DURING the hold from firing a return — which read as the animation running
    // backwards just before the app opened.
    var launchInFlight by remember { mutableStateOf(false) }
    var awaitingReturn by remember { mutableStateOf(false) }
    var returnTick by remember { mutableIntStateOf(0) }
    var launchTick by remember { mutableIntStateOf(0) }
    // True while a launch/return is animating — used to freeze the (GPU-heavy) frosted glass and the
    // per-frame wallpaper capture so the animation itself stays smooth on weak TV hardware. The top bar
    // is the longer of the two, so it's the last to settle.
    val transitioning by remember { derivedStateOf { topBarLaunch.value > 0.001f } }

    // DEPTH-only dock ripple: remember which dock tile launched the app so the transition leads from
    // it (and the return lands on it last). -1 = launched from the grid → uniform transform as before.
    var launchDockIndex by remember { mutableIntStateOf(-1) }
    val dockRipple = remember(launchDockIndex, uiState.dockApps.size) {
        if (launchDockIndex >= 0 && launchDockIndex < uiState.dockApps.size) {
            DockRipple(launchDockIndex, uiState.dockApps.size) { dockLaunch.value }
        } else {
            null
        }
    }

    // Metro launch: its own progress (0 = home, 1 = launched) driving the tile scatter/zoom, plus the
    // launched tile's index in [metroApps] (the scatter origin). Separate from the tvOS dock ripple so
    // each style keeps its own timing; only the active style's Animatable is ever animated.
    val metroLaunch = remember { Animatable(0f) }
    var launchMetroIndex by remember { mutableIntStateOf(-1) }
    val metroScatter = remember(launchMetroIndex, settings.columns, metroApps.size) {
        if (launchMetroIndex in metroApps.indices) {
            MetroLaunch(launchMetroIndex, settings.columns) { metroLaunch.value }
        } else {
            null
        }
    }

    fun launchApp(packageName: String) {
        // No window scale-up — the app opens with the system default while the launcher chrome does the
        // dock-drop / bar-rise dissolve (the same motion as entering Frame Art).
        if (launchInFlight || awaitingReturn) return // ignore taps during the hold or while in an app
        sounds.click()
        val metro = settings.launcherStyle == LauncherStyle.WINDOWS_METRO
        if (metro) {
            launchMetroIndex = metroApps.indexOfFirst { it.packageName == packageName }
            launchDockIndex = -1
        } else {
            launchDockIndex = uiState.dockApps.indexOfFirst { it.packageName == packageName }
        }
        launchInFlight = true
        scope.launch {
            // First, when the frosted glass is on, clear it (blur -> 0 over 300ms) before the move — a
            // two-stage open. With blur off there is nothing to clear, so fly immediately.
            if (settings.glassBlur) blurFade.animateTo(0f, tween(300))
            launchTick++ // start the launch animation
            // Hold the actual app start until the launch animation has played (see Motion.kt), so a
            // fast-starting app can't cover the move halfway through. awaitingReturn stays false through
            // the hold, so a stray resume here can't fire a return.
            val hold = if (metro) METRO_LAUNCH_HOLD_MS else DEPTH_LAUNCH_HOLD_MS
            if (hold > 0) delay(hold)
            val launched = viewModel.launchApp(packageName, null)
            launchInFlight = false
            if (!launched) {
                // The app never started (no launch intent) — bring the chrome back home.
                returnTick++
                return@launch
            }
            // The app is starting; from now a launcher resume means we returned from it.
            awaitingReturn = true
            // Safety valve: if the app never covers us (still resumed well past the start), don't
            // sit chrome-less on the wallpaper — come back home.
            delay(LAUNCH_COVER_TIMEOUT_MS)
            if (awaitingReturn && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                awaitingReturn = false
                returnTick++
            }
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
            // Leaving for the app. Metro scatters its tiles on its own fast spec; tvOS drops the dock
            // and trails the top bar. Either way topBarLaunch drives `transitioning` (which freezes the
            // glass) and fades the top bar. Run in parallel so each layer keeps its own duration.
            if (settings.launcherStyle == LauncherStyle.WINDOWS_METRO) {
                launch { metroLaunch.animateTo(1f, metroLaunchSpec(entering = true)) }
                launch { topBarLaunch.animateTo(1f, metroLaunchSpec(entering = true)) }
            } else {
                launch { dockLaunch.animateTo(1f, launchDockSpec(entering = true)) }
                launch { topBarLaunch.animateTo(1f, launchTopBarSpec(entering = true)) }
            }
        }
    }
    LaunchedEffect(returnTick) {
        if (returnTick > 0) {
            // Returning from the app: start from the launched state and reverse home. Once settled,
            // forget the launch origin so the tiles shed their extra draw layers.
            if (settings.launcherStyle == LauncherStyle.WINDOWS_METRO) {
                metroLaunch.snapTo(1f)
                topBarLaunch.snapTo(1f)
                launch {
                    metroLaunch.animateTo(0f, metroLaunchSpec(entering = false))
                    launchMetroIndex = -1
                }
                launch {
                    topBarLaunch.animateTo(0f, metroLaunchSpec(entering = false))
                    // Chrome is back at rest — re-focus the glass gradually (over 650ms) at the end.
                    blurFade.animateTo(1f, tween(650))
                }
            } else {
                dockLaunch.snapTo(1f)
                topBarLaunch.snapTo(1f)
                // blurFade stays 0 (cleared) through the return — the glass is off while the chrome moves.
                launch {
                    dockLaunch.animateTo(0f, launchDockSpec(entering = false))
                    launchDockIndex = -1
                }
                launch {
                    topBarLaunch.animateTo(0f, launchTopBarSpec(entering = false))
                    // Chrome is back at rest — re-focus the glass gradually (over 650ms) at the end.
                    blurFade.animateTo(1f, tween(650))
                }
            }
        }
    }

    CompositionLocalProvider(LocalLauncherColors provides colors, LocalGlassBlurAlpha provides glassBlurAlpha) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                when {
                    // Frame Art is up. In a folder slideshow with >1 photo, Left/Right page through
                    // the photos (and stay in Frame Art); any other key exits, swallowing the press.
                    frameOn -> {
                        if (e.type == KeyEventType.KeyDown) {
                            when {
                                frameNav.canPage && e.key == Key.DirectionRight -> { sounds.navigate(); frameNav.next() }
                                frameNav.canPage && e.key == Key.DirectionLeft -> { sounds.navigate(); frameNav.previous() }
                                else -> {
                                    sounds.back()
                                    frameOn = false
                                    wakingUp = true
                                    interaction++
                                }
                            }
                        }
                        true
                    }
                    // Swallow the key-up of the press that exited the frame (no stray click/launch).
                    wakingUp -> {
                        if (e.type == KeyEventType.KeyUp) wakingUp = false
                        true
                    }
                    else -> {
                        if (e.type == KeyEventType.KeyDown) {
                            interaction++ // any key resets the idle timer
                            // The tvOS navigation tick, on every D-pad move (per the sound pack's own
                            // action mapping — selects and backs have their own sounds at their sites).
                            when (e.key) {
                                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight ->
                                    sounds.navigate()
                            }
                        }
                        false
                    }
                }
            },
    ) {
        // A launcher owns Back on its home screen: at rest, Back must do nothing (you're already
        // home). Without this, Back finishes HomeActivity, the stock launcher flashes up, and the
        // redirect service bounces right back to Tarang — a jarring blink. This is the lowest-priority
        // catch-all; inner surfaces (Settings, Frame Art below, the Dialog-based pickers/menus) register
        // their own Back handling, which — being composed later — takes precedence, so their Back works.
        BackHandler(enabled = true) { /* consume: stay home */ }
        // Frame Art owns Back too: consume it to wake the launcher instead of letting the system act
        // on it (which flashes the stock launcher / can finish this activity).
        BackHandler(enabled = frameOn || framePartly) {
            sounds.back()
            frameOn = false
            interaction++
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Skip the per-frame frosted backdrop capture during a launch zoom, once any Frame
                    // Art is showing, or when glass blur is turned off entirely (no one samples it then)
                    // — draw straight to the screen.
                    if (transitioning || framePartly || !settings.glassBlur) {
                        drawContent()
                    } else {
                        // Throttle the (full-screen, GPU-heavy) capture to ~20fps: an animated wallpaper
                        // invalidates every frame, but the blurred backdrop behind the glass doesn't need
                        // 60fps. Always composite the (possibly slightly stale) layer to the screen.
                        val now = System.nanoTime()
                        if (now - lastBackdropCapture[0] >= BACKDROP_CAPTURE_INTERVAL_NS) {
                            backdrop.record { this@drawWithContent.drawContent() }
                            lastBackdropCapture[0] = now
                        }
                        drawLayer(backdrop)
                    }
                },
        ) {
            // Hover artwork (when App artwork is on) takes priority over everything — including Frame
            // Art as the wallpaper — then falls back to the frame art / image / gradient base.
            Crossfade(targetState = artworkApp, animationSpec = tween(700), label = "wallpaper") { app ->
                when {
                    app != null -> AppArtworkWallpaper(
                        packageName = app,
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Frame Art as the base wallpaper (a still frame on home; alive in frame mode).
                    frameArtIsWallpaper -> FrameArtContent(
                        settings,
                        drift = artDrift,
                        driftAmount = artDriftAmount,
                        cycle = artCycle,
                        isDark = isDark,
                        nav = frameNav,
                        modifier = Modifier.fillMaxSize(),
                    )

                    showImage && imagePath != null -> ImageWallpaper(
                        path = imagePath,
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> AnimatedWallpaper(
                        preset = preset,
                        animated = false, // the wallpaper is always a still gradient now
                        ambient = null,
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // Frame mode with a configured source that ISN'T already the wallpaper: fade the art in over
            // the current wallpaper as the chrome leaves. (The "current wallpaper" frame source has no
            // art of its own, so nothing overlays — it just shows the wallpaper full-screen.)
            if (framePartly && frameArtConfigured && !frameArtIsWallpaper) {
                Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = frameProgress.value
                        // DEPTH lets the art rise forward from a hair larger; others just crossfade.
                        val s = artEntryScale(frameProgress.value)
                        scaleX = s
                        scaleY = s
                    },
                ) {
                    FrameArtContent(settings, drift = artDrift, driftAmount = artDriftAmount, cycle = artCycle, isDark = isDark, nav = frameNav, modifier = Modifier.fillMaxSize())
                }
            }
        }

        // Night dimming: a scrim over the art (below the clock, so the time stays readable) that ramps
        // up in the small hours. Fades in with the frame transition.
        if (framePartly && settings.frameNightDim) {
            NightDimScrim(reveal = { frameProgress.value }, modifier = Modifier.fillMaxSize())
        }

        // Big Frame Art clock (with optional weather). Its entrance (fade + slight scale on the text,
        // fade-only on the scrim) is sequenced to arrive in the back half of the transition.
        if (framePartly && settings.frameClock) {
            FrameClock(
                position = settings.frameClockPosition,
                size = settings.frameClockSize,
                showDate = settings.frameShowDate,
                reveal = { ((frameProgress.value - 0.45f) / 0.55f).coerceIn(0f, 1f) },
                weather = if (settings.frameWeather) weather else null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Launcher chrome (settings page or the grid). Hidden entirely in Frame Art, which is a pure,
        // chrome-free "painting".
        if (chromePresent) {
            // The settings page takes over the whole screen (not a modal), so D-pad focus can't reach
            // the launcher behind it; the launcher isn't composed while settings is open.
            if (showSettings) {
                SettingsScreen(
                    settings = settings,
                    onWallpaper = viewModel::setWallpaper,
                    onGlassBlur = viewModel::setGlassBlur,
                    onColumns = viewModel::setColumns,
                    onLauncherStyle = viewModel::setLauncherStyle,
                    onPickImage = pickImage,
                    onUseImage = { viewModel.setUseImageWallpaper(true) },
                    onScanTvContent = { showTvProbe = true },
                    favoriteApps = uiState.dockApps,
                    onUseAppArtwork = viewModel::setUseAppArtwork,
                    onToggleArtworkApp = viewModel::setArtworkApp,
                    theme = settings.theme,
                    onTheme = viewModel::setTheme,
                    hiddenApps = uiState.allApps.filter { it.packageName in settings.hiddenApps },
                    onUnhideApp = { viewModel.setAppHidden(it, false) },
                    onFrameSource = viewModel::setFrameSource,
                    onPickFrameFolder = pickFrameFolder,
                    onPickFramePhoto = pickFramePhoto,
                    onFrameInterval = viewModel::setFrameInterval,
                    onFrameAutoStart = viewModel::setFrameAutoStart,
                    onFrameClock = viewModel::setFrameClock,
                    onFrameClockPosition = viewModel::setFrameClockPosition,
                    onFrameClockSize = viewModel::setFrameClockSize,
                    onFrameShowDate = viewModel::setFrameShowDate,
                    onFrameMotion = viewModel::setFrameMotion,
                    onFrameShuffle = viewModel::setFrameShuffle,
                    onUseFrameArtWallpaper = viewModel::setUseFrameArtWallpaper,
                    onWeatherOnHome = viewModel::setWeatherOnHome,
                    onFrameWeather = viewModel::setFrameWeather,
                    onWeatherUnit = viewModel::setWeatherUnit,
                    onWeatherCity = viewModel::setWeatherCity,
                    onWeatherAuto = viewModel::clearWeatherCity,
                    onFrameNightDim = viewModel::setFrameNightDim,
                    onNowPlaying = viewModel::setNowPlaying,
                    onNavSounds = viewModel::setNavSounds,
                    onOpenNotificationAccess = { openNotificationAccess(context) },
                    onOpenAccessibilitySettings = { HomeSetup.openAccessibilitySettings(context) },
                    onOpenAndroidSettings = { openAndroidSettings(context) },
                    onChooseHomeApp = chooseHomeApp,
                    onClose = { sounds.back(); showSettings = false },
                )
            } else if (settings.launcherStyle == LauncherStyle.WINDOWS_METRO) {
                // Windows Metro home. The whole surface fades out as Frame Art takes over (frameProgress);
                // per-tile scatter (metroScatter) carries app launches. The shared top bar rides in
                // through MetroHome's slot and fades on launch via topBarLaunch.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - frameProgress.value * 1.5f).coerceIn(0f, 1f) },
                ) {
                    when {
                        uiState.isLoading -> Centered { Text("Loading apps…", color = colors.text, fontSize = 20.sp) }
                        uiState.allApps.isEmpty() -> Centered { Text("No apps found", color = colors.text, fontSize = 20.sp) }
                        else -> MetroHome(
                            apps = metroApps,
                            columns = settings.columns,
                            iconLoader = container.iconLoader,
                            scatter = metroScatter,
                            onAppFocused = viewModel::onAppFocused,
                            onAppClicked = { pkg -> launchApp(pkg) },
                            topFocusRequester = tuneFocus,
                            topBar = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = (1f - topBarLaunch.value).coerceIn(0f, 1f) },
                                ) {
                                    TopBar(
                                        onOpenSettings = { sounds.click(); showSettings = true },
                                        onEnterFrame = { sounds.click(); frameOn = true },
                                        nowPlaying = nowPlaying,
                                        onOpenNowPlaying = { pkg -> launchApp(pkg) },
                                        homeWeather = if (settings.weatherOnHome) weather else null,
                                        tuneFocus = tuneFocus,
                                        backdrop = backdrop,
                                        glassLive = !transitioning && !frameMoving,
                                        glassRefract = !transitioning && !frameMoving,
                                        glassBlur = settings.glassBlur,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top bar rises up and off the top — driven by BOTH the frame transition and an app
                    // launch (topBarLaunch), so launching an app uses the same chrome choreography.
                    Box(
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            applyChrome(
                                ChromeLayer.TOP_BAR,
                                frameP = topBarProgress.value,
                                launchP = topBarLaunch.value,
                            )
                        },
                    ) {
                        TopBar(
                            onOpenSettings = { sounds.click(); showSettings = true },
                            onEnterFrame = { sounds.click(); frameOn = true },
                            nowPlaying = nowPlaying,
                            // Clicking the chip jumps back into whatever app is playing, with the
                            // same launch choreography as a grid tile.
                            onOpenNowPlaying = { pkg -> launchApp(pkg) },
                            homeWeather = if (settings.weatherOnHome) weather else null,
                            tuneFocus = tuneFocus,
                            backdrop = backdrop,
                            // Drop the blur entirely while the chrome moves (a launch OR a Frame Art
                            // transition): the blur is a per-frame GPU pass and re-blurring moving,
                            // fading chips is the biggest jank source on a weak TV GPU. Moving chips fall
                            // back to the flat translucent tint — imperceptible mid-fade, and it holds
                            // 60fps through the transition. Full frosted glass returns at rest.
                            glassLive = !transitioning && !frameMoving,
                            glassRefract = !transitioning && !frameMoving,
                            glassBlur = settings.glassBlur,
                        )
                    }
                    // Dock + grid scale up and drop off the bottom — same choreography for frame mode and
                    // for app launch (so the two share one motion language).
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                applyChrome(
                                    ChromeLayer.DOCK,
                                    frameP = dockProgress.value,
                                    // With a dock ripple, the chrome takes the trailing slot — the
                                    // launched tile and its neighbours lead (see DockRipple).
                                    launchP = dockRipple?.chromeProgress() ?: dockLaunch.value,
                                )
                            },
                    ) {
                        when {
                            uiState.isLoading -> Centered { Text("Loading apps…", color = colors.text, fontSize = 20.sp) }
                            uiState.allApps.isEmpty() -> Centered { Text("No apps found", color = colors.text, fontSize = 20.sp) }
                            else -> LauncherContent(
                                dockApps = uiState.dockApps,
                                gridApps = visibleGrid,
                                iconLoader = container.iconLoader,
                                dockRipple = dockRipple,
                                onAppFocused = viewModel::onAppFocused,
                                onAppClicked = { pkg -> launchApp(pkg) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onReorder = viewModel::setFavoritesOrder,
                                columns = settings.columns,
                                backdrop = backdrop,
                                topFocusRequester = tuneFocus,
                                onFavoriteHover = { favoriteHover = it },
                                onHideApp = { viewModel.setAppHidden(it, true) },
                                onAppInfo = { viewModel.openAppInfo(it) },
                                onUninstall = { viewModel.uninstallApp(it) },
                                glassLive = !transitioning && !frameMoving,
                                glassRefract = !transitioning && !frameMoving,
                                glassBlur = settings.glassBlur,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        if (showPicker) {
            ImagePickerDialog(onPick = { applyPickedImage(it) }, onDismiss = { showPicker = false })
        }

        if (showFolderPicker) {
            FolderPickerDialog(
                onPick = { id, name ->
                    showFolderPicker = false
                    viewModel.setFrameFolder(id, name)
                },
                onDismiss = { showFolderPicker = false },
            )
        }

        if (showTvProbe) {
            TvProbeDialog(onDismiss = { showTvProbe = false })
        }

        // Transparent focus trap, on top while Frame Art is showing: it owns D-pad focus so the grid
        // (still composed during the entry/exit transition, removed once settled) can't be navigated
        // or clicked behind the art. The root key handler above turns the first key into "wake", so
        // this only needs to hold focus — it has no key logic of its own.
        if (frameOn || framePartly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(frameCaptureFocus)
                    .focusable(),
            )
        }
    }
    }
}

// One chip height and one stadium shape for every top-bar element, so the bar reads as a single
// family of chips rather than mixed widgets (the clock used to be a squarer rounded-rect).
private val ChipHeight = 56.dp
private val ChipShape = RoundedCornerShape(percent = 50)
// The top-bar chips are small and heavily blurred, so a coarser capture (≈1/3 res) is invisible but
// cuts the blur's pixel count to ~4/9 of the dock's half-res. The dock keeps the default (0.5).
private const val ChipDownscale = 0.34f

@Composable
private fun TopBar(
    onOpenSettings: () -> Unit,
    onEnterFrame: () -> Unit,
    nowPlaying: NowPlaying?,
    onOpenNowPlaying: (String) -> Unit,
    homeWeather: WeatherData?,
    tuneFocus: FocusRequester,
    backdrop: GraphicsLayer,
    glassLive: Boolean,
    glassRefract: Boolean,
    glassBlur: Boolean,
) {
    val context = LocalContext.current
    val net = rememberNetStatus()
    val colors = LocalLauncherColors.current
    val tint = if (glassBlur) colors.textBackdrop else colors.textBackdropOpaque
    // A healthy connection is silence: the Wi-Fi chip only appears when something needs attention
    // (offline, or a weak Wi-Fi signal) and then click-throughs to Wi-Fi settings. Android settings
    // moved into the launcher Settings page (Home setup), so the resting bar stays minimal.
    val netNeedsAttention = !net.online || net.kind == NetKind.NONE ||
        (net.kind == NetKind.WIFI && net.wifiLevel <= 1)
    // Three anchored slots (start / centre / end) instead of SpaceBetween: the now-playing chip is
    // centred on the SCREEN, not between the flanking groups, so it doesn't drift when the weather
    // pill appears.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, top = 28.dp),
    ) {
        // Left group: clock + (optional) weather, kept together as their own frosted containers so text
        // stays legible over any wallpaper without scrimming the whole image.
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clock(
                modifier = Modifier
                    .height(ChipHeight)
                    .frostedGlass(backdrop, ChipShape, tint = tint, live = glassLive, refract = glassRefract, blur = glassBlur && glassLive, downscale = ChipDownscale)
                    .padding(horizontal = 20.dp),
            )
            homeWeather?.let { w ->
                Row(
                    modifier = Modifier
                        .height(ChipHeight)
                        .frostedGlass(backdrop, ChipShape, tint = tint, live = glassLive, refract = glassRefract, blur = glassBlur && glassLive, downscale = ChipDownscale)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = w.glyph, fontSize = 16.sp)
                    Text(text = w.tempText, color = colors.text, fontSize = 18.sp)
                }
            }
        }
        // Centre: the "now playing" chip. Retain the last snapshot while animating out so the exit
        // doesn't collapse to an empty pill.
        var lastNowPlaying by remember { mutableStateOf(nowPlaying) }
        if (nowPlaying != null) lastNowPlaying = nowPlaying
        AnimatedVisibility(
            visible = nowPlaying != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(200)),
        ) {
            lastNowPlaying?.let { np ->
                NowPlayingChip(
                    nowPlaying = np,
                    onClick = { onOpenNowPlaying(np.packageName) },
                    backdrop = backdrop,
                    tint = tint,
                    glassLive = glassLive,
                    glassRefract = glassRefract,
                    glassBlur = glassBlur,
                )
            }
        }
        // End group: quiet status + actions, icon-only.
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(ChipHeight)
                .frostedGlass(backdrop, ChipShape, tint = tint, live = glassLive, refract = glassRefract, blur = glassBlur && glassLive, downscale = ChipDownscale)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (netNeedsAttention) {
                PillButton(onClick = { openWifiSettings(context) }, contentDescription = "Wi-Fi settings") {
                    WifiIndicator(status = net, tint = colors.text, modifier = Modifier.size(22.dp))
                }
            }
            PillButton(onClick = onEnterFrame, contentDescription = "Frame Art") {
                Image(
                    painterResource(R.drawable.ic_frame),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(colors.text),
                )
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
        shape = ClickableSurfaceDefaults.shape(ChipShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = colors.text.copy(alpha = 0.22f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/** A compact frosted "now playing" chip: album art + track title (artist if room). Click opens the app. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NowPlayingChip(
    nowPlaying: NowPlaying,
    onClick: () -> Unit,
    backdrop: GraphicsLayer,
    tint: Color,
    glassLive: Boolean,
    glassRefract: Boolean,
    glassBlur: Boolean,
) {
    val colors = LocalLauncherColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(ChipHeight)
            .frostedGlass(backdrop, ChipShape, tint = tint, live = glassLive, refract = glassRefract, blur = glassBlur && glassLive, downscale = ChipDownscale)
            .semantics { contentDescription = "Now playing: ${nowPlaying.title}. Open app" },
        shape = ClickableSurfaceDefaults.shape(ChipShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = colors.text.copy(alpha = 0.14f),
        ),
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .height(ChipHeight)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            nowPlaying.art?.let { art ->
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
            Column {
                Text(
                    text = nowPlaying.title,
                    color = colors.text,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                nowPlaying.artist?.let {
                    Text(text = it, color = colors.textDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
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

private fun openNotificationAccess(context: Context) {
    runCatching {
        context.startActivity(Intent(NOTIFICATION_ACCESS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
