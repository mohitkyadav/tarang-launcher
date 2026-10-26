package com.tarang.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.tarang.launcher.media.TarangNotificationListenerService
import kotlinx.coroutines.channels.Channel

/** A snapshot of whatever's currently playing, for the top-bar chip. */
data class NowPlaying(val title: String, val artist: String?, val art: ImageBitmap?, val packageName: String)

/**
 * Whether two snapshots would RENDER the same chip. Each poll builds a fresh object (with a fresh
 * [ImageBitmap] wrapper), so plain equality is always false — publishing that would recompose the
 * top bar every 2.5s while music plays. Art is compared by presence only: same track + art still
 * there means nothing visible changed.
 */
private fun NowPlaying?.sameDisplay(other: NowPlaying?): Boolean = when {
    this == null || other == null -> (this == null) == (other == null)
    else -> title == other.title && artist == other.artist && packageName == other.packageName &&
        (art == null) == (other.art == null)
}

/**
 * Watches the active media sessions while the launcher is visible and [enabled], returning the
 * currently *playing* session (or null — including when notification access hasn't been granted,
 * since reading sessions then throws and is swallowed).
 *
 * Event-driven: it re-reads only when the session list, a playback state or the metadata changes,
 * instead of waking every 2.5 s. Without notification access the listener can't be registered and
 * the chip stays empty; granting access happens in system Settings, and coming back restarts this
 * block (the launcher goes STOPPED → STARTED), which registers it then.
 */
@Composable
fun rememberNowPlaying(enabled: Boolean): NowPlaying? {
    val context = LocalContext.current
    var np by remember { mutableStateOf<NowPlaying?>(null) }
    RunWhileStarted(enabled) {
        if (!enabled) return@RunWhileStarted
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return@RunWhileStarted
        val component = ComponentName(context, TarangNotificationListenerService::class.java)
        val main = Handler(Looper.getMainLooper())
        val changed = Channel<Unit>(Channel.CONFLATED)
        val controllerCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { changed.trySend(Unit) }
            override fun onMetadataChanged(metadata: MediaMetadata?) { changed.trySend(Unit) }
            override fun onSessionDestroyed() { changed.trySend(Unit) }
        }
        var watched: List<MediaController> = emptyList()
        fun watch(controllers: List<MediaController>) {
            watched.forEach { it.unregisterCallback(controllerCallback) }
            watched = controllers
            controllers.forEach { it.registerCallback(controllerCallback, main) }
            changed.trySend(Unit)
        }
        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it.orEmpty()) }
        val listening = runCatching { msm.addOnActiveSessionsChangedListener(sessionsListener, component, main) }.isSuccess
        if (!listening) {
            np = null
            return@RunWhileStarted
        }
        try {
            watch(runCatching { msm.getActiveSessions(component) }.getOrDefault(emptyList()))
            while (true) {
                changed.receive()
                val fresh = readNowPlaying(msm, component)
                if (!fresh.sameDisplay(np)) np = fresh
            }
        } finally {
            runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
            watched.forEach { it.unregisterCallback(controllerCallback) }
        }
    }
    return if (enabled) np else null
}

private fun readNowPlaying(msm: MediaSessionManager, component: ComponentName): NowPlaying? = runCatching {
    val controllers: List<MediaController> = msm.getActiveSessions(component)
    // Only surface something that's actually playing, so the chip stays relevant and disappears on pause.
    val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: return null
    val md = playing.metadata
    val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
    val art = (
        md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        )?.asImageBitmap()
    NowPlaying(
        title = title,
        artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
        art = art,
        packageName = playing.packageName,
    )
}.getOrNull()

/** Whether Tarang's notification listener is enabled (required to read media sessions). */
fun isNotificationAccessGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** Intent action for the system's notification-access settings screen. */
const val NOTIFICATION_ACCESS_ACTION: String = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
