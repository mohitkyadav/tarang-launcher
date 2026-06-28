package com.tarang.launcher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.TileArt

private val TileShape = RoundedCornerShape(24.dp)
val TileWidth = 190.dp
val TileHeight = 114.dp // 5:3, tvOS-style wide tile

/**
 * One app tile: a wide banner-artwork tile that scales up on focus and lifts with a soft shadow
 * (no border). Apps without a banner fall back to their icon on a color. Long-press pins/unpins
 * (grid) or lifts the tile into move mode (dock).
 *
 * [onBoundsChanged] reports the tile's on-screen rect while focused, so the launch transition can
 * zoom out from exactly where the tile sits. In move mode, [isMoving] lifts the tile a little more
 * and [dimmed] fades the tiles that aren't being moved.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppCard(
    app: AppInfo,
    iconLoader: IconLoader,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
    onBoundsChanged: (Rect) -> Unit = {},
    isMoving: Boolean = false,
    dimmed: Boolean = false,
) {
    val tile: TileArt? by produceState<TileArt?>(initialValue = null, app.packageName) {
        value = iconLoader.loadTile(app)
    }

    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focused by remember { mutableStateOf(false) }

    val elevation by animateDpAsState(
        targetValue = when {
            isMoving -> 16.dp
            focused -> 8.dp
            else -> 0.dp
        },
        label = "tileElevation",
    )

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(width = TileWidth, height = TileHeight)
            .alpha(if (dimmed) 0.4f else 1f)
            .shadow(elevation = elevation, shape = TileShape, clip = false)
            .then(
                if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier,
            )
            .onGloballyPositioned { coords = it }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                    coords?.takeIf { c -> c.isAttached }?.let { c -> onBoundsChanged(c.boundsInRoot()) }
                }
            },
        shape = ClickableSurfaceDefaults.shape(TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (isMoving) 1.12f else 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A2A2C),
            focusedContainerColor = Color(0xFF2A2A2C),
        ),
    ) {
        when (val art = tile) {
            is TileArt.Banner -> Image(
                bitmap = art.image,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            is TileArt.Fallback -> Box(
                modifier = Modifier.fillMaxSize().background(art.color),
                contentAlignment = Alignment.Center,
            ) {
                art.icon?.let {
                    Image(bitmap = it, contentDescription = app.label, modifier = Modifier.size(56.dp))
                }
            }

            null -> Box(modifier = Modifier.fillMaxSize())
        }
    }
}
