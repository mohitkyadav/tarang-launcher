package com.tarang.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DockShape = RoundedCornerShape(36.dp)
private val SidePad = 56.dp // horizontal screen margin (matches the top bar)
private val TileGap = 24.dp // gap between tiles in a row (matches AppRow's arrangement)
private val DockPad = 20.dp // inner padding of the frosted dock container
// Resting bottom margin for the dock — kept ≤ the inter-row spacing so the first grid row stays just
// off-screen until the user presses DOWN. The top gap is computed from the available height (see
// below) so the dock sits near the bottom and the grid is fully below the fold (Apple-TV style). The
// bring-into-view spec won't scroll the already-visible dock, so it stays put until you move down.
private val DockBottomGap = 24.dp
private val MinTopGap = 84.dp // floor for the computed top gap on very short viewports

/**
 * Minimal bring-into-view: don't move an item that's already fully visible (keeps the dock low on
 * focus), and otherwise scroll the least amount to reveal it (so the grid scrolls up cleanly). The
 * default TV behaviour over-scrolls, pulling the dock up and eating the top gap.
 */
// A focused tile scales up ~6–7% via graphicsLayer, and Compose's bring-into-view reads the focused
// node's *transformed* (scaled) bounds. So the first Left/Right move into a row scrolls a few px to
// re-fit the scaled tile against the edge — a visible one-time nudge. [slop] is sized to that halo
// (see the call site) so an already-visible row is left alone and never re-fits.
@OptIn(ExperimentalFoundationApi::class)
internal fun minimalBringIntoView(slop: Float): BringIntoViewSpec = object : BringIntoViewSpec {
    private val inset = 24f // keep a focused row a hair off the screen edges (covers the focus scale)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leading = offset - inset
        val trailing = offset + size + inset
        return when {
            leading >= -slop && trailing <= containerSize + slop -> 0f // already visible (within slop): don't move
            size + inset * 2 > containerSize -> 0f // taller than the viewport: leave it
            abs(leading) < abs(trailing - containerSize) -> leading
            else -> trailing - containerSize
        }
    }
}

/**
 * tvOS-style home layout: a frosted "dock" (favorites) row on top, then the rest as a grid of rows.
 * A LazyColumn of rows (not LazyVerticalGrid) for reliable D-pad focus on TV. [topFocusRequester]
 * is where the top row sends D-pad UP (the settings button).
 *
 * Interactions: long-press a grid tile to pin it to the dock ([onToggleFavorite]); long-press a dock
 * tile to "lift" it into move mode and rearrange/remove it (committed via [onReorder]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherContent(
    dockApps: List<AppInfo>,
    gridApps: List<AppInfo>,
    iconLoader: IconLoader,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    columns: Int,
    backdrop: GraphicsLayer,
    modifier: Modifier = Modifier,
    // DEPTH launch ripple across the dock tiles (null for other styles / grid launches).
    dockRipple: DockRipple? = null,
    topFocusRequester: FocusRequester? = null,
    onFavoriteHover: (String?) -> Unit = {},
    onHideApp: (String) -> Unit = {},
    onAppInfo: (String) -> Unit = {},
    onUninstall: (String) -> Unit = {},
    glassLive: Boolean = true,
    glassRefract: Boolean = true,
    glassBlur: Boolean = true,
) {
    val gridRows = remember(gridApps, columns) { gridApps.chunked(columns) }
    val firstCard = remember { FocusRequester() }
    val hasDock = dockApps.isNotEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val colors = LocalLauncherColors.current

    // Move mode operates on a local working copy of the dock; it's committed (or abandoned) on exit.
    var movingPackage by remember { mutableStateOf<String?>(null) }
    var workingDock by remember(dockApps) { mutableStateOf(dockApps) }
    val shownDock = if (movingPackage != null) workingDock else dockApps

    // Long-press opens a context menu for the app (favorite/unfavorite, and reorder for dock apps).
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    val openMenu: (String) -> Unit = { pkg ->
        menuApp = (dockApps + gridApps).firstOrNull { it.packageName == pkg }
    }

    fun moveBy(dir: Int) {
        val cur = workingDock.indexOfFirst { it.packageName == movingPackage }
        val target = cur + dir
        if (cur >= 0 && target in workingDock.indices) {
            workingDock = workingDock.toMutableList().also { it.add(target, it.removeAt(cur)) }
        }
    }
    fun commitMove() {
        onReorder(workingDock.map { it.packageName })
        movingPackage = null
    }
    fun removeMoving() {
        movingPackage?.let { onToggleFavorite(it) } // toggle off = unpin from the dock
        movingPackage = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Size tiles to fill the row at the chosen column count (the grid stretches edge-to-edge). The
        // dock tiles match the grid tile width so favorites sit on the same columns — until there are
        // more favorites than columns, when they shrink together to keep all of them on one row.
        val availWidth = maxWidth - SidePad * 2
        val gridTileW = (availWidth - TileGap * (columns - 1)) / columns
        val gridTileH = gridTileW * TILE_ASPECT
        val dockCount = shownDock.size.coerceAtLeast(1)
        val dockTileW = minOf(gridTileW, (availWidth - TileGap * (dockCount - 1)) / dockCount)
        val dockTileH = dockTileW * TILE_ASPECT

        // Drop the dock to the bottom so ONLY it shows at rest; the grid sits fully below the fold and
        // slides up when the user moves down into it.
        val dockBlockH = dockTileH + DockPad * 2
        val topGap = (maxHeight - dockBlockH - DockBottomGap).coerceAtLeast(MinTopGap)

        // Size the bring-into-view slop to the focus scale-halo: a focused/moving tile grows up to ~7%,
        // so it spills ~0.07·tileH past its layout box on each edge. Absorbing that stops the one-time
        // re-fit nudge when moving Left/Right into a row. The resting position is still set by the
        // vertical scroll that bottom-aligns the row, so the scaled tile keeps its margin (no clipping).
        val density = LocalDensity.current
        val bringIntoView = remember(gridTileH, density) {
            minimalBringIntoView(with(density) { gridTileH.toPx() } * 0.07f + 3f)
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // No horizontal content padding: the grid rows and the dock apply their own side insets, so
            // the dock's frosted bar can reach a DockPad past the grid margin (see the dock item below).
            contentPadding = PaddingValues(top = topGap, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (hasDock) {
                item(key = "dock") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Inset to one DockPad short of the grid margin, so the frosted bar always
                            // spans a full grid row PLUS a DockPad of breathing room bleeding into each
                            // side margin — regardless of how few favorites there are. The inner DockPad
                            // padding then lands the tiles exactly on the grid columns.
                            .padding(horizontal = SidePad - DockPad)
                            // When focus returns to the dock, scroll back so the layout sits at the
                            // top. When focus leaves the dock entirely, clear the artwork hover.
                            .onFocusChanged {
                                if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } else onFavoriteHover(null)
                            },
                    ) {
                        // The frosted bar sits BEHIND the tiles as a sibling, not as their container:
                        // frostedGlass clips to the bar shape, and the launch ripple lifts tiles above
                        // the bar — as a parent it would crop them at the dock border.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                // No per-app accent on the dock: it kept re-tinting on every hover (a
                                // visible flicker + redraw). A stable chrome tint keeps the dock calm.
                                .frostedGlass(backdrop, DockShape, tint = if (glassBlur) colors.chrome else colors.chromeOpaque, live = glassLive, refract = glassRefract, blur = glassBlur && glassLive),
                        )
                        Box(modifier = Modifier.padding(DockPad)) {
                        AppRow(
                            apps = shownDock,
                            iconLoader = iconLoader,
                            ripple = dockRipple,
                            // Dock = favorites: report which one is hovered so its artwork can play.
                            onAppFocused = { onAppFocused(it); onFavoriteHover(it) },
                            onAppClicked = onAppClicked,
                            onAppLongPressed = openMenu, // long-press a dock tile -> context menu
                            tileWidth = dockTileW,
                            tileHeight = dockTileH,
                            firstCardFocusRequester = firstCard,
                            // UP from the dock goes to the top bar (settings button).
                            upFocusRequester = topFocusRequester,
                            movingPackage = movingPackage,
                            onMove = ::moveBy,
                            onRemoveFromDock = ::removeMoving,
                            onCommitMove = ::commitMove,
                        )
                        }
                    }
                }
            }
            itemsIndexed(gridRows, key = { _, row -> row.first().packageName }) { index, row ->
                AppRow(
                    apps = row,
                    iconLoader = iconLoader,
                    onAppFocused = onAppFocused,
                    onAppClicked = onAppClicked,
                    onAppLongPressed = openMenu, // long-press a grid tile -> context menu
                    tileWidth = gridTileW,
                    tileHeight = gridTileH,
                    modifier = Modifier.padding(horizontal = SidePad), // own side inset (no list padding)
                    firstCardFocusRequester = if (!hasDock && index == 0) firstCard else null,
                    // Only the very top row sends UP to the settings button.
                    upFocusRequester = if (!hasDock && index == 0) topFocusRequester else null,
                )
            }
        }
        }

        if (movingPackage != null) {
            MoveHint(colors = colors, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
    }

    menuApp?.let { app ->
        AppContextMenu(
            appLabel = app.label,
            isFavorite = dockApps.any { it.packageName == app.packageName },
            onToggleFavorite = { onToggleFavorite(app.packageName) },
            onMove = {
                movingPackage = app.packageName // enter move mode for this dock tile
                menuApp = null
            },
            onDismiss = { menuApp = null },
            onAppInfo = { onAppInfo(app.packageName) },
            onUninstall = { onUninstall(app.packageName) },
            onHide = { onHideApp(app.packageName) }, // grid-only (menu hides it for favorites)
        )
    }

    val firstPackage = dockApps.firstOrNull()?.packageName ?: gridApps.firstOrNull()?.packageName
    LaunchedEffect(firstPackage) {
        if (firstPackage != null) runCatching { firstCard.requestFocus() }
    }
}

@Composable
private fun MoveHint(colors: LauncherColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.panel)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(
            text = "←  →  Move      ↑  Remove      OK  Done",
            color = colors.text,
            fontSize = 14.sp,
        )
    }
}
