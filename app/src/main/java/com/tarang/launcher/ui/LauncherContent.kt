package com.tarang.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader

private val DockShape = RoundedCornerShape(36.dp)

/**
 * tvOS-style home layout: a frosted "dock" (favorites) row on top, then the rest as a grid of rows.
 * A LazyColumn of rows (not LazyVerticalGrid) for reliable D-pad focus on TV. [topFocusRequester]
 * is where the top row sends D-pad UP (the settings button).
 *
 * Interactions: long-press a grid tile to pin it to the dock ([onToggleFavorite]); long-press a dock
 * tile to "lift" it into move mode and rearrange/remove it (committed via [onReorder]).
 * [onAppBounds] reports the focused tile's rect for the launch transition.
 */
@Composable
fun LauncherContent(
    dockApps: List<AppInfo>,
    gridApps: List<AppInfo>,
    iconLoader: IconLoader,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    topFocusRequester: FocusRequester? = null,
    onAppBounds: (Rect) -> Unit = {},
) {
    val gridRows = remember(gridApps) { gridApps.chunked(COLUMNS) }
    val firstCard = remember { FocusRequester() }
    val hasDock = dockApps.isNotEmpty()

    // Move mode operates on a local working copy of the dock; it's committed (or abandoned) on exit.
    var movingPackage by remember { mutableStateOf<String?>(null) }
    var workingDock by remember(dockApps) { mutableStateOf(dockApps) }
    val shownDock = if (movingPackage != null) workingDock else dockApps

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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 74.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (hasDock) {
                item(key = "dock") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DockShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), DockShape)
                            .padding(20.dp),
                    ) {
                        AppRow(
                            apps = shownDock,
                            iconLoader = iconLoader,
                            onAppFocused = onAppFocused,
                            onAppClicked = onAppClicked,
                            onAppLongPressed = { movingPackage = it }, // lift into move mode
                            firstCardFocusRequester = firstCard,
                            upFocusRequester = topFocusRequester,
                            onAppBounds = onAppBounds,
                            movingPackage = movingPackage,
                            onMove = ::moveBy,
                            onRemoveFromDock = ::removeMoving,
                            onCommitMove = ::commitMove,
                        )
                    }
                }
            }
            itemsIndexed(gridRows, key = { _, row -> row.first().packageName }) { index, row ->
                AppRow(
                    apps = row,
                    iconLoader = iconLoader,
                    onAppFocused = onAppFocused,
                    onAppClicked = onAppClicked,
                    onAppLongPressed = onToggleFavorite, // long-press a grid tile to pin it
                    firstCardFocusRequester = if (!hasDock && index == 0) firstCard else null,
                    // Only the very top row sends UP to the settings button.
                    upFocusRequester = if (!hasDock && index == 0) topFocusRequester else null,
                    onAppBounds = onAppBounds,
                )
            }
        }

        if (movingPackage != null) {
            MoveHint(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
    }

    val firstPackage = dockApps.firstOrNull()?.packageName ?: gridApps.firstOrNull()?.packageName
    LaunchedEffect(firstPackage) {
        if (firstPackage != null) runCatching { firstCard.requestFocus() }
    }
}

@Composable
private fun MoveHint(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(
            text = "←  →  Move      ↑  Remove      OK  Done",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
        )
    }
}

private const val COLUMNS = 4
