package com.tarang.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.AppListCache
import com.tarang.launcher.data.AppRepository
import com.tarang.launcher.data.FavoritesStore
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.LauncherSettings
import com.tarang.launcher.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
)

@OptIn(FlowPreview::class)
class LauncherViewModel(
    private val repository: AppRepository,
    private val favoritesStore: FavoritesStore,
    private val settingsStore: SettingsStore,
    private val appListCache: AppListCache,
    private val iconLoader: IconLoader,
) : ViewModel() {

    private val apps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val _focusedPackage = MutableStateFlow<String?>(null)

    /** The currently focused app package — drives the ambient wallpaper glow. Kept OUT of [uiState]
     *  so moving focus doesn't recompute the dock/grid lists (and recompose the grid) on every press. */
    val focusedPackage: StateFlow<String?> = _focusedPackage

    val uiState: StateFlow<LauncherUiState> =
        combine(loading, apps, favoritesStore.favorites) { isLoading, allApps, favorites ->
            val favoriteSet = favorites.toSet()
            val dock = favorites.mapNotNull { pkg -> allApps.firstOrNull { it.packageName == pkg } }
            val grid = allApps.filterNot { it.packageName in favoriteSet }
            LauncherUiState(
                isLoading = isLoading,
                dockApps = dock,
                gridApps = grid,
                allApps = allApps,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    /** Null until the first DataStore read lands — the UI holds its (black) first frame on it
     *  instead of flashing default settings (wrong wallpaper/theme) and re-rendering. */
    val settings: StateFlow<LauncherSettings?> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var prefetchJob: Job? = null

    init {
        viewModelScope.launch {
            // Cold start: publish the last-known app list from disk so the dock/grid draw on the
            // first frame instead of waiting out the PackageManager scan. The real scan follows
            // (briefly deferred on a cache hit, so it doesn't contend with first-frame rendering).
            val cached = appListCache.read()
            if (!cached.isNullOrEmpty() && apps.value.isEmpty()) {
                apps.value = cached
                loading.value = false
                delay(COLD_START_SCAN_DELAY_MS)
            }
            scan(showLoading = apps.value.isEmpty())
        }
        // Keep the list live: refresh when apps are installed/removed/updated (debounced to
        // coalesce the burst of broadcasts a single install produces).
        viewModelScope.launch {
            repository.packageEvents().debounce(400).collect { refresh(showLoading = false) }
        }
    }

    /** [showLoading] is false for background refreshes (e.g. install/uninstall) so the grid
     *  doesn't flash the "Loading…" placeholder while the user is looking at it. */
    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch { scan(showLoading) }
    }

    private suspend fun scan(showLoading: Boolean) {
        if (showLoading) loading.value = true
        val loaded = repository.loadApps()
        apps.value = loaded
        loading.value = false
        appListCache.write(loaded)
        if (!favoritesStore.seeded.first()) {
            favoritesStore.setFavorites(loaded.take(DEFAULT_DOCK_COUNT).map { it.packageName })
            favoritesStore.markSeeded()
        }
        // Warm the art the active home style shows (the two styles resolve different bitmaps), so the
        // prefetch cost stays the same as before rather than doubling.
        val style = runCatching { settingsStore.settings.first().launcherStyle }
            .getOrDefault(com.tarang.launcher.data.LauncherStyle.TVOS)
        prefetchTiles(loaded, style)
    }

    /** Warms the tile-art disk cache for every app (sequentially, after a beat) so off-screen tiles —
     *  and the whole next cold start — load from disk instead of the slow PM resolve. */
    private fun prefetchTiles(apps: List<AppInfo>, style: com.tarang.launcher.data.LauncherStyle) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(PREFETCH_DELAY_MS)
            for (app in apps) {
                runCatching {
                    if (style == com.tarang.launcher.data.LauncherStyle.WINDOWS_METRO) {
                        iconLoader.loadMetroTile(app)
                    } else {
                        iconLoader.loadTile(app)
                    }
                }
            }
        }
    }

    fun onAppFocused(packageName: String) {
        if (_focusedPackage.value != packageName) _focusedPackage.value = packageName
    }

    fun launchApp(packageName: String, options: android.os.Bundle? = null): Boolean =
        repository.launch(packageName, options)

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
    fun setGlassBlur(value: Boolean) = viewModelScope.launch { settingsStore.setGlassBlur(value) }.let {}
    fun setColumns(n: Int) = viewModelScope.launch { settingsStore.setColumns(n) }.let {}
    fun setLauncherStyle(style: com.tarang.launcher.data.LauncherStyle) =
        viewModelScope.launch { settingsStore.setLauncherStyle(style) }.let {}
    fun setMetroTileSquare(packageName: String, square: Boolean) =
        viewModelScope.launch { settingsStore.setMetroTileSquare(packageName, square) }.let {}
    fun setImageWallpaper(path: String) = viewModelScope.launch { settingsStore.setImageWallpaper(path) }.let {}
    fun setUseImageWallpaper(value: Boolean) = viewModelScope.launch { settingsStore.setUseImageWallpaper(value) }.let {}
    fun setUseAppArtwork(value: Boolean) = viewModelScope.launch { settingsStore.setUseAppArtwork(value) }.let {}
    fun setArtworkApp(packageName: String, enabled: Boolean) =
        viewModelScope.launch { settingsStore.setArtworkApp(packageName, enabled) }.let {}
    fun setTheme(mode: com.tarang.launcher.data.ThemeMode) =
        viewModelScope.launch { settingsStore.setTheme(mode) }.let {}
    fun setAppHidden(packageName: String, hidden: Boolean) =
        viewModelScope.launch { settingsStore.setAppHidden(packageName, hidden) }.let {}
    fun setFrameSource(source: com.tarang.launcher.data.FrameSource) =
        viewModelScope.launch { settingsStore.setFrameSource(source) }.let {}
    fun setFrameFolder(id: String, name: String) =
        viewModelScope.launch { settingsStore.setFrameFolder(id, name) }.let {}
    fun setFrameImage(path: String) = viewModelScope.launch { settingsStore.setFrameImage(path) }.let {}
    fun setFrameInterval(sec: Int) = viewModelScope.launch { settingsStore.setFrameInterval(sec) }.let {}
    fun setFrameAutoStart(sec: Int) = viewModelScope.launch { settingsStore.setFrameAutoStart(sec) }.let {}
    fun setFrameClock(value: Boolean) = viewModelScope.launch { settingsStore.setFrameClock(value) }.let {}
    fun setFrameClockPosition(pos: com.tarang.launcher.data.FrameClockPosition) =
        viewModelScope.launch { settingsStore.setFrameClockPosition(pos) }.let {}
    fun setFrameClockSize(size: com.tarang.launcher.data.FrameClockSize) =
        viewModelScope.launch { settingsStore.setFrameClockSize(size) }.let {}
    fun setFrameShowDate(value: Boolean) = viewModelScope.launch { settingsStore.setFrameShowDate(value) }.let {}
    fun setFrameMotion(value: Boolean) = viewModelScope.launch { settingsStore.setFrameMotion(value) }.let {}
    fun setFrameShuffle(value: Boolean) = viewModelScope.launch { settingsStore.setFrameShuffle(value) }.let {}
    fun setUseFrameArtWallpaper(value: Boolean) =
        viewModelScope.launch { settingsStore.setUseFrameArtWallpaper(value) }.let {}
    fun setWeatherOnHome(value: Boolean) = viewModelScope.launch { settingsStore.setWeatherOnHome(value) }.let {}
    fun setFrameWeather(value: Boolean) = viewModelScope.launch { settingsStore.setFrameWeather(value) }.let {}
    fun setWeatherUnit(unit: com.tarang.launcher.data.WeatherUnit) =
        viewModelScope.launch { settingsStore.setWeatherUnit(unit) }.let {}
    fun setWeatherCity(name: String, lat: Double, lon: Double) =
        viewModelScope.launch { settingsStore.setWeatherCity(name, lat, lon) }.let {}
    fun clearWeatherCity() = viewModelScope.launch { settingsStore.clearWeatherCity() }.let {}
    fun setFrameNightDim(value: Boolean) = viewModelScope.launch { settingsStore.setFrameNightDim(value) }.let {}
    fun setNowPlaying(value: Boolean) = viewModelScope.launch { settingsStore.setNowPlaying(value) }.let {}
    fun setNavSounds(value: Boolean) = viewModelScope.launch { settingsStore.setNavSounds(value) }.let {}

    companion object {
        private const val DEFAULT_DOCK_COUNT = 5

        /** How long a cache-hit cold start defers the verify scan, keeping the CPU free while the
         *  first frames render. Package broadcasts still trigger an immediate refresh. */
        private const val COLD_START_SCAN_DELAY_MS = 1_500L

        /** How long after a scan the tile prefetch starts (lets the visible tiles load first). */
        private const val PREFETCH_DELAY_MS = 3_000L

        fun provideFactory(
            repository: AppRepository,
            favoritesStore: FavoritesStore,
            settingsStore: SettingsStore,
            appListCache: AppListCache,
            iconLoader: IconLoader,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LauncherViewModel(repository, favoritesStore, settingsStore, appListCache, iconLoader) }
        }
    }
}
