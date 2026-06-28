package com.tarang.launcher.di

import android.content.Context
import com.tarang.launcher.data.AppRepository
import com.tarang.launcher.data.FavoritesStore
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.SettingsStore

/**
 * Minimal manual dependency graph (plan §4). Held by [com.tarang.launcher.TarangApp].
 * Hilt is the upgrade path if/when this grows.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appRepository: AppRepository by lazy { AppRepository(appContext) }
    val iconLoader: IconLoader by lazy { IconLoader(appContext) }
    val favoritesStore: FavoritesStore by lazy { FavoritesStore(appContext) }
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
}
