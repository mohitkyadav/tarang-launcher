package com.tarang.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.MetroTile

private val MetroSidePad = 48.dp // horizontal screen margin
private val MetroGap = 12.dp // tight gap between tiles (the dense Metro look)
private val MetroGutter = 44.dp // the vertical channel between the favorites group and the rest
private val MetroTopPad = 16.dp // gap under the top bar
private val MetroTargetTile = 168.dp // preferred tile side; the row count is derived from it
private const val MetroMinRows = 3
private const val MetroMaxRows = 5

/**
 * The Windows Metro home surface: a horizontally-scrolling board of flat, sharp-cornered live tiles.
 * Tiles fill each column top-to-bottom, then the next column (column-major), the way the Windows 8
 * Start screen packs them. The favorites form the first group; a wide gutter separates them from the
 * rest. With no favorites there is a single group and no gutter.
 *
 * D-pad Up/Down moves within a column; Left/Right crosses columns (and the gutter) and scrolls the
 * board. The shared top bar (clock / Frame Art / settings) rides in through [topBar]. [launchProgress]
 * (0 = home, 1 = app in front) drives the Windows 8.1 build-in: a dark overlay plus the per-column
 * slide-in wave (see Motion.kt). The cold-start entrance plays the same wave.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetroHome(
    favorites: List<AppInfo>,
    others: List<AppInfo>,
    iconLoader: IconLoader,
    launchProgress: () -> Float,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    topFocusRequester: FocusRequester?,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flatSize = favorites.size + others.size
    val firstCard = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Cold-start entrance: the tiles start "gone" (off to the right) and slide home once, in the same
    // right-to-left wave a return uses. Read lazily at draw time so it never triggers a recomposition.
    val appear = remember { Animatable(1f) }
    LaunchedEffect(Unit) { appear.animateTo(0f, metroAppearSpec()) }
    val appearProgress = remember { { appear.value } }

    Box(modifier = modifier.fillMaxSize()) {
        // The dark backdrop over the wallpaper (behind the tiles). It rests at full while the Start
        // screen is home and fades in as the board returns; it clears as an app takes the front. Driven
        // by whichever transition is active (the cold-start entrance or a launch/return).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = metroOverlayAlpha(maxOf(launchProgress(), appearProgress())) }
                .background(Color.Black),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // The whole tile board scales as one group (0.8 → 1); the per-tile layers only
                    // slide and fade. Read at draw time so it never triggers a recomposition.
                    .graphicsLayer {
                        val s = metroGroupScale(maxOf(launchProgress(), appearProgress()))
                        scaleX = s
                        scaleY = s
                    },
            ) {
                // Derive the row count (and therefore the tile size) from the board height.
                val rows = ((maxHeight.value + MetroGap.value) / (MetroTargetTile.value + MetroGap.value))
                    .toInt().coerceIn(MetroMinRows, MetroMaxRows)
                val tileSize = (maxHeight - MetroGap * (rows - 1)) / rows

                val favCols = remember(favorites, rows) { favorites.chunked(rows) }
                val restCols = remember(others, rows) { others.chunked(rows) }
                val favCount = favorites.size
                val maxCol = favCols.size + restCols.size - 1

                // Same scale-halo slop as the tvOS grid, so the first Left/Right move never nudges.
                val bringIntoView = remember(tileSize, density) {
                    minimalBringIntoView(with(density) { tileSize.toPx() } * 0.08f + 3f)
                }

                CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = MetroSidePad,
                            end = MetroSidePad,
                            top = MetroTopPad,
                            bottom = 8.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(MetroGap),
                        verticalAlignment = Alignment.Top,
                    ) {
                        itemsIndexed(favCols, key = { _, col -> "fav-" + col.first().packageName }) { ci, col ->
                            MetroColumn(
                                apps = col,
                                iconLoader = iconLoader,
                                tileSize = tileSize,
                                absoluteCol = ci,
                                maxCol = maxCol,
                                flatStart = ci * rows,
                                launchProgress = launchProgress,
                                appearProgress = appearProgress,
                                firstCard = firstCard,
                                topFocusRequester = topFocusRequester,
                                onAppFocused = onAppFocused,
                                onAppClicked = onAppClicked,
                            )
                        }
                        if (favCols.isNotEmpty() && restCols.isNotEmpty()) {
                            item(key = "gutter") { Spacer(Modifier.width(MetroGutter)) }
                        }
                        itemsIndexed(restCols, key = { _, col -> "rest-" + col.first().packageName }) { ci, col ->
                            MetroColumn(
                                apps = col,
                                iconLoader = iconLoader,
                                tileSize = tileSize,
                                absoluteCol = favCols.size + ci,
                                maxCol = maxCol,
                                flatStart = favCount + ci * rows,
                                launchProgress = launchProgress,
                                appearProgress = appearProgress,
                                firstCard = firstCard,
                                topFocusRequester = topFocusRequester,
                                onAppFocused = onAppFocused,
                                onAppClicked = onAppClicked,
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(favorites.firstOrNull()?.packageName, others.firstOrNull()?.packageName) {
        if (flatSize > 0) runCatching { firstCard.requestFocus() }
    }
}

/** One board column: a top-to-bottom stack of up to `rows` tiles. */
@Composable
private fun MetroColumn(
    apps: List<AppInfo>,
    iconLoader: IconLoader,
    tileSize: Dp,
    absoluteCol: Int,
    maxCol: Int,
    flatStart: Int,
    launchProgress: () -> Float,
    appearProgress: () -> Float,
    firstCard: FocusRequester,
    topFocusRequester: FocusRequester?,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MetroGap)) {
        apps.forEachIndexed { positionInColumn, app ->
            val flatIndex = flatStart + positionInColumn
            MetroCard(
                app = app,
                iconLoader = iconLoader,
                size = tileSize,
                onFocused = { onAppFocused(app.packageName) },
                onClick = { onAppClicked(app.packageName) },
                // Only the top tile of each column sends D-pad UP to the top bar (settings button).
                upFocusRequester = if (positionInColumn == 0) topFocusRequester else null,
                layer = {
                    // The tile slides for whichever is larger: the one-shot entrance or a launch/return.
                    val gone = maxOf(
                        metroGone(appearProgress(), absoluteCol, maxCol),
                        metroGone(launchProgress(), absoluteCol, maxCol),
                    )
                    applyMetroSlide(gone)
                },
                modifier = if (flatIndex == 0) Modifier.focusRequester(firstCard) else Modifier,
            )
        }
    }
}

/**
 * One Metro tile: a flat, sharp-cornered square in the app's accent color, with the icon centered and
 * the label in the bottom-left. The text color flips with the tile's luminance so it stays legible on
 * any accent. On focus the tile scales up a touch and gains a white outline. [layer] carries the
 * entrance/launch slide; it composes on top of the focus scale.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetroCard(
    app: AppInfo,
    iconLoader: IconLoader,
    size: Dp,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    upFocusRequester: FocusRequester?,
    layer: GraphicsLayerScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val tile by androidx.compose.runtime.produceState<MetroTile?>(initialValue = null, app.packageName) {
        value = iconLoader.loadMetroTile(app)
    }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "metroScale",
    )

    val colors = LocalLauncherColors.current
    val tileColor = tile?.color ?: colors.chip
    val onTile = if (tileColor.luminance() > 0.5f) Color.Black else Color.White

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                layer()
            }
            .then(if (focused) Modifier.border(3.dp, Color.White, RectangleShape) else Modifier)
            .then(
                if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = tileColor,
            focusedContainerColor = tileColor,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            tile?.icon?.let {
                Image(
                    bitmap = it,
                    contentDescription = app.label,
                    modifier = Modifier.align(Alignment.Center).size(size * 0.42f),
                )
            }
            Text(
                text = app.label,
                color = onTile,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            )
        }
    }
}
