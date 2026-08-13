package com.tarang.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.R
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.MetroTile

private val MetroSidePad = 64.dp // horizontal screen margin (also the space kept past the first/last tile)
private val MetroGap = 12.dp // tight gap between tiles (the dense Metro look)
private val MetroGutter = 44.dp // the vertical channel between the favorites group and the rest
// Tiles per column (the vertical count). The tile SIZE is the board height / [MetroSizeDivisor], so with
// a divisor of 4 the tiles keep the "4-up" size, but only 3 pack per column — the leftover vertical
// space becomes a comfortable margin above/below the grid (a clear gap under the clock). The screen is
// 1080p (960×540 dp) on both the emulator and the TV, so this is identical on the real device.
private const val MetroRows = 3
private const val MetroSizeDivisor = 4

/**
 * One placed item in a board column. A [Wide] tile fills the whole column width; a [Squares] slot holds
 * one or two square tiles side-by-side in the same footprint as a wide tile — so squares pack two-to-a-
 * slot without leaving a hole in the column.
 */
private sealed interface MetroSlot {
    data class Wide(val app: AppInfo) : MetroSlot
    data class Squares(val a: AppInfo, val b: AppInfo?) : MetroSlot
}

/** Packs an ordered app list into slots: wide apps take a slot each; consecutive squares pair up. */
private fun packSlots(apps: List<AppInfo>, squareApps: Set<String>): List<MetroSlot> {
    val slots = mutableListOf<MetroSlot>()
    var i = 0
    while (i < apps.size) {
        val app = apps[i]
        if (app.packageName in squareApps) {
            val next = apps.getOrNull(i + 1)
            if (next != null && next.packageName in squareApps) {
                slots.add(MetroSlot.Squares(app, next)); i += 2
            } else {
                slots.add(MetroSlot.Squares(app, null)); i += 1
            }
        } else {
            slots.add(MetroSlot.Wide(app)); i += 1
        }
    }
    return slots
}

/**
 * The Windows Metro home surface: a horizontally-scrolling board of flat, sharp-cornered live tiles.
 * Slots fill each column top-to-bottom, then the next column (column-major), the way the Windows 8
 * Start screen packs them. Tiles default to wide; long-press a tile to switch it to square (squares
 * then pair two-to-a-slot). The favorites form the first group; a wide gutter separates them from the
 * rest. With no favorites there is a single group and no gutter.
 *
 * D-pad Up/Down moves within a column; Left/Right crosses columns (and the gutter) and scrolls the
 * board. The shared top bar rides in through [topBar]. [launchProgress] (0 = home, 1 = app in front)
 * drives the Windows 8.1 build-in (dark backdrop + per-column slide-in wave, see Motion.kt); the
 * cold-start entrance plays the same wave.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetroHome(
    favorites: List<AppInfo>,
    others: List<AppInfo>,
    squareApps: Set<String>,
    iconLoader: IconLoader,
    launchProgress: () -> Float,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    onSetTileSquare: (String, Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onHideApp: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    topFocusRequester: FocusRequester?,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flatSize = favorites.size + others.size
    val firstCard = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Long-press menu target (resize / manage). Null when the menu is closed.
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    val openMenu: (String) -> Unit = { pkg ->
        menuApp = (favorites + others).firstOrNull { it.packageName == pkg }
    }

    // Cold-start entrance: the tiles start "gone" (off to the right) and slide home once, in the same
    // right-to-left wave a return uses. Read lazily at draw time so it never triggers a recomposition.
    val appear = remember { Animatable(1f) }
    LaunchedEffect(Unit) { appear.animateTo(0f, metroAppearSpec()) }
    val appearProgress = remember { { appear.value } }

    Box(modifier = modifier.fillMaxSize()) {
        // The dark backdrop over the wallpaper (behind the tiles). It rests at full while the Start
        // screen is home and fades in as the board returns; it clears as an app takes the front.
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
                // Tile size follows the size divisor (keeps the liked size); only [rows] pack per column.
                // The leftover vertical space becomes the margin above/below the grid. Every tile is one
                // row tall; a WIDE tile is two squares plus a gap wide (Metro's wide live tile).
                val rows = MetroRows
                val tileHeight = (maxHeight - MetroGap * (MetroSizeDivisor - 1)) / MetroSizeDivisor
                val wideWidth = tileHeight * 2 + MetroGap
                val columnHeight = tileHeight * rows + MetroGap * (rows - 1)
                val vMargin = (maxHeight - columnHeight).coerceAtLeast(0.dp)

                val favCols = remember(favorites, squareApps, rows) { packSlots(favorites, squareApps).chunked(rows) }
                val restCols = remember(others, squareApps, rows) { packSlots(others, squareApps).chunked(rows) }
                val maxCol = favCols.size + restCols.size - 1

                // slop absorbs the focus scale-halo (so a visible tile never re-fits); inset keeps a
                // focused edge tile the same margin off the screen edges as the resting side padding.
                val bringIntoView = remember(wideWidth, density) {
                    minimalBringIntoView(
                        slop = with(density) { wideWidth.toPx() } * 0.08f + 3f,
                        inset = with(density) { MetroSidePad.toPx() },
                    )
                }

                CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = MetroSidePad,
                            end = MetroSidePad,
                            // Bias the leftover space toward the top, for a clear gap below the clock.
                            top = vMargin * 0.6f,
                            bottom = vMargin * 0.4f,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(MetroGap),
                        verticalAlignment = Alignment.Top,
                    ) {
                        itemsIndexed(favCols, key = { _, col -> "fav-" + col.slotKey() }) { ci, col ->
                            MetroColumn(
                                slots = col,
                                iconLoader = iconLoader,
                                wideWidth = wideWidth,
                                tileHeight = tileHeight,
                                absoluteCol = ci,
                                maxCol = maxCol,
                                isFirstColumn = ci == 0,
                                launchProgress = launchProgress,
                                appearProgress = appearProgress,
                                firstCard = firstCard,
                                topFocusRequester = topFocusRequester,
                                onAppFocused = onAppFocused,
                                onAppClicked = onAppClicked,
                                onLongPress = openMenu,
                            )
                        }
                        if (favCols.isNotEmpty() && restCols.isNotEmpty()) {
                            item(key = "gutter") { Spacer(Modifier.width(MetroGutter)) }
                        }
                        itemsIndexed(restCols, key = { _, col -> "rest-" + col.slotKey() }) { ci, col ->
                            MetroColumn(
                                slots = col,
                                iconLoader = iconLoader,
                                wideWidth = wideWidth,
                                tileHeight = tileHeight,
                                absoluteCol = favCols.size + ci,
                                maxCol = maxCol,
                                isFirstColumn = favCols.isEmpty() && ci == 0,
                                launchProgress = launchProgress,
                                appearProgress = appearProgress,
                                firstCard = firstCard,
                                topFocusRequester = topFocusRequester,
                                onAppFocused = onAppFocused,
                                onAppClicked = onAppClicked,
                                onLongPress = openMenu,
                            )
                        }
                    }
                }
            }
        }
    }

    menuApp?.let { app ->
        MetroTileMenu(
            appLabel = app.label,
            isSquare = app.packageName in squareApps,
            isFavorite = favorites.any { it.packageName == app.packageName },
            onResize = { square -> onSetTileSquare(app.packageName, square) },
            onToggleFavorite = { onToggleFavorite(app.packageName) },
            onHide = { onHideApp(app.packageName) },
            onAppInfo = { onAppInfo(app.packageName) },
            onUninstall = { onUninstall(app.packageName) },
            onDismiss = { menuApp = null },
        )
    }

    LaunchedEffect(favorites.firstOrNull()?.packageName, others.firstOrNull()?.packageName) {
        if (flatSize > 0) runCatching { firstCard.requestFocus() }
    }
}

/** A stable-ish key for a column: the first app's package. */
private fun List<MetroSlot>.slotKey(): String = when (val s = first()) {
    is MetroSlot.Wide -> s.app.packageName
    is MetroSlot.Squares -> s.a.packageName
}

/** One board column: a top-to-bottom stack of slots (wide tiles and square pairs). */
@Composable
private fun MetroColumn(
    slots: List<MetroSlot>,
    iconLoader: IconLoader,
    wideWidth: Dp,
    tileHeight: Dp,
    absoluteCol: Int,
    maxCol: Int,
    isFirstColumn: Boolean,
    launchProgress: () -> Float,
    appearProgress: () -> Float,
    firstCard: FocusRequester,
    topFocusRequester: FocusRequester?,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    // The slide/fade transform is per-column (all tiles in a column share the column's wave timing).
    val columnLayer: GraphicsLayerScope.() -> Unit = {
        val gone = maxOf(
            metroGone(appearProgress(), absoluteCol, maxCol),
            metroGone(launchProgress(), absoluteCol, maxCol),
        )
        applyMetroSlide(gone)
    }

    @Composable
    fun tile(app: AppInfo, width: Dp, up: FocusRequester?, isBoardFirst: Boolean) {
        MetroCard(
            app = app,
            iconLoader = iconLoader,
            width = width,
            height = tileHeight,
            onFocused = { onAppFocused(app.packageName) },
            onClick = { onAppClicked(app.packageName) },
            onLongClick = { onLongPress(app.packageName) },
            upFocusRequester = up,
            layer = columnLayer,
            modifier = if (isBoardFirst) Modifier.focusRequester(firstCard) else Modifier,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(MetroGap)) {
        slots.forEachIndexed { slotPos, slot ->
            // Only the top slot of each column sends D-pad UP to the top bar (settings button).
            val up = if (slotPos == 0) topFocusRequester else null
            val firstHere = isFirstColumn && slotPos == 0
            when (slot) {
                is MetroSlot.Wide -> tile(slot.app, wideWidth, up, firstHere)
                is MetroSlot.Squares -> Row(horizontalArrangement = Arrangement.spacedBy(MetroGap)) {
                    tile(slot.a, tileHeight, up, firstHere)
                    slot.b?.let { tile(it, tileHeight, up, false) }
                }
            }
        }
    }
}

/**
 * One Metro tile: a flat, sharp-cornered [width]×[height] rectangle in the app's accent color, with
 * the icon centered and the label in the bottom-left. Wide tiles are 2:1; squares are 1:1. The text
 * color flips with the tile's luminance so it stays legible on any accent. On focus the tile scales up
 * a touch and gains a white outline. Long-press opens the resize/manage menu. [layer] carries the
 * entrance/launch slide; it composes on top of the focus scale.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetroCard(
    app: AppInfo,
    iconLoader: IconLoader,
    width: Dp,
    height: Dp,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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
        onLongClick = onLongClick,
        modifier = modifier
            .size(width, height)
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
                    modifier = Modifier.align(Alignment.Center).size(height * 0.42f),
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

/**
 * Long-press menu for a Metro tile: resize (wide/square) plus the usual management actions. A modal
 * [Dialog] so D-pad focus stays on its actions; opens focused on the (non-actionable) title so the OK
 * release that long-pressed the tile can't trigger an item — press DOWN to reach the first action.
 */
@Composable
private fun MetroTileMenu(
    appLabel: String,
    isSquare: Boolean,
    isFavorite: Boolean,
    onResize: (square: Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onHide: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val titleFocus = remember { FocusRequester() }
        val colors = LocalLauncherColors.current
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.panel)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = appLabel,
                    color = colors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .focusRequester(titleFocus)
                        .focusable()
                        .padding(start = 8.dp, bottom = 10.dp),
                )
                MenuRow(
                    R.drawable.ic_swap_horiz,
                    if (isSquare) "Make wide" else "Make square",
                ) { onResize(!isSquare); onDismiss() }
                if (isFavorite) {
                    MenuRow(R.drawable.ic_star, "Remove from favorites") { onToggleFavorite(); onDismiss() }
                } else {
                    MenuRow(R.drawable.ic_star_outline, "Add to favorites") { onToggleFavorite(); onDismiss() }
                }
                MenuRow(R.drawable.ic_visibility_off, "Hide app") { onHide(); onDismiss() }
                MenuRow(R.drawable.ic_info, "App info") { onAppInfo(); onDismiss() }
                MenuRow(R.drawable.ic_delete, "Uninstall") { onUninstall(); onDismiss() }
            }
        }
        LaunchedEffect(Unit) { runCatching { titleFocus.requestFocus() } }
    }
}
