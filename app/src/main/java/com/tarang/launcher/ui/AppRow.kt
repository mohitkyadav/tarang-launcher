package com.tarang.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader

/**
 * A single horizontal row of [AppCard]s. Rows are stacked in a LazyColumn (see [LauncherContent]).
 * [upFocusRequester], when set, makes every card redirect D-pad UP there (so the top row reaches
 * the settings button).
 *
 * When [movingPackage] is set the row is in "move mode": the lifted tile keeps focus and D-pad
 * LEFT/RIGHT reorder it ([onMove]), UP removes it from the dock ([onRemoveFromDock]) and
 * OK/Back/DOWN commit ([onCommitMove]). These keys are caught on the row (it's the focus ancestor
 * of the lifted card) before normal focus traversal happens.
 */
@Composable
fun AppRow(
    apps: List<AppInfo>,
    iconLoader: IconLoader,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    onAppLongPressed: (String) -> Unit,
    tileWidth: Dp,
    tileHeight: Dp,
    modifier: Modifier = Modifier,
    firstCardFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    reduceMotion: Boolean = false,
    movingPackage: String? = null,
    onMove: (Int) -> Unit = {},
    onRemoveFromDock: () -> Unit = {},
    onCommitMove: () -> Unit = {},
) {
    val moveFocus = remember { FocusRequester() }
    val movingIndex = apps.indexOfFirst { it.packageName == movingPackage }

    // Keep focus on the lifted tile as it changes position during a reorder.
    LaunchedEffect(movingPackage, movingIndex) {
        if (movingIndex >= 0) runCatching { moveFocus.requestFocus() }
    }

    Row(
        modifier = modifier.then(
            if (movingPackage != null) {
                Modifier.onPreviewKeyEvent { e -> handleMoveKey(e, onMove, onRemoveFromDock, onCommitMove) }
            } else {
                Modifier
            },
        ),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        apps.forEachIndexed { index, app ->
            val isMoving = app.packageName == movingPackage
            val focusRequester = when {
                isMoving -> moveFocus
                index == 0 -> firstCardFocusRequester
                else -> null
            }
            AppCard(
                app = app,
                iconLoader = iconLoader,
                onFocused = { onAppFocused(app.packageName) },
                onClick = { onAppClicked(app.packageName) },
                onLongClick = { onAppLongPressed(app.packageName) },
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                upFocusRequester = upFocusRequester,
                reduceMotion = reduceMotion,
                isMoving = isMoving,
                dimmed = movingPackage != null && !isMoving,
                modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            )
        }
    }
}

private fun handleMoveKey(
    e: KeyEvent,
    onMove: (Int) -> Unit,
    onRemoveFromDock: () -> Unit,
    onCommitMove: () -> Unit,
): Boolean {
    val down = e.type == KeyEventType.KeyDown
    return when (e.key) {
        Key.DirectionLeft -> { if (down) onMove(-1); true }
        Key.DirectionRight -> { if (down) onMove(1); true }
        Key.DirectionUp -> { if (down) onRemoveFromDock(); true }
        // Commit OK on key-UP (and swallow both edges) so the press doesn't fall through to the
        // tile's click handler and launch the app once move mode exits.
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (!down) onCommitMove(); true }
        Key.DirectionDown, Key.Back -> { if (down) onCommitMove(); true }
        else -> false
    }
}
