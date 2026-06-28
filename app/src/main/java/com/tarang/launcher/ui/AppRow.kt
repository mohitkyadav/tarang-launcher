package com.tarang.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader

/**
 * A single horizontal row of [AppCard]s. Rows are stacked in a LazyColumn (see
 * [LauncherContent]); left/right moves within a row, up/down moves between rows.
 */
@Composable
fun AppRow(
    apps: List<AppInfo>,
    iconLoader: IconLoader,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
    firstCardFocusRequester: FocusRequester? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        apps.forEachIndexed { index, app ->
            AppCard(
                app = app,
                iconLoader = iconLoader,
                onFocused = { onAppFocused(app.packageName) },
                onClick = { onAppClicked(app.packageName) },
                onLongClick = { onToggleFavorite(app.packageName) },
                modifier = if (index == 0 && firstCardFocusRequester != null) {
                    Modifier.focusRequester(firstCardFocusRequester)
                } else {
                    Modifier
                },
            )
        }
    }
}
