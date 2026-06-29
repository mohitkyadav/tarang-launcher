package com.tarang.launcher.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_IMAGES = 60
private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic")

/** The runtime permission needed to list the device's photos (version-dependent). */
fun imageReadPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun hasImagePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, imageReadPermission()) == PackageManager.PERMISSION_GRANTED

/** App-owned folder (no permission needed). Push images here with `adb push <img> <this path>`. */
fun wallpaperDropDir(context: Context): File =
    File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "").apply { mkdirs() }

/**
 * Pickable images: anything in the no-permission drop folder, plus the device gallery
 * ([MediaStore]) when photo access has been granted. Newest first, capped at [MAX_IMAGES].
 */
private fun loadPickableImages(context: Context): List<Uri> {
    val dropped = runCatching {
        wallpaperDropDir(context).listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXTS }
            ?.sortedByDescending { it.lastModified() }
            ?.map { Uri.fromFile(it) }
            ?: emptyList()
    }.getOrDefault(emptyList())

    val gallery = if (hasImagePermission(context)) queryMediaImages(context) else emptyList()
    return (dropped + gallery).take(MAX_IMAGES)
}

private fun queryMediaImages(context: Context): List<Uri> = runCatching {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    val out = mutableListOf<Uri>()
    context.contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && out.size < MAX_IMAGES) {
            out += ContentUris.withAppendedId(collection, cursor.getLong(idCol))
        }
    }
    out
}.getOrDefault(emptyList())

private fun decodeUriThumb(context: Context, uri: Uri, reqW: Int, reqH: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outHeight / 2 / sample >= reqH && bounds.outWidth / 2 / sample >= reqW) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
}.getOrNull()

@Composable
private fun rememberUriThumb(uri: Uri): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = uri.toString()) {
        value = withContext(Dispatchers.IO) { decodeUriThumb(context, uri, 320, 200) }
    }
    return image
}

/**
 * A modal photo browser ([Dialog] so D-pad focus stays inside it). Picking an image returns its
 * [Uri] via [onPick]; the caller copies it into app storage and sets it as the wallpaper. When no
 * images are available it explains how to add some (grant access, or push into the drop folder).
 */
@Composable
fun ImagePickerDialog(onPick: (Uri) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        var granted by remember { mutableStateOf(hasImagePermission(context)) }
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted = it }

        val images by produceState(initialValue = emptyList<Uri>(), granted) {
            value = withContext(Dispatchers.IO) { loadPickableImages(context) }
        }
        val firstFocus = remember { FocusRequester() }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .heightIn(max = 460.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF141417))
                    .padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("Choose a photo", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)

                if (images.isEmpty()) {
                    val hint = buildString {
                        append("No photos found on this device. ")
                        if (!granted) append("Grant photo access to browse your gallery, or ")
                        append("copy images into:")
                    }
                    Text(hint, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                    Text(
                        wallpaperDropDir(context).absolutePath,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                    if (!granted) {
                        ActionChip("Grant photo access", Modifier.focusRequester(firstFocus)) {
                            permLauncher.launch(imageReadPermission())
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(images.chunked(4)) { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEachIndexed { col, uri ->
                                    val mod = if (rowIndex == 0 && col == 0) {
                                        Modifier.focusRequester(firstFocus)
                                    } else {
                                        Modifier
                                    }
                                    Thumbnail(uri = uri, modifier = mod, onClick = { onPick(uri) })
                                }
                            }
                        }
                    }
                }

                Text("Press Back to cancel", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
            }
        }

        LaunchedEffect(images.isEmpty(), granted) { runCatching { firstFocus.requestFocus() } }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Thumbnail(uri: Uri, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val thumb = rememberUriThumb(uri)
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 158.dp, height = 99.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A2A2E),
            focusedContainerColor = Color(0xFF3A3A40),
        ),
    ) {
        thumb?.let {
            Image(
                bitmap = it,
                contentDescription = "Wallpaper option",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(percent = 50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A2A2E),
            focusedContainerColor = Color.White,
        ),
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
    }
}
