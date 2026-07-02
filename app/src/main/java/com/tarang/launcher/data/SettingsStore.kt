package com.tarang.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tarang_settings")

const val MIN_COLUMNS = 3
const val MAX_COLUMNS = 7
const val DEFAULT_COLUMNS = 4

/** Light/dark appearance. [AUTO] follows the time of day (light 7am–7pm, dark otherwise). */
enum class ThemeMode { DARK, LIGHT, AUTO }

/** What the Frame Art mode (the "painting" full-screen view) displays. */
enum class FrameSource {
    /** Show whatever wallpaper is currently set (the default). */
    WALLPAPER,

    /** Cycle through the photos in a chosen device folder. */
    FOLDER,

    /** Show a single chosen photo. */
    SINGLE,
}

/** Where the Frame Art clock sits. */
enum class FrameClockPosition { BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT, CENTER }

/** Frame Art clock size. */
enum class FrameClockSize { SMALL, MEDIUM, LARGE }

/** Temperature unit for the weather readout. */
enum class WeatherUnit { CELSIUS, FAHRENHEIT }

/**
 * The transition "personality" used for the four big moves (enter/exit Frame Art, launch/return an
 * app). An experiment switch so the different motion languages can be A/B'd live on-device.
 *
 * - [BASELINE]  the shipped v0.2.2 motion: chrome scales up + flies apart (fixed tweens).
 * - [GLIDE]     fluid, spring-driven slides, no blur — cheapest and safest on weak TVs.
 * - [DEPTH]     z-axis: the home plane recedes to a painting, dives into an app (scale + fade).
 */
enum class AnimStyle { BASELINE, GLIDE, DEPTH }

/** Slideshow switch intervals (seconds) offered for the folder source. */
val FRAME_INTERVALS = listOf(10, 30, 60, 300, 900)

/** Idle timeouts (seconds) before Frame Art starts itself; 0 = only start it manually. */
val FRAME_AUTOSTART_TIMEOUTS = listOf(0, 60, 120, 300, 600, 1800)

/** User-tunable launcher look (set from the in-app settings panel). */
data class LauncherSettings(
    val wallpaperId: Int = 0,
    /** Frosted-glass blur behind the top bar and dock. Off = a flat tint (no backdrop capture/blur).
     *  Defaults off: it's the single biggest GPU cost on weak TVs, so opt in rather than out. */
    val glassBlur: Boolean = false,
    /** How many app tiles per row in the grid (and the dock). */
    val columns: Int = DEFAULT_COLUMNS,
    /** When true (and [wallpaperImagePath] resolves) the photo is shown instead of a gradient. */
    val useImageWallpaper: Boolean = false,
    /** Absolute path to the user's chosen wallpaper, copied into app storage. */
    val wallpaperImagePath: String? = null,
    /** Master switch: while hovering an opted-in favorite, play its artwork as the wallpaper. */
    val useAppArtwork: Boolean = false,
    /** Packages (favorites) whose TV artwork is allowed to take over the wallpaper on hover. */
    val artworkApps: Set<String> = emptySet(),
    /** Light/dark appearance. */
    val theme: ThemeMode = ThemeMode.DARK,
    /** Calm everything down: no wallpaper drift, slideshow, or tile-focus spring. */
    val reduceMotion: Boolean = false,
    /** Which transition "personality" the four big moves use (an experiment switch). */
    val animStyle: AnimStyle = AnimStyle.BASELINE,
    /** Packages the user has hidden from the grid (still launchable, just out of sight). */
    val hiddenApps: Set<String> = emptySet(),
    /** What Frame Art shows (defaults to the current wallpaper). */
    val frameSource: FrameSource = FrameSource.WALLPAPER,
    /** MediaStore bucket id of the folder to slideshow (when [frameSource] is FOLDER). */
    val frameFolderId: String? = null,
    /** Human-readable name of the chosen folder, for the settings UI. */
    val frameFolderName: String? = null,
    /** Absolute path to the single chosen frame photo, copied into app storage (SINGLE). */
    val frameImagePath: String? = null,
    /** Seconds each folder photo lingers before the next. */
    val frameIntervalSec: Int = 60,
    /** Idle seconds before Frame Art starts on its own (0 = manual only). */
    val frameAutoStartSec: Int = 0,
    /** Show a beautiful clock over the frame (off = pure picture). */
    val frameClock: Boolean = false,
    /** Where the frame clock sits. */
    val frameClockPosition: FrameClockPosition = FrameClockPosition.BOTTOM_LEFT,
    /** Frame clock size. */
    val frameClockSize: FrameClockSize = FrameClockSize.MEDIUM,
    /** Show the date line under the frame clock's time. */
    val frameShowDate: Boolean = true,
    /** Slow, continuous "living painting" drift over the frame image. */
    val frameMotion: Boolean = true,
    /** Shuffle the folder slideshow (random order) instead of newest-first. */
    val frameShuffle: Boolean = false,
    /** Play Frame Art as the launcher's wallpaper (so entering frame mode just dissolves the chrome).
     *  The home wallpaper is always a still frame; the art only comes alive in full frame mode. */
    val useFrameArtWallpaper: Boolean = false,
    /** Show the weather readout in the top bar on the home screen. */
    val weatherOnHome: Boolean = false,
    /** Show the weather readout beside the Frame Art clock. */
    val frameWeather: Boolean = false,
    /** Temperature unit for the weather readout. */
    val weatherUnit: WeatherUnit = WeatherUnit.CELSIUS,
    /** Chosen city name for the weather (null = detect automatically by IP). */
    val weatherCity: String? = null,
    /** Cached coordinates of [weatherCity]; null when detecting automatically. */
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    /** Dim Frame Art automatically during the deep-night hours (so it isn't bright at 2am). */
    val frameNightDim: Boolean = false,
    /** Show a compact "now playing" chip in the top bar (needs notification-access to read sessions). */
    val nowPlaying: Boolean = false,
)

/** Persists [LauncherSettings] via DataStore. */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<LauncherSettings> = dataStore.data.map { p ->
        LauncherSettings(
            wallpaperId = p[WALLPAPER_ID] ?: 0,
            glassBlur = p[GLASS_BLUR] ?: false,
            columns = (p[COLUMNS] ?: DEFAULT_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            useImageWallpaper = p[USE_IMAGE] ?: false,
            wallpaperImagePath = p[IMAGE_PATH],
            useAppArtwork = p[USE_APP_ARTWORK] ?: false,
            artworkApps = p[ARTWORK_APPS] ?: emptySet(),
            theme = runCatching { ThemeMode.valueOf(p[THEME] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            reduceMotion = p[REDUCE_MOTION] ?: false,
            animStyle = runCatching { AnimStyle.valueOf(p[ANIM_STYLE] ?: "BASELINE") }.getOrDefault(AnimStyle.BASELINE),
            hiddenApps = p[HIDDEN_APPS] ?: emptySet(),
            frameSource = runCatching {
                FrameSource.valueOf(p[FRAME_SOURCE] ?: "WALLPAPER")
            }.getOrDefault(FrameSource.WALLPAPER),
            frameFolderId = p[FRAME_FOLDER_ID],
            frameFolderName = p[FRAME_FOLDER_NAME],
            frameImagePath = p[FRAME_IMAGE_PATH],
            frameIntervalSec = p[FRAME_INTERVAL] ?: 60,
            frameAutoStartSec = p[FRAME_AUTOSTART] ?: 0,
            frameClock = p[FRAME_CLOCK] ?: false,
            frameClockPosition = runCatching {
                FrameClockPosition.valueOf(p[FRAME_CLOCK_POS] ?: "BOTTOM_LEFT")
            }.getOrDefault(FrameClockPosition.BOTTOM_LEFT),
            frameClockSize = runCatching {
                FrameClockSize.valueOf(p[FRAME_CLOCK_SIZE] ?: "MEDIUM")
            }.getOrDefault(FrameClockSize.MEDIUM),
            frameShowDate = p[FRAME_SHOW_DATE] ?: true,
            frameMotion = p[FRAME_MOTION] ?: true,
            frameShuffle = p[FRAME_SHUFFLE] ?: false,
            useFrameArtWallpaper = p[USE_FRAME_WALLPAPER] ?: false,
            weatherOnHome = p[WEATHER_ON_HOME] ?: false,
            frameWeather = p[FRAME_WEATHER] ?: false,
            weatherUnit = runCatching { WeatherUnit.valueOf(p[WEATHER_UNIT] ?: "CELSIUS") }
                .getOrDefault(WeatherUnit.CELSIUS),
            weatherCity = p[WEATHER_CITY],
            weatherLat = p[WEATHER_LAT],
            weatherLon = p[WEATHER_LON],
            frameNightDim = p[FRAME_NIGHT_DIM] ?: false,
            nowPlaying = p[NOW_PLAYING] ?: false,
        )
    }

    /** Selecting a gradient preset also switches off the image wallpaper. */
    suspend fun setWallpaper(id: Int) = dataStore.edit {
        it[WALLPAPER_ID] = id
        it[USE_IMAGE] = false
    }

    suspend fun setGlassBlur(value: Boolean) = dataStore.edit { it[GLASS_BLUR] = value }
    suspend fun setColumns(n: Int) = dataStore.edit { it[COLUMNS] = n.coerceIn(MIN_COLUMNS, MAX_COLUMNS) }

    /** Records a freshly picked photo and makes it the active wallpaper. */
    suspend fun setImageWallpaper(path: String) = dataStore.edit {
        it[IMAGE_PATH] = path
        it[USE_IMAGE] = true
    }

    /** Re-activates the already-stored photo without re-picking it. */
    suspend fun setUseImageWallpaper(value: Boolean) = dataStore.edit { it[USE_IMAGE] = value }

    suspend fun setUseAppArtwork(value: Boolean) = dataStore.edit { it[USE_APP_ARTWORK] = value }

    /** Opts a favorite package in/out of the artwork-on-hover wallpaper. */
    suspend fun setArtworkApp(packageName: String, enabled: Boolean) = dataStore.edit { p ->
        val current = p[ARTWORK_APPS] ?: emptySet()
        p[ARTWORK_APPS] = if (enabled) current + packageName else current - packageName
    }

    suspend fun setTheme(mode: ThemeMode) = dataStore.edit { it[THEME] = mode.name }
    suspend fun setReduceMotion(value: Boolean) = dataStore.edit { it[REDUCE_MOTION] = value }
    suspend fun setAnimStyle(style: AnimStyle) = dataStore.edit { it[ANIM_STYLE] = style.name }

    /** Hides/unhides an app from the grid. */
    suspend fun setAppHidden(packageName: String, hidden: Boolean) = dataStore.edit { p ->
        val current = p[HIDDEN_APPS] ?: emptySet()
        p[HIDDEN_APPS] = if (hidden) current + packageName else current - packageName
    }

    suspend fun setFrameSource(source: FrameSource) = dataStore.edit { it[FRAME_SOURCE] = source.name }

    /** Selecting a folder also switches the frame to the FOLDER source. */
    suspend fun setFrameFolder(id: String, name: String) = dataStore.edit {
        it[FRAME_FOLDER_ID] = id
        it[FRAME_FOLDER_NAME] = name
        it[FRAME_SOURCE] = FrameSource.FOLDER.name
    }

    /** Picking a single photo also switches the frame to the SINGLE source. */
    suspend fun setFrameImage(path: String) = dataStore.edit {
        it[FRAME_IMAGE_PATH] = path
        it[FRAME_SOURCE] = FrameSource.SINGLE.name
    }

    suspend fun setFrameInterval(sec: Int) = dataStore.edit { it[FRAME_INTERVAL] = sec }
    suspend fun setFrameAutoStart(sec: Int) = dataStore.edit { it[FRAME_AUTOSTART] = sec }
    suspend fun setFrameClock(value: Boolean) = dataStore.edit { it[FRAME_CLOCK] = value }
    suspend fun setFrameClockPosition(pos: FrameClockPosition) = dataStore.edit { it[FRAME_CLOCK_POS] = pos.name }
    suspend fun setFrameClockSize(size: FrameClockSize) = dataStore.edit { it[FRAME_CLOCK_SIZE] = size.name }
    suspend fun setFrameShowDate(value: Boolean) = dataStore.edit { it[FRAME_SHOW_DATE] = value }
    suspend fun setFrameMotion(value: Boolean) = dataStore.edit { it[FRAME_MOTION] = value }
    suspend fun setFrameShuffle(value: Boolean) = dataStore.edit { it[FRAME_SHUFFLE] = value }
    suspend fun setUseFrameArtWallpaper(value: Boolean) = dataStore.edit { it[USE_FRAME_WALLPAPER] = value }
    suspend fun setWeatherOnHome(value: Boolean) = dataStore.edit { it[WEATHER_ON_HOME] = value }
    suspend fun setFrameWeather(value: Boolean) = dataStore.edit { it[FRAME_WEATHER] = value }
    suspend fun setWeatherUnit(unit: WeatherUnit) = dataStore.edit { it[WEATHER_UNIT] = unit.name }

    /** Pins the weather to a chosen city (with its resolved coordinates). */
    suspend fun setWeatherCity(name: String, lat: Double, lon: Double) = dataStore.edit {
        it[WEATHER_CITY] = name
        it[WEATHER_LAT] = lat
        it[WEATHER_LON] = lon
    }

    /** Clears the chosen city so weather detects location automatically (by IP) again. */
    suspend fun clearWeatherCity() = dataStore.edit {
        it.remove(WEATHER_CITY)
        it.remove(WEATHER_LAT)
        it.remove(WEATHER_LON)
    }

    suspend fun setFrameNightDim(value: Boolean) = dataStore.edit { it[FRAME_NIGHT_DIM] = value }
    suspend fun setNowPlaying(value: Boolean) = dataStore.edit { it[NOW_PLAYING] = value }

    private companion object {
        val WALLPAPER_ID = intPreferencesKey("wallpaper_id")
        val GLASS_BLUR = booleanPreferencesKey("glass_blur")
        val COLUMNS = intPreferencesKey("columns")
        val USE_IMAGE = booleanPreferencesKey("use_image_wallpaper")
        val IMAGE_PATH = stringPreferencesKey("wallpaper_image_path")
        val USE_APP_ARTWORK = booleanPreferencesKey("use_app_artwork")
        val ARTWORK_APPS = stringSetPreferencesKey("artwork_apps")
        val THEME = stringPreferencesKey("theme")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val ANIM_STYLE = stringPreferencesKey("anim_style")
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val FRAME_SOURCE = stringPreferencesKey("frame_source")
        val FRAME_FOLDER_ID = stringPreferencesKey("frame_folder_id")
        val FRAME_FOLDER_NAME = stringPreferencesKey("frame_folder_name")
        val FRAME_IMAGE_PATH = stringPreferencesKey("frame_image_path")
        val FRAME_INTERVAL = intPreferencesKey("frame_interval_sec")
        val FRAME_AUTOSTART = intPreferencesKey("frame_autostart_sec")
        val FRAME_CLOCK = booleanPreferencesKey("frame_clock")
        val FRAME_CLOCK_POS = stringPreferencesKey("frame_clock_position")
        val FRAME_CLOCK_SIZE = stringPreferencesKey("frame_clock_size")
        val FRAME_SHOW_DATE = booleanPreferencesKey("frame_show_date")
        val FRAME_MOTION = booleanPreferencesKey("frame_motion")
        val FRAME_SHUFFLE = booleanPreferencesKey("frame_shuffle")
        val USE_FRAME_WALLPAPER = booleanPreferencesKey("use_frame_wallpaper")
        val WEATHER_ON_HOME = booleanPreferencesKey("weather_on_home")
        val FRAME_WEATHER = booleanPreferencesKey("frame_weather")
        val WEATHER_UNIT = stringPreferencesKey("weather_unit")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val WEATHER_LAT = doublePreferencesKey("weather_lat")
        val WEATHER_LON = doublePreferencesKey("weather_lon")
        val FRAME_NIGHT_DIM = booleanPreferencesKey("frame_night_dim")
        val NOW_PLAYING = booleanPreferencesKey("now_playing")
    }
}
