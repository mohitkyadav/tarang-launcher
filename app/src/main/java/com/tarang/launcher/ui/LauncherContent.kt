package com.tarang.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader

/**
 * tvOS-style home layout: a "dock" (favorites) row on top, then the remaining apps as a grid of
 * rows. Implemented as a LazyColumn of rows rather than LazyVerticalGrid for reliable D-pad focus
 * traversal on TV. Long-pressing a tile pins/unpins it (handled upstream via [onToggleFavorite]).
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
) {
    val gridRows = remember(gridApps) { gridApps.chunked(COLUMNS) }
    val firstCard = remember { FocusRequester() }
    val hasDock = dockApps.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        if (hasDock) {
            item(key = "dock") {
                AppRow(
                    apps = dockApps,
                    iconLoader = iconLoader,
                    onAppFocused = onAppFocused,
                    onAppClicked = onAppClicked,
                    onToggleFavorite = onToggleFavorite,
                    firstCardFocusRequester = firstCard,
                )
            }
        }
        itemsIndexed(gridRows, key = { _, row -> row.first().packageName }) { index, row ->
            AppRow(
                apps = row,
                iconLoader = iconLoader,
                onAppFocused = onAppFocused,
                onAppClicked = onAppClicked,
                onToggleFavorite = onToggleFavorite,
                // If there is no dock, land initial focus on the first grid row instead.
                firstCardFocusRequester = if (!hasDock && index == 0) firstCard else null,
            )
        }
    }

    val firstPackage = dockApps.firstOrNull()?.packageName ?: gridApps.firstOrNull()?.packageName
    LaunchedEffect(firstPackage) {
        if (firstPackage != null) runCatching { firstCard.requestFocus() }
    }
}

private const val COLUMNS = 6
