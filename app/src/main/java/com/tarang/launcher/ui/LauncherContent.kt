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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader

private val DockShape = RoundedCornerShape(36.dp)

/**
 * tvOS-style home layout: a frosted "dock" (favorites) row on top, then the rest as a grid of rows.
 * A LazyColumn of rows (not LazyVerticalGrid) for reliable D-pad focus on TV. [topFocusRequester]
 * is where the top row sends D-pad UP (the settings button). Long-press pins/unpins a tile.
 */
@Composable
fun LauncherContent(
    dockApps: List<AppInfo>,
    gridApps: List<AppInfo>,
    iconLoader: IconLoader,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
    topFocusRequester: FocusRequester? = null,
) {
    val gridRows = remember(gridApps) { gridApps.chunked(COLUMNS) }
    val firstCard = remember { FocusRequester() }
    val hasDock = dockApps.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 56.dp),
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
                        apps = dockApps,
                        iconLoader = iconLoader,
                        onAppFocused = onAppFocused,
                        onAppClicked = onAppClicked,
                        onToggleFavorite = onToggleFavorite,
                        firstCardFocusRequester = firstCard,
                        upFocusRequester = topFocusRequester,
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
                onToggleFavorite = onToggleFavorite,
                firstCardFocusRequester = if (!hasDock && index == 0) firstCard else null,
                // Only the very top row sends UP to the settings button.
                upFocusRequester = if (!hasDock && index == 0) topFocusRequester else null,
            )
        }
    }

    val firstPackage = dockApps.firstOrNull()?.packageName ?: gridApps.firstOrNull()?.packageName
    LaunchedEffect(firstPackage) {
        if (firstPackage != null) runCatching { firstCard.requestFocus() }
    }
}

private const val COLUMNS = 4
