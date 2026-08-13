package com.tarang.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Artwork for one app tile.
 * - [Banner]: the app's wide banner image (the tvOS-style look).
 * - [Fallback]: apps without a banner — their square icon centered on a color drawn from it.
 */
sealed interface TileArt {
    data class Banner(val image: androidx.compose.ui.graphics.ImageBitmap) : TileArt
    data class Fallback(val icon: androidx.compose.ui.graphics.ImageBitmap?, val color: Color) : TileArt
}

/**
 * Artwork for one Windows Metro tile: the app's square icon and a flat accent color drawn from it. The
 * Metro grid always uses icon-on-color (never the banner), so this is resolved and cached separately
 * from [TileArt].
 */
data class MetroTile(val icon: androidx.compose.ui.graphics.ImageBitmap?, val color: Color)

/**
 * Resolves per-app tile artwork (plan §2.3 / §5.3) and a brand accent color. Prefers the app's
 * banner so tiles look like tvOS/Google TV; falls back to icon-on-color when no banner is provided.
 *
 * Cached at three levels: an in-memory LRU, then a disk cache (bitmap file + a JSON index carrying
 * the fallback color), then the full PackageManager resolve (drawable render + Palette — the
 * expensive path). The disk layer is what makes a cold start cheap: decoding a small cached bitmap
 * is milliseconds where the full resolve is tens on weak TV CPUs. Entries are keyed to the
 * package's lastUpdateTime, so an app update re-resolves its art.
 */
class IconLoader(context: Context) {

    private val pm: PackageManager = context.applicationContext.packageManager
    private val tileCache = LruCache<String, TileArt>(CACHE_ENTRIES)
    private val metroCache = LruCache<String, MetroTile>(CACHE_ENTRIES)
    private val colorCache = LruCache<String, Int>(CACHE_ENTRIES)

    private val diskDir = File(context.applicationContext.filesDir, "tiles")
    private val indexFile = File(diskDir, "tiles.json")
    private val diskLock = Any()
    private var diskIndex: MutableMap<String, DiskTile>? = null // lazily loaded under [diskLock]

    /** One disk-cache index entry: what kind of art the bitmap file holds, and when it was resolved. */
    private data class DiskTile(val banner: Boolean, val color: Int, val stamp: Long)

    suspend fun loadTile(app: AppInfo): TileArt {
        tileCache.get(app.packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            tileCache.get(app.packageName)?.let { return@withContext it }
            val tile = loadTileFromDisk(app) ?: resolveTile(app)
            tileCache.put(app.packageName, tile)
            tile
        }
    }

    /**
     * Resolves the Metro tile art (icon + accent color) for [app]. Cached at the same three levels as
     * [loadTile] — an in-memory LRU, a disk bitmap + index entry (keyed under [METRO_PREFIX] so it
     * never collides with the tvOS tile), then the full PackageManager resolve. The disk layer keeps a
     * cold start into Metro cheap.
     */
    suspend fun loadMetroTile(app: AppInfo): MetroTile {
        metroCache.get(app.packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            metroCache.get(app.packageName)?.let { return@withContext it }
            val tile = loadMetroFromDisk(app) ?: resolveMetro(app)
            metroCache.put(app.packageName, tile)
            tile
        }
    }

    private fun loadMetroFromDisk(app: AppInfo): MetroTile? {
        val entry = index()[METRO_PREFIX + app.packageName] ?: return null
        val current = packageStamp(app.packageName)
        if (current != UNKNOWN_STAMP && current != entry.stamp) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(metroFile(app.packageName).path) }.getOrNull()
        return MetroTile(bitmap?.asImageBitmap(), Color(entry.color))
    }

    private fun resolveMetro(app: AppInfo): MetroTile {
        val stamp = packageStamp(app.packageName)
        val icon = resolveIcon(app)
        val bmp = icon?.toBitmap(METRO_ICON_PX, METRO_ICON_PX)
        val color = icon?.let { colorFromDrawable(it) } ?: DEFAULT_TILE_ARGB
        persistMetro(app.packageName, bmp, DiskTile(banner = false, color = color, stamp = stamp))
        return MetroTile(bmp?.asImageBitmap(), Color(color))
    }

    private fun persistMetro(pkg: String, bmp: Bitmap?, entry: DiskTile) {
        runCatching {
            diskDir.mkdirs()
            val file = metroFile(pkg)
            if (bmp != null) {
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            } else {
                file.delete()
            }
            synchronized(diskLock) {
                index()[METRO_PREFIX + pkg] = entry
                writeIndex()
            }
        }
    }

    private fun metroFile(pkg: String): File = File(diskDir, "$pkg.metro.img")

    /** Brand color drawn from the app's icon, used for the ambient wallpaper tint. */
    suspend fun accentColor(app: AppInfo): Color {
        colorCache.get(app.packageName)?.let { return Color(it) }
        return withContext(Dispatchers.IO) {
            val argb = resolveIcon(app)?.let { colorFromDrawable(it) } ?: DEFAULT_TILE_ARGB
            colorCache.put(app.packageName, argb)
            Color(argb)
        }
    }

    // ---- Disk layer ----------------------------------------------------------------------------

    private fun loadTileFromDisk(app: AppInfo): TileArt? {
        val entry = index()[app.packageName] ?: return null
        // Re-resolve after an app update. A failed stamp lookup (package gone — e.g. a stale entry
        // from the cached app list) still serves the disk art: the real scan drops the tile shortly.
        val current = packageStamp(app.packageName)
        if (current != UNKNOWN_STAMP && current != entry.stamp) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(bitmapFile(app.packageName).path) }.getOrNull()
        return when {
            entry.banner -> bitmap?.let { TileArt.Banner(it.asImageBitmap()) }
            else -> TileArt.Fallback(bitmap?.asImageBitmap(), Color(entry.color))
        }
    }

    /** The full resolve (drawable render + Palette), persisted to disk for the next cold start. */
    private fun resolveTile(app: AppInfo): TileArt {
        val stamp = packageStamp(app.packageName)
        val banner = resolveBanner(app)
        if (banner != null) {
            val bmp = banner.toBitmap(BANNER_W, BANNER_H)
            persistTile(app.packageName, bmp, DiskTile(banner = true, color = 0, stamp = stamp))
            return TileArt.Banner(bmp.asImageBitmap())
        }
        val iconDrawable = resolveIcon(app)
        val bmp = iconDrawable?.toBitmap(ICON_PX, ICON_PX)
        val color = iconDrawable?.let { colorFromDrawable(it) } ?: DEFAULT_TILE_ARGB
        persistTile(app.packageName, bmp, DiskTile(banner = false, color = color, stamp = stamp))
        return TileArt.Fallback(bmp?.asImageBitmap(), Color(color))
    }

    private fun persistTile(pkg: String, bmp: Bitmap?, entry: DiskTile) {
        runCatching {
            diskDir.mkdirs()
            val file = bitmapFile(pkg)
            if (bmp != null) {
                // Banners are opaque by spec → JPEG (small). Icons need alpha → PNG (small anyway).
                val format = if (entry.banner) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                file.outputStream().use { bmp.compress(format, 85, it) }
            } else {
                file.delete()
            }
            synchronized(diskLock) {
                index()[pkg] = entry
                writeIndex()
            }
        }
    }

    private fun index(): MutableMap<String, DiskTile> = synchronized(diskLock) {
        diskIndex ?: loadIndex().also { diskIndex = it }
    }

    private fun loadIndex(): MutableMap<String, DiskTile> = runCatching {
        val obj = JSONObject(indexFile.readText())
        val map = mutableMapOf<String, DiskTile>()
        for (key in obj.keys()) {
            val e = obj.getJSONObject(key)
            map[key] = DiskTile(e.getBoolean("banner"), e.getInt("color"), e.getLong("stamp"))
        }
        map
    }.getOrDefault(mutableMapOf()) // missing/corrupt index → cold resolve rebuilds it

    private fun writeIndex() {
        runCatching {
            val obj = JSONObject()
            for ((pkg, e) in index()) {
                obj.put(pkg, JSONObject().put("banner", e.banner).put("color", e.color).put("stamp", e.stamp))
            }
            indexFile.writeText(obj.toString())
        }
    }

    private fun bitmapFile(pkg: String): File = File(diskDir, "$pkg.img")

    private fun packageStamp(pkg: String): Long =
        runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(UNKNOWN_STAMP)

    // ---- PackageManager resolve ------------------------------------------------------------------

    private fun resolveBanner(app: AppInfo): Drawable? =
        runCatching { pm.getActivityBanner(ComponentName(app.packageName, app.activityName)) }.getOrNull()
            ?: runCatching { pm.getApplicationBanner(app.packageName) }.getOrNull()

    private fun resolveIcon(app: AppInfo): Drawable? =
        runCatching { pm.getActivityIcon(ComponentName(app.packageName, app.activityName)) }.getOrNull()
            ?: runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()

    private fun colorFromDrawable(drawable: Drawable): Int {
        val bmp = drawable.toBitmap(PALETTE_PX, PALETTE_PX)
        val palette = Palette.from(bmp).generate()
        return (palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch)?.rgb
            ?: DEFAULT_TILE_ARGB
    }

    private companion object {
        const val CACHE_ENTRIES = 256
        const val BANNER_W = 320
        const val BANNER_H = 180 // 16:9 native banner; the UI crops it to the 5:3 tile
        const val ICON_PX = 144
        const val METRO_ICON_PX = 192 // crisp on a 1080p TV tile
        const val METRO_PREFIX = "metro:" // disk-index key prefix; keeps Metro art off the tvOS tile
        const val PALETTE_PX = 64
        const val UNKNOWN_STAMP = -1L
        val DEFAULT_TILE_ARGB = 0xFF2A2A2C.toInt()
    }
}
