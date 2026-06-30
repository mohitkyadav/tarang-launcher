package com.tarang.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.TileArt

/** Tiles are 5:3 (tvOS-style wide). The actual size is computed from the column count. */
const val TILE_ASPECT = 0.6f

/**
 * One app tile: a wide banner-artwork tile that scales up on focus and lifts with a soft shadow
 * (no border). Apps without a banner fall back to their icon on a color. Long-press pins/unpins
 * (grid) or lifts the tile into move mode (dock). In move mode, [isMoving] lifts the tile a little
 * more and [dimmed] fades the tiles that aren't being moved.
 *
 * [tileWidth]/[tileHeight] are supplied by the layout so the grid fills the row at any column count;
 * the corner radius and fallback-icon size scale with the tile so small tiles still look right.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppCard(
    app: AppInfo,
    iconLoader: IconLoader,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    tileWidth: Dp,
    tileHeight: Dp,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
    isMoving: Boolean = false,
    dimmed: Boolean = false,
    reduceMotion: Boolean = false,
) {
    val tile: TileArt? by produceState<TileArt?>(initialValue = null, app.packageName) {
        value = iconLoader.loadTile(app)
    }
    val tileShape = RoundedCornerShape(tileHeight * 0.21f)

    var focused by remember { mutableStateOf(false) }

    val elevation by animateDpAsState(
        targetValue = when {
            isMoving -> 16.dp
            focused -> 8.dp
            else -> 0.dp
        },
        label = "tileElevation",
    )
    // Drive the focus scale ourselves (the TV Surface's built-in scale animates too slowly): a snappy
    // spring up to a slightly larger size than before.
    val scale by animateFloatAsState(
        targetValue = when {
            isMoving -> 1.14f
            focused -> 1.12f
            else -> 1f
        },
        // Reduce motion: snap to the focused size instead of springing into it.
        animationSpec = if (reduceMotion) snap() else
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "tileScale",
    )

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(width = tileWidth, height = tileHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (dimmed) 0.4f else 1f)
            .shadow(elevation = elevation, shape = tileShape, clip = false)
            .then(
                if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = ClickableSurfaceDefaults.shape(tileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
                    Image(bitmap = it, contentDescription = app.label, modifier = Modifier.size(tileHeight * 0.5f))
                }
            }

            null -> Box(modifier = Modifier.fillMaxSize())
        }
    }
}
