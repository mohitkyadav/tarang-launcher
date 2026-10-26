package com.tarang.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
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
    // Bounded by bytes so a TV with hundreds of apps can't grow it without limit (sizes in KB).
    private val tileCache = object : LruCache<String, TileArt>(TILE_CACHE_KB) {
        override fun sizeOf(key: String, value: TileArt): Int {
            val bitmap = when (value) {
                is TileArt.Banner -> value.image
                is TileArt.Fallback -> value.icon
            }
            return ((bitmap?.asAndroidBitmap()?.allocationByteCount ?: 0) / 1024).coerceAtLeast(1)
        }
    }
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
        // Banners are opaque, so RGB_565 holds them at half the memory of ARGB_8888.
        val opts = BitmapFactory.Options().apply {
            if (entry.banner) inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = runCatching { BitmapFactory.decodeFile(bitmapFile(app.packageName).path, opts) }.getOrNull()
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
            val bmp = banner.toBitmap(BANNER_W, BANNER_H, Bitmap.Config.RGB_565)
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
        const val TILE_CACHE_KB = 16 * 1024 // ~140 banners at 320×180 RGB_565
        const val BANNER_W = 320
        const val BANNER_H = 180 // 16:9 native banner; the UI crops it to the 5:3 tile
        const val ICON_PX = 144
        const val PALETTE_PX = 64
        const val UNKNOWN_STAMP = -1L
        val DEFAULT_TILE_ARGB = 0xFF2A2A2C.toInt()
    }
}
