package com.tarang.launcher.ui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A device photo folder (MediaStore bucket): its id, display name, photo count, and a cover image. */
data class ImageFolder(val id: String, val name: String, val count: Int, val cover: Uri)

/**
 * Folders (MediaStore buckets) that contain images, each with its newest photo as a cover and a
 * count. We group the gallery by bucket ourselves rather than launch the system document picker,
 * which is unreliable on TV — same approach as [ImagePickerDialog]. Sorted by photo count, desc.
 */
private fun loadImageFolders(context: Context): List<ImageFolder> {
    if (!hasImagePermission(context)) return emptyList()
    return runCatching {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        // Preserve newest-first so the first photo seen per bucket becomes its cover.
        val folders = LinkedHashMap<String, MutableFolder>()
        context.contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(bucketCol) ?: continue
                val name = cursor.getString(nameCol) ?: "Unknown"
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                val f = folders.getOrPut(bucketId) { MutableFolder(bucketId, name, uri) }
                f.count++
            }
        }
        folders.values.map { ImageFolder(it.id, it.name, it.count, it.cover) }
            .sortedByDescending { it.count }
    }.getOrDefault(emptyList())
}

private class MutableFolder(val id: String, val name: String, val cover: Uri, var count: Int = 0)

/**
 * A modal folder browser ([Dialog] so D-pad focus stays inside it). Picking a folder returns its
 * bucket id + display name via [onPick]; the caller stores it as the Frame Art source.
 */
@Composable
fun FolderPickerDialog(onPick: (id: String, name: String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        var granted by remember { mutableStateOf(hasImagePermission(context)) }
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted = it }

        val folders by produceState(initialValue = emptyList<ImageFolder>(), granted) {
            value = withContext(Dispatchers.IO) { loadImageFolders(context) }
        }
        val firstFocus = remember { FocusRequester() }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .heightIn(max = 500.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF141417))
                    .padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("Choose a folder", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)

                if (folders.isEmpty()) {
                    val hint = buildString {
                        append("No photo folders found. ")
                        if (!granted) append("Grant photo access, or ")
                        append("add images to a folder on the device:")
                    }
                    Text(hint, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                    Text(picturesPathLabel(), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                    if (!granted) {
                        FolderActionChip("Grant photo access", Modifier.focusRequester(firstFocus)) {
                            permLauncher.launch(imageReadPermission())
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(folders) { i, folder ->
                            val mod = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
                            FolderRow(folder = folder, modifier = mod) { onPick(folder.id, folder.name) }
                        }
                    }
                }

                Text("Press Back to cancel", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
            }
        }

        LaunchedEffect(folders.isEmpty(), granted) { runCatching { firstFocus.requestFocus() } }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FolderRow(folder: ImageFolder, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val thumb = rememberUriThumb(folder.cover)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A2A2E),
            focusedContainerColor = Color(0xFF3A3A40),
        ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 96.dp, height = 60.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                thumb?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    "${folder.count} ${if (folder.count == 1) "photo" else "photos"}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FolderActionChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
