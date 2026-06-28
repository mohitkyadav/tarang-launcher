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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LauncherUiState(
    val isLoading: Boolean = true,
    val dockApps: List<AppInfo> = emptyList(),
    val gridApps: List<AppInfo> = emptyList(),
    val allApps: List<AppInfo> = emptyList(),
    val focusedPackage: String? = null,
)

class LauncherViewModel(
    private val repository: AppRepository,
    private val favoritesStore: FavoritesStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val apps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val focusedPackage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LauncherUiState> =
        combine(loading, apps, favoritesStore.favorites, focusedPackage) { isLoading, allApps, favorites, focused ->
            val favoriteSet = favorites.toSet()
            val dock = favorites.mapNotNull { pkg -> allApps.firstOrNull { it.packageName == pkg } }
            val grid = allApps.filterNot { it.packageName in favoriteSet }
            LauncherUiState(
                isLoading = isLoading,
                dockApps = dock,
                gridApps = grid,
                allApps = allApps,
                focusedPackage = focused,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    val settings: StateFlow<LauncherSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            val loaded = repository.loadApps()
            apps.value = loaded
            loading.value = false
            if (!favoritesStore.seeded.first()) {
                favoritesStore.setFavorites(loaded.take(DEFAULT_DOCK_COUNT).map { it.packageName })
                favoritesStore.markSeeded()
            }
        }
    }

    fun onAppFocused(packageName: String) {
        if (focusedPackage.value != packageName) focusedPackage.value = packageName
    }

    fun launchApp(packageName: String) {
        repository.launch(packageName)
    }

    fun toggleFavorite(packageName: String) {
        viewModelScope.launch { favoritesStore.toggle(packageName) }
    }

    fun setWallpaper(id: Int) = viewModelScope.launch { settingsStore.setWallpaper(id) }.let {}
    fun setAnimated(value: Boolean) = viewModelScope.launch { settingsStore.setAnimated(value) }.let {}
    fun setBlurred(value: Boolean) = viewModelScope.launch { settingsStore.setBlurred(value) }.let {}

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
