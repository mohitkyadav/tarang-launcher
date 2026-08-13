package com.tarang.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.MetroTile

private val MetroSidePad = 48.dp // horizontal screen margin for the Start screen
private val MetroGap = 12.dp // tight gap between tiles (the dense Metro look)
private val MetroTopPad = 20.dp // gap under the top bar

/**
 * The Windows Metro home surface: a scrolling grid of flat, sharp-cornered live tiles, each in the
 * app's own accent color with the icon centered and the name in the bottom-left. It reuses the shared
 * launcher machinery through its inputs — [topBar] is the shared top bar (clock / Frame Art /
 * settings), and [scatter] carries the launch/return animation driven by [LauncherScreen].
 *
 * A LazyColumn of rows (not a LazyVerticalGrid) keeps D-pad focus reliable on TV — the same choice
 * the tvOS layout makes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetroHome(
    apps: List<AppInfo>,
    columns: Int,
    iconLoader: IconLoader,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    topFocusRequester: FocusRequester?,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scatter: MetroLaunch? = null,
) {
    val rows = remember(apps, columns) { apps.chunked(columns) }
    val firstCard = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Entrance cascade: runs once when the Start screen appears (and again if the user switches into
    // Metro from another style). Read lazily at draw time so it never triggers a recomposition.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, metroAppearSpec()) }
    val appearProgress = remember { { appear.value } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availWidth = maxWidth - MetroSidePad * 2
        val tileSize = (availWidth - MetroGap * (columns - 1)) / columns

        // Same scale-halo slop as the tvOS grid, so the first Left/Right move into a row never nudges.
        val bringIntoView = remember(tileSize, density) {
            minimalBringIntoView(with(density) { tileSize.toPx() } * 0.08f + 3f)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            topBar()
            CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = MetroSidePad,
                        end = MetroSidePad,
                        top = MetroTopPad,
                        bottom = 56.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MetroGap),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.first().packageName }) { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(MetroGap)) {
                            row.forEachIndexed { colIndex, app ->
                                val index = rowIndex * columns + colIndex
                                MetroCard(
                                    app = app,
                                    iconLoader = iconLoader,
                                    size = tileSize,
                                    onFocused = { onAppFocused(app.packageName) },
                                    onClick = { onAppClicked(app.packageName) },
                                    // Only the top row sends D-pad UP to the top bar (settings button).
                                    upFocusRequester = if (rowIndex == 0) topFocusRequester else null,
                                    layer = {
                                        applyMetroAppear(appearProgress(), rowIndex, colIndex)
                                        scatter?.tileLayer(index)?.invoke(this)
                                    },
                                    modifier = Modifier
                                        .then(if (index == 0) Modifier.focusRequester(firstCard) else Modifier)
                                        // The hero tile draws over its neighbours as it zooms out.
                                        .then(
                                            if (scatter != null && index == scatter.origin) {
                                                Modifier.zIndex(10f)
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(apps.firstOrNull()?.packageName) {
            if (apps.isNotEmpty()) runCatching { firstCard.requestFocus() }
        }
    }
}

/**
 * One Metro tile: a flat, sharp-cornered square in the app's accent color, with the icon centered and
 * the label in the bottom-left. The text color flips with the tile's luminance so it stays legible on
 * any accent. On focus the tile scales up a touch and gains a white outline. [layer] carries the
 * entrance cascade and launch scatter; it composes on top of the focus scale.
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
