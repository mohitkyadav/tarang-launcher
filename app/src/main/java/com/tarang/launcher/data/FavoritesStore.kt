package com.tarang.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "tarang_favorites")

/**
 * Persists the ordered list of "dock" favorite packages (plan §5.7).
 *
 * Stored as a single newline-joined string so insertion order is preserved (a Preferences
 * string-set would lose order). [seeded] guards a one-time default so the dock isn't empty on
 * first run.
 */
class FavoritesStore(context: Context) {

    private val dataStore = context.applicationContext.favoritesDataStore

    val favorites: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[FAVORITES_KEY].orEmpty().split('\n').filter { it.isNotBlank() }
    }

    val seeded: Flow<Boolean> = dataStore.data.map { it[SEEDED_KEY] ?: false }

    suspend fun setFavorites(packages: List<String>) {
        dataStore.edit { it[FAVORITES_KEY] = packages.joinToString("\n") }
    }

    /** Adds [packageName] to the dock if absent, removes it if present (appends to the end). */
    suspend fun toggle(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY].orEmpty().split('\n').filter { it.isNotBlank() }.toMutableList()
            if (!current.remove(packageName)) current.add(packageName)
            prefs[FAVORITES_KEY] = current.joinToString("\n")
        }
    }

    suspend fun markSeeded() {
        dataStore.edit { it[SEEDED_KEY] = true }
    }

    private companion object {
        val FAVORITES_KEY = stringPreferencesKey("favorites")
        val SEEDED_KEY = booleanPreferencesKey("seeded")
    }
}
