package com.tarang.launcher.dream

import android.service.dreams.DreamService
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tarang.launcher.TarangApp
import com.tarang.launcher.data.LauncherSettings
import com.tarang.launcher.ui.FrameArtScreensaver
import com.tarang.launcher.ui.rememberIsDark
import com.tarang.launcher.ui.theme.TarangTheme

/**
 * Tarang's Frame Art as the system screensaver. Registered as a Dream so the OS shows it during idle
 * (the user selects it under the device's Screensaver settings), which lets the system own the
 * ambient-display lifecycle properly instead of the launcher's own idle auto-start.
 *
 * A [DreamService] isn't a lifecycle/saved-state owner, so we provide those to the hosted [ComposeView]
 * ourselves — the standard recipe for running Compose outside an Activity.
 */
class FrameArtDreamService :
    DreamService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false // any key/touch dismisses the dream (the system handles that)
        isFullscreen = true
        isScreenBright = true // it's a picture frame — keep the display lit (night-dim handles the small hours)

        val settingsStore = (application as TarangApp).container.settingsStore
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FrameArtDreamService)
            setViewTreeViewModelStoreOwner(this@FrameArtDreamService)
            setViewTreeSavedStateRegistryOwner(this@FrameArtDreamService)
            setContent {
                val settings by settingsStore.settings.collectAsState(initial = LauncherSettings())
                TarangTheme {
                    FrameArtScreensaver(
                        settings = settings,
                        isDark = rememberIsDark(settings.theme),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        setContentView(view)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}
