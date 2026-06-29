package com.tarang.launcher.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

/** Permission a launcher needs to read other apps' home-screen rows (declared in the manifest). */
const val TV_LISTINGS_PERMISSION = "android.permission.READ_TV_LISTINGS"

/** One sampled preview program, with flags for the assets a carousel would need. */
data class TvProbeRow(
    val pkg: String,
    val title: String,
    val poster: Boolean,
    val video: Boolean,
    val intent: Boolean,
)

/** What the [TvContentProbe] found — used to decide if a content carousel is even feasible here. */
data class TvProbeResult(
    val permissionGranted: Boolean,
    val previewChannels: Int,
    val previewPrograms: Int,
    val watchNext: Int,
    val perPackage: List<Pair<String, Int>>,
    val samples: List<TvProbeRow>,
    /** True only if reading preview programs itself was denied — the real "can't do it" signal. */
    val blocked: Boolean,
    val error: String?,
)

/**
 * Diagnostic: queries the system TvProvider for the home-screen channels/programs that content apps
 * publish (the data a recommendation carousel would render). The point is to learn, on a real
 * device, whether a sideloaded launcher can actually read other packages' rows — or whether it's
 * blocked (SecurityException) or simply empty (Google TV serves these server-side).
 *
 * Uses the raw provider URIs + column names (verified queryable) to avoid pulling in the tvprovider
 * library for a throwaway probe.
 */
object TvContentProbe {
    private val CHANNELS = Uri.parse("content://android.media.tv/channel")
    private val PREVIEW = Uri.parse("content://android.media.tv/preview_program")
    private val WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program")

    fun run(context: Context): TvProbeResult {
        val granted = ContextCompat.checkSelfPermission(
            context,
            TV_LISTINGS_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED

        val cr = context.contentResolver
        var error: String? = null
        fun note(e: Exception, where: String) {
            if (error == null) error = "$where: ${e.javaClass.simpleName}: ${e.message}"
        }

        // Query channels with no selection (a WHERE clause is rejected for unprivileged callers),
        // then count the preview-type rows in code.
        val previewChannels = try {
            cr.query(CHANNELS, arrayOf("package_name", "type"), null, null, null)?.use { c ->
                val ti = c.getColumnIndex("type")
                var n = 0
                while (c.moveToNext()) if (ti >= 0 && c.getString(ti) == "TYPE_PREVIEW") n++
                n
            } ?: -1
        } catch (e: Exception) {
            note(e, "channel"); -1
        }

        val watchNext = try {
            cr.query(WATCH_NEXT, arrayOf("package_name"), null, null, null)?.use { it.count } ?: -1
        } catch (e: Exception) {
            note(e, "watch_next"); -1
        }

        val perPackage = linkedMapOf<String, Int>()
        val samples = mutableListOf<TvProbeRow>()
        var previewPrograms = -1
        var blocked = false
        try {
            cr.query(
                PREVIEW,
                arrayOf("package_name", "title", "poster_art_uri", "preview_video_uri", "intent_uri"),
                null, null, null,
            )?.use { c ->
                previewPrograms = c.count
                val pi = c.getColumnIndex("package_name")
                val ti = c.getColumnIndex("title")
                val poi = c.getColumnIndex("poster_art_uri")
                val vi = c.getColumnIndex("preview_video_uri")
                val ii = c.getColumnIndex("intent_uri")
                while (c.moveToNext()) {
                    val pkg = (pi.takeIf { it >= 0 }?.let { c.getString(it) }) ?: "?"
                    perPackage[pkg] = (perPackage[pkg] ?: 0) + 1
                    if (samples.size < 12) {
                        samples += TvProbeRow(
                            pkg = pkg,
                            title = (ti.takeIf { it >= 0 }?.let { c.getString(it) }).orEmpty(),
                            poster = poi >= 0 && !c.getString(poi).isNullOrBlank(),
                            video = vi >= 0 && !c.getString(vi).isNullOrBlank(),
                            intent = ii >= 0 && !c.getString(ii).isNullOrBlank(),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            blocked = e is SecurityException
            note(e, "preview_program")
        }

        return TvProbeResult(
            permissionGranted = granted,
            previewChannels = previewChannels,
            previewPrograms = previewPrograms,
            watchNext = watchNext,
            perPackage = perPackage.entries.map { it.key to it.value },
            samples = samples,
            blocked = blocked,
            error = error,
        )
    }
}
