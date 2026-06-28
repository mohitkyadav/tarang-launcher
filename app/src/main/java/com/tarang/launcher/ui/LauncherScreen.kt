package com.tarang.launcher.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.R
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.TileArt
import com.tarang.launcher.di.AppContainer
import com.tarang.launcher.viewmodel.LauncherViewModel
import kotlin.math.roundToInt

/** A tile being launched: where it sits on screen and which app it is, for the zoom transition. */
private data class LaunchAnim(val app: AppInfo, val bounds: Rect?)

/** Smooth ease-in-out so the tile accelerates forward and decelerates into the app. */
private val LaunchEasing = CubicBezierEasing(0.35f, 0f, 0.2f, 1f)
private const val LAUNCH_MS = 430

/**
 * Top-level launcher UI: an animated wallpaper behind a clean app grid, a top bar with the clock
 * and settings (tune) button, and a tile→app zoom transition on launch. No content rows.
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

    // Launch transition state: the tile we're zooming, and a "fade in" when we return from an app.
    var focusedBounds by remember { mutableStateOf<Rect?>(null) }
    var launching by remember { mutableStateOf<LaunchAnim?>(null) }
    var entering by remember { mutableStateOf(false) }

    // One progress drives both the receding launcher and the zooming tile so they stay in lockstep.
    val launchProgress = remember { Animatable(0f) }
    LaunchedEffect(launching) {
        val anim = launching
        if (anim == null) {
            launchProgress.snapTo(0f)
            return@LaunchedEffect
        }
        launchProgress.snapTo(0f)
        launchProgress.animateTo(1f, tween(durationMillis = LAUNCH_MS, easing = LaunchEasing))
        if (!viewModel.launchApp(anim.app.packageName)) launching = null // recover if it won't open
    }
    val recede = if (launching != null) launchProgress.value else 0f

    // When we come back from a launched app, fade the launcher in from black.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && launching != null) {
                launching = null
                entering = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        // The launcher itself — recedes (scales down + fades) as a tile is launched.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f - 0.10f * recede
                    scaleX = s
                    scaleY = s
                    alpha = 1f - recede
                },
        ) {
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
                            onAppClicked = { pkg ->
                                val app = uiState.allApps.firstOrNull { it.packageName == pkg }
                                if (app != null) launching = LaunchAnim(app, focusedBounds) else viewModel.launchApp(pkg)
                            },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onReorder = viewModel::setFavoritesOrder,
                            topFocusRequester = tuneFocus,
                            onAppBounds = { focusedBounds = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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

        // Tile → app zoom. The clone flies out of the tile's slot to fill the screen, then
        // dissolves to black to hand off to the opening app.
        launching?.let { anim ->
            LaunchZoom(anim = anim, progress = launchProgress.value, iconLoader = container.iconLoader)
        }

        // Returning from an app: fade the launcher back in.
        if (entering) {
            FadeIn(onDone = { entering = false })
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

/**
 * The launch transition: a clone of the focused tile zooms out from its slot to fill the screen,
 * staying fully opaque, then dissolves to black in the final stretch so opening an app feels like
 * flying into it. Falls back to a centered zoom if the tile's bounds weren't captured.
 */
@Composable
private fun LaunchZoom(anim: LaunchAnim, progress: Float, iconLoader: IconLoader) {
    val tile: TileArt? by produceState<TileArt?>(initialValue = null, anim.app.packageName) {
        value = iconLoader.loadTile(anim.app)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val t = progress

        val start = anim.bounds ?: Rect(w * 0.42f, h * 0.42f, w * 0.58f, h * 0.58f)
        val ratio = if (start.height > 0f) start.width / start.height else w / h
        // Grow about the tile's OWN centre (so the artwork doesn't drift), keeping the tile's
        // aspect ratio, to a rect big enough to cover the screen from that centre.
        val cx = (start.left + start.right) / 2f
        val cy = (start.top + start.bottom) / 2f
        val margin = 1.1f
        var halfW = maxOf(cx, w - cx) * margin
        var halfH = halfW / ratio
        val needHalfH = maxOf(cy, h - cy) * margin
        if (halfH < needHalfH) {
            halfH = needHalfH
            halfW = halfH * ratio
        }
        val cur = Rect(
            left = lerp(start.left, cx - halfW, t),
            top = lerp(start.top, cy - halfH, t),
            right = lerp(start.right, cx + halfW, t),
            bottom = lerp(start.bottom, cy + halfH, t),
        )
        val density = LocalDensity.current
        // Container fades in over the icon and is fully solid by 90% of the animation.
        val containerAlpha = (t / 0.9f).coerceIn(0f, 1f)
        val blurRadius = (28f * containerAlpha).dp

        // Black behind the tile; the surrounding launcher has already faded to it.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = containerAlpha)))
        Box(
            modifier = Modifier
                .offset { IntOffset(cur.left.roundToInt(), cur.top.roundToInt()) }
                .size(
                    width = with(density) { cur.width.toDp() },
                    height = with(density) { cur.height.toDp() },
                )
                .clip(RoundedCornerShape(lerp(24f, 0f, t).dp)),
        ) {
            // The artwork, progressively blurred as the container takes over.
            Box(modifier = Modifier.fillMaxSize().blurCompat(blurRadius)) {
                when (val art = tile) {
                    is TileArt.Banner -> Image(
                        bitmap = art.image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    is TileArt.Fallback -> Box(
                        modifier = Modifier.fillMaxSize().background(art.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        art.icon?.let {
                            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(96.dp))
                        }
                    }
                    null -> Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2C)))
                }
            }
            // The container, layered on top of the icon, fully solid by 90%.
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = containerAlpha)))
        }
    }
}

@Composable
private fun FadeIn(onDone: () -> Unit) {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(0f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
        onDone()
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha.value)))
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** [Modifier.blur] is a no-op below API 31; gate it so we don't pay for a blur that won't render. */
private fun Modifier.blurCompat(radius: Dp): Modifier =
    if (radius > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this
