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
 * A single horizontal row of [AppCard]s. Rows are stacked in a LazyColumn (see [LauncherContent]).
 * [upFocusRequester], when set, makes every card in the row redirect D-pad UP there (used so the
 * top row reaches the settings button regardless of which tile is focused).
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
    upFocusRequester: FocusRequester? = null,
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
                upFocusRequester = upFocusRequester,
                modifier = if (index == 0 && firstCardFocusRequester != null) {
                    Modifier.focusRequester(firstCardFocusRequester)
                } else {
                    Modifier
                },
            )
        }
    }
}
