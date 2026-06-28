package com.tarang.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tarang_settings")

/** User-tunable launcher look (set from the in-app settings panel). */
data class LauncherSettings(
    val wallpaperId: Int = 0,
    val animated: Boolean = true,
    val blurred: Boolean = false,
)

/** Persists [LauncherSettings] via DataStore. */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<LauncherSettings> = dataStore.data.map { p ->
        LauncherSettings(
            wallpaperId = p[WALLPAPER_ID] ?: 0,
            animated = p[ANIMATED] ?: true,
            blurred = p[BLURRED] ?: false,
        )
    }

    suspend fun setWallpaper(id: Int) = dataStore.edit { it[WALLPAPER_ID] = id }
    suspend fun setAnimated(value: Boolean) = dataStore.edit { it[ANIMATED] = value }
    suspend fun setBlurred(value: Boolean) = dataStore.edit { it[BLURRED] = value }

    private companion object {
        val WALLPAPER_ID = intPreferencesKey("wallpaper_id")
        val ANIMATED = booleanPreferencesKey("animated")
        val BLURRED = booleanPreferencesKey("blurred")
    }
}
