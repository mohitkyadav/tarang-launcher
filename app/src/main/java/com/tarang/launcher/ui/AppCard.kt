package com.tarang.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.TileArt

private val TileShape = RoundedCornerShape(24.dp)
val TileWidth = 190.dp
val TileHeight = 114.dp // 5:3, tvOS-style wide tile

/**
 * One app tile: a wide banner-artwork tile that scales up with a soft white glow on focus, and a
 * name that fades in only when focused. Apps without a banner fall back to their icon on a color.
 * Long-press pins/unpins to the dock. [upFocusRequester], when set, redirects D-pad UP (used so the
 * top row always reaches the settings button).
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
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(targetValue = if (focused) 1f else 0f, label = "labelAlpha")

    Column(
        modifier = modifier.width(TileWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .size(width = TileWidth, height = TileHeight)
                .then(
                    if (upFocusRequester != null) {
                        Modifier.focusProperties { up = upFocusRequester }
                    } else {
                        Modifier
                    },
                )
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
            shape = ClickableSurfaceDefaults.shape(TileShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF2A2A2C),
                focusedContainerColor = Color(0xFF2A2A2C),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.White, elevation = 16.dp),
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
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(TileWidth)
                .height(20.dp)
                .alpha(labelAlpha),
        )
    }
}
