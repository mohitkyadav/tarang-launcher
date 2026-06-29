package com.tarang.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.AppRepository
import com.tarang.launcher.data.FavoritesStore
import com.tarang.launcher.data.LauncherSettings
import com.tarang.launcher.data.SettingsStore
import com.tarang.launcher.data.WatchNextItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LauncherUiState(
    val isLoading: Boolean = true,
    val dockApps: List<AppInfo> = emptyList(),
    val gridApps: List<AppInfo> = emptyList(),
    val allApps: List<AppInfo> = emptyList(),
    val watchNext: List<WatchNextItem> = emptyList(),
)

@OptIn(FlowPreview::class)
class LauncherViewModel(
    private val repository: AppRepository,
    private val favoritesStore: FavoritesStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val apps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val _focusedPackage = MutableStateFlow<String?>(null)
    private val watchNext = MutableStateFlow<List<WatchNextItem>>(emptyList())

    /** The currently focused app package — drives the ambient wallpaper glow. Kept OUT of [uiState]
     *  so moving focus doesn't recompute the dock/grid lists (and recompose the grid) on every press. */
    val focusedPackage: StateFlow<String?> = _focusedPackage

    val uiState: StateFlow<LauncherUiState> =
        combine(loading, apps, favoritesStore.favorites, watchNext) { isLoading, allApps, favorites, watchNextItems ->
            val favoriteSet = favorites.toSet()
            val dock = favorites.mapNotNull { pkg -> allApps.firstOrNull { it.packageName == pkg } }
            val grid = allApps.filterNot { it.packageName in favoriteSet }
            LauncherUiState(
                isLoading = isLoading,
                dockApps = dock,
                gridApps = grid,
                allApps = allApps,
                watchNext = watchNextItems,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    val settings: StateFlow<LauncherSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())

    init {
        refresh()
        // Keep the list live: refresh when apps are installed/removed/updated (debounced to
        // coalesce the burst of broadcasts a single install produces).
        viewModelScope.launch {
            repository.packageEvents().debounce(400).collect { refresh(showLoading = false) }
        }
    }

    /** [showLoading] is false for background refreshes (e.g. install/uninstall) so the grid
     *  doesn't flash the "Loading…" placeholder while the user is looking at it. */
    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) loading.value = true
            val loaded = repository.loadApps()
            apps.value = loaded
            loading.value = false
            watchNext.value = repository.watchNext()
            if (!favoritesStore.seeded.first()) {
                favoritesStore.setFavorites(loaded.take(DEFAULT_DOCK_COUNT).map { it.packageName })
                favoritesStore.markSeeded()
            }
        }
    }

    fun onAppFocused(packageName: String) {
        if (_focusedPackage.value != packageName) _focusedPackage.value = packageName
    }

    fun launchApp(packageName: String): Boolean = repository.launch(packageName)

    fun openAppInfo(packageName: String) = repository.openAppInfo(packageName)

    fun uninstallApp(packageName: String) = repository.requestUninstall(packageName)

    fun toggleFavorite(packageName: String) {
        viewModelScope.launch { favoritesStore.toggle(packageName) }
    }

    /** Persists a new dock order (used when the user reorders favorites in move mode). */
    fun setFavoritesOrder(packages: List<String>) {
        viewModelScope.launch { favoritesStore.setFavorites(packages) }
    }

    fun setWallpaper(id: Int) = viewModelScope.launch { settingsStore.setWallpaper(id) }.let {}
    fun setAnimated(value: Boolean) = viewModelScope.launch { settingsStore.setAnimated(value) }.let {}
    fun setBlurred(value: Boolean) = viewModelScope.launch { settingsStore.setBlurred(value) }.let {}
    fun setColumns(n: Int) = viewModelScope.launch { settingsStore.setColumns(n) }.let {}
    fun setImageWallpaper(path: String) = viewModelScope.launch { settingsStore.setImageWallpaper(path) }.let {}
    fun setUseImageWallpaper(value: Boolean) = viewModelScope.launch { settingsStore.setUseImageWallpaper(value) }.let {}
    fun setUseAppArtwork(value: Boolean) = viewModelScope.launch { settingsStore.setUseAppArtwork(value) }.let {}
    fun setArtworkApp(packageName: String, enabled: Boolean) =
        viewModelScope.launch { settingsStore.setArtworkApp(packageName, enabled) }.let {}
    fun setTheme(mode: com.tarang.launcher.data.ThemeMode) =
        viewModelScope.launch { settingsStore.setTheme(mode) }.let {}
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { settingsStore.setReduceMotion(value) }.let {}
    fun setAppHidden(packageName: String, hidden: Boolean) =
        viewModelScope.launch { settingsStore.setAppHidden(packageName, hidden) }.let {}
    fun setScreensaverTimeout(sec: Int) = viewModelScope.launch { settingsStore.setScreensaverTimeout(sec) }.let {}
    fun setScreensaverSource(source: com.tarang.launcher.data.ScreensaverSource) =
        viewModelScope.launch { settingsStore.setScreensaverSource(source) }.let {}

    companion object {
        private const val DEFAULT_DOCK_COUNT = 5

        fun provideFactory(
            repository: AppRepository,
            favoritesStore: FavoritesStore,
            settingsStore: SettingsStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LauncherViewModel(repository, favoritesStore, settingsStore) }
        }
    }
}
