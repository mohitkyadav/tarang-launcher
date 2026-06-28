package com.tarang.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.TileArt

private val TileShape = RoundedCornerShape(24.dp)
private val FocusBorderColor = Color(0xFF67E8F9) // cool cyan — focus outline
val TileWidth = 190.dp
val TileHeight = 114.dp // 5:3, tvOS-style wide tile

/**
 * One app tile: a wide banner-artwork tile that, on focus, scales up slightly and gains a cool
 * cyan outline. Apps without a banner fall back to their icon on a color. The app name is no longer
 * shown (the artwork makes it clear) but is kept as the content description for accessibility.
 * Long-press pins/unpins to the dock. [upFocusRequester], when set, redirects D-pad UP.
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
) {
    val tile: TileArt? by produceState<TileArt?>(initialValue = null, app.packageName) {
        value = iconLoader.loadTile(app)
    }

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(width = TileWidth, height = TileHeight)
            .then(
                if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier,
            )
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A2A2C),
            focusedContainerColor = Color(0xFF2A2A2C),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, FocusBorderColor), shape = TileShape),
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
