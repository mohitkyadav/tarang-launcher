package com.tarang.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tarang_settings")

const val MIN_COLUMNS = 3
const val MAX_COLUMNS = 7
const val DEFAULT_COLUMNS = 4

/** User-tunable launcher look (set from the in-app settings panel). */
data class LauncherSettings(
    val wallpaperId: Int = 0,
    val animated: Boolean = true,
    val blurred: Boolean = false,
    /** How many app tiles per row in the grid (and the dock). */
    val columns: Int = DEFAULT_COLUMNS,
    /** When true (and [wallpaperImagePath] resolves) the photo is shown instead of a gradient. */
    val useImageWallpaper: Boolean = false,
    /** Absolute path to the user's chosen wallpaper, copied into app storage. */
    val wallpaperImagePath: String? = null,
)

/** Persists [LauncherSettings] via DataStore. */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<LauncherSettings> = dataStore.data.map { p ->
        LauncherSettings(
            wallpaperId = p[WALLPAPER_ID] ?: 0,
            animated = p[ANIMATED] ?: true,
            blurred = p[BLURRED] ?: false,
            columns = (p[COLUMNS] ?: DEFAULT_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            useImageWallpaper = p[USE_IMAGE] ?: false,
            wallpaperImagePath = p[IMAGE_PATH],
        )
    }

    /** Selecting a gradient preset also switches off the image wallpaper. */
    suspend fun setWallpaper(id: Int) = dataStore.edit {
        it[WALLPAPER_ID] = id
        it[USE_IMAGE] = false
    }

    suspend fun setAnimated(value: Boolean) = dataStore.edit { it[ANIMATED] = value }
    suspend fun setBlurred(value: Boolean) = dataStore.edit { it[BLURRED] = value }
    suspend fun setColumns(n: Int) = dataStore.edit { it[COLUMNS] = n.coerceIn(MIN_COLUMNS, MAX_COLUMNS) }

    /** Records a freshly picked photo and makes it the active wallpaper. */
    suspend fun setImageWallpaper(path: String) = dataStore.edit {
        it[IMAGE_PATH] = path
        it[USE_IMAGE] = true
    }

    /** Re-activates the already-stored photo without re-picking it. */
    suspend fun setUseImageWallpaper(value: Boolean) = dataStore.edit { it[USE_IMAGE] = value }

    private companion object {
        val WALLPAPER_ID = intPreferencesKey("wallpaper_id")
        val ANIMATED = booleanPreferencesKey("animated")
        val BLURRED = booleanPreferencesKey("blurred")
        val COLUMNS = intPreferencesKey("columns")
        val USE_IMAGE = booleanPreferencesKey("use_image_wallpaper")
        val IMAGE_PATH = stringPreferencesKey("wallpaper_image_path")
    }
}
