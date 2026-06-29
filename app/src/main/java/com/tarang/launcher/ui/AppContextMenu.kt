package com.tarang.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.R

/**
 * Long-press menu for an app tile: favorite/unfavorite (and reorder, for dock apps). A modal
 * [Dialog] so D-pad focus stays on its actions. Back closes it.
 *
 * Opens with focus on the (non-actionable) title rather than the first action, so the OK release
 * that long-pressed the tile can't trigger an item — press DOWN to reach the first action.
 */
@Composable
fun AppContextMenu(
    appLabel: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMove: () -> Unit,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onHide: (() -> Unit)? = null,
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

                if (isFavorite) {
                    MenuRow(R.drawable.ic_star, "Remove from favorites") { onToggleFavorite(); onDismiss() }
                    MenuRow(R.drawable.ic_swap_horiz, "Move") { onMove() }
                } else {
                    MenuRow(R.drawable.ic_star_outline, "Add to favorites") { onToggleFavorite(); onDismiss() }
                    onHide?.let { hide ->
                        MenuRow(R.drawable.ic_visibility_off, "Hide app") { hide(); onDismiss() }
                    }
                }
                // App management, available for every app.
                MenuRow(R.drawable.ic_info, "App info") { onAppInfo(); onDismiss() }
                MenuRow(R.drawable.ic_delete, "Uninstall") { onUninstall(); onDismiss() }
            }
        }
        LaunchedEffect(Unit) { runCatching { titleFocus.requestFocus() } }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuRow(iconRes: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalLauncherColors.current
    val tint = if (focused) colors.onHighlight else colors.text
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.chip,
            focusedContainerColor = colors.highlight,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(tint),
            )
            Text(label, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}
