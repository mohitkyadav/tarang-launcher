package com.tarang.launcher.ui

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.data.AppArtwork
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.LauncherSettings
import com.tarang.launcher.data.MAX_COLUMNS
import com.tarang.launcher.data.MIN_COLUMNS
import com.tarang.launcher.data.SCREENSAVER_TIMEOUTS
import com.tarang.launcher.data.ScreensaverSource
import com.tarang.launcher.data.TV_LISTINGS_PERMISSION
import com.tarang.launcher.data.ThemeMode
import com.tarang.launcher.data.TvArtwork
import com.tarang.launcher.home.HomeSetup

private enum class SettingsSection(val title: String) {
    APPEARANCE("Appearance"),
    HOME_SETUP("Home setup"),
    HIDDEN_APPS("Hidden apps"),
    DIAGNOSTICS("Diagnostics"),
}

/**
 * A full-screen, tvOS-style settings page: a section list on the left, the selected section's
 * controls on the right. Focusing a left item selects it (the detail follows focus); press RIGHT to
 * step into the controls, LEFT to come back, and Back to leave. Rendered in place of the launcher
 * (not as a modal), so D-pad focus can't leak to the grid behind it. Colors come from
 * [LocalLauncherColors] so the page follows the light/dark theme.
 */
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onWallpaper: (Int) -> Unit,
    onAnimated: (Boolean) -> Unit,
    onBlurred: (Boolean) -> Unit,
    onColumns: (Int) -> Unit,
    onPickImage: () -> Unit,
    onUseImage: () -> Unit,
    onScanTvContent: () -> Unit,
    favoriteApps: List<AppInfo>,
    onUseAppArtwork: (Boolean) -> Unit,
    onToggleArtworkApp: (String, Boolean) -> Unit,
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    hiddenApps: List<AppInfo>,
    onUnhideApp: (String) -> Unit,
    screensaverTimeoutSec: Int,
    onScreensaverTimeout: (Int) -> Unit,
    screensaverSource: ScreensaverSource,
    onScreensaverSource: (ScreensaverSource) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onChooseHomeApp: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val colors = LocalLauncherColors.current
    var section by remember { mutableStateOf(SettingsSection.APPEARANCE) }
    val firstSection = remember { FocusRequester() }

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.page),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 56.dp, top = 44.dp, bottom = 0.dp),
        ) {
            // Left: section list.
            Column(modifier = Modifier.width(280.dp).fillMaxHeight()) {
                Text("Settings", color = colors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(28.dp))
                SettingsSection.entries.forEachIndexed { i, s ->
                    SectionNavRow(
                        title = s.title,
                        active = section == s,
                        onFocused = { section = s },
                        modifier = if (i == 0) Modifier.focusRequester(firstSection) else Modifier,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.width(40.dp))

            // Right: detail pane for the selected section.
            Box(modifier = Modifier.fillMaxSize()) {
                when (section) {
                    SettingsSection.APPEARANCE -> AppearancePane(
                        settings = settings,
                        onWallpaper = onWallpaper,
                        onAnimated = onAnimated,
                        onBlurred = onBlurred,
                        onColumns = onColumns,
                        onPickImage = onPickImage,
                        onUseImage = onUseImage,
                        favoriteApps = favoriteApps,
                        onUseAppArtwork = onUseAppArtwork,
                        onToggleArtworkApp = onToggleArtworkApp,
                        theme = theme,
                        onTheme = onTheme,
                        reduceMotion = reduceMotion,
                        onReduceMotion = onReduceMotion,
                        screensaverTimeoutSec = screensaverTimeoutSec,
                        onScreensaverTimeout = onScreensaverTimeout,
                        screensaverSource = screensaverSource,
                        onScreensaverSource = onScreensaverSource,
                    )

                    SettingsSection.HOME_SETUP -> HomeSetupPane(
                        onOpenAccessibility = onOpenAccessibilitySettings,
                        onChooseHome = onChooseHomeApp,
                    )

                    SettingsSection.HIDDEN_APPS -> HiddenAppsPane(
                        hiddenApps = hiddenApps,
                        onUnhide = onUnhideApp,
                    )

                    SettingsSection.DIAGNOSTICS -> DiagnosticsPane(onScanTvContent = onScanTvContent)
                }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { firstSection.requestFocus() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionNavRow(
    title: String,
    active: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLauncherColors.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = {},
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) colors.text.copy(alpha = 0.10f) else Color.Transparent,
            focusedContainerColor = colors.highlight,
        ),
    ) {
        Text(
            text = title,
            color = when {
                focused -> colors.onHighlight
                active -> colors.text
                else -> colors.textDim
            },
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun AppearancePane(
    settings: LauncherSettings,
    onWallpaper: (Int) -> Unit,
    onAnimated: (Boolean) -> Unit,
    onBlurred: (Boolean) -> Unit,
    onColumns: (Int) -> Unit,
    onPickImage: () -> Unit,
    onUseImage: () -> Unit,
    favoriteApps: List<AppInfo>,
    onUseAppArtwork: (Boolean) -> Unit,
    onToggleArtworkApp: (String, Boolean) -> Unit,
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    screensaverTimeoutSec: Int,
    onScreensaverTimeout: (Int) -> Unit,
    screensaverSource: ScreensaverSource,
    onScreensaverSource: (ScreensaverSource) -> Unit,
) {
    val thumb = rememberWallpaperThumb(settings.wallpaperImagePath)
    val imageActive = settings.useImageWallpaper

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        PaneTitle("Appearance")

        SectionLabel("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToggleChip("Dark", theme == ThemeMode.DARK) { onTheme(ThemeMode.DARK) }
            ToggleChip("Light", theme == ThemeMode.LIGHT) { onTheme(ThemeMode.LIGHT) }
            ToggleChip("Automatic", theme == ThemeMode.AUTO) { onTheme(ThemeMode.AUTO) }
        }
        if (theme == ThemeMode.AUTO) {
            Text("Light from 7am to 7pm, dark otherwise.", color = LocalLauncherColors.current.textDim, fontSize = 13.sp)
        }

        SectionLabel("Wallpaper")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WallpaperPresets.forEachIndexed { i, preset ->
                PresetSwatch(
                    preset = preset,
                    selected = !imageActive && i == settings.wallpaperId,
                    onClick = { onWallpaper(i) },
                )
            }
            PhotoSwatch(
                thumb = thumb,
                selected = imageActive,
                onClick = {
                    when {
                        settings.wallpaperImagePath == null -> onPickImage()
                        !imageActive -> onUseImage()
                        else -> onPickImage()
                    }
                },
            )
        }

        AppArtworkSection(
            settings = settings,
            favoriteApps = favoriteApps,
            onUseAppArtwork = onUseAppArtwork,
            onToggleArtworkApp = onToggleArtworkApp,
        )

        SectionLabel("Tiles per row")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (n in MIN_COLUMNS..MAX_COLUMNS) {
                ToggleChip("$n", n == settings.columns) { onColumns(n) }
            }
        }

        SectionLabel("Motion")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToggleChip("Animated", settings.animated) { onAnimated(true) }
            ToggleChip("Static", !settings.animated) { onAnimated(false) }
        }

        SectionLabel("Reduce motion")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToggleChip("On", reduceMotion) { onReduceMotion(true) }
            ToggleChip("Off", !reduceMotion) { onReduceMotion(false) }
        }
        Text(
            "Calms the interface: no wallpaper drift, no artwork slideshow, and tiles snap into focus instead of springing.",
            color = LocalLauncherColors.current.textDim,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(0.85f),
        )

        SectionLabel("Background")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToggleChip("Blurred", settings.blurred) { onBlurred(true) }
            ToggleChip("Sharp", !settings.blurred) { onBlurred(false) }
        }

        SectionLabel("Screensaver")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SCREENSAVER_TIMEOUTS.forEach { sec ->
                ToggleChip(timeoutLabel(sec), sec == screensaverTimeoutSec) { onScreensaverTimeout(sec) }
            }
        }
        if (screensaverTimeoutSec > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ToggleChip("Artwork", screensaverSource == ScreensaverSource.ARTWORK) {
                    onScreensaverSource(ScreensaverSource.ARTWORK)
                }
                ToggleChip("Clock", screensaverSource == ScreensaverSource.CLOCK) {
                    onScreensaverSource(ScreensaverSource.CLOCK)
                }
            }
            Text(
                "After this long with no input, show a slideshow of your apps' artwork (or just a " +
                    "clock) until you press a button.",
                color = LocalLauncherColors.current.textDim,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        }

        // Breathing room so the last section can scroll clear of the screen edge.
        Spacer(Modifier.height(44.dp))
    }
}

/**
 * "App artwork" wallpaper controls: a master on/off, plus (when on) a per-favorite list of apps that
 * publish poster artwork, each individually toggleable. Needs [TV_LISTINGS_PERMISSION] to read what
 * apps publish; offers to grant it inline.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppArtworkSection(
    settings: LauncherSettings,
    favoriteApps: List<AppInfo>,
    onUseAppArtwork: (Boolean) -> Unit,
    onToggleArtworkApp: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalLauncherColors.current

    SectionLabel("App artwork")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ToggleChip("On", settings.useAppArtwork) { onUseAppArtwork(true) }
        ToggleChip("Off", !settings.useAppArtwork) { onUseAppArtwork(false) }
    }
    Text(
        "While you hover a favourite, its show/movie artwork plays as the wallpaper. " +
            "Add an app to favourites to use its artwork.",
        color = colors.textDim,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth(0.85f),
    )

    if (!settings.useAppArtwork) return

    var reload by remember { mutableIntStateOf(0) }
    val granted = remember(reload) {
        ContextCompat.checkSelfPermission(context, TV_LISTINGS_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
    if (!granted) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reload++ }
        Text("Needs permission to read app artwork.", color = warningColor(colors.isDark), fontSize = 13.sp)
        ToggleChip("Grant permission", active = false) { launcher.launch(TV_LISTINGS_PERMISSION) }
        return
    }

    val availability by produceState<Map<String, AppArtwork>?>(initialValue = null, favoriteApps, reload) {
        value = TvArtwork.availability(context)
    }
    val avail = availability
    when {
        avail == null -> Text("Scanning…", color = colors.textDim, fontSize = 13.sp)
        else -> {
            val eligible = favoriteApps.filter { (avail[it.packageName]?.posters ?: 0) > 0 }
            if (eligible.isEmpty()) {
                Text("None of your favourites publish artwork yet.", color = colors.textDim, fontSize = 13.sp)
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    eligible.forEach { app ->
                        val on = app.packageName in settings.artworkApps
                        ArtworkAppRow(
                            label = app.label,
                            detail = artworkDetail(avail[app.packageName]),
                            on = on,
                            onClick = { onToggleArtworkApp(app.packageName, !on) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ArtworkAppRow(label: String, detail: String, on: Boolean, onClick: () -> Unit) {
    val colors = LocalLauncherColors.current
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) colors.onHighlight else colors.text
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.chip,
            focusedContainerColor = colors.highlight,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (detail.isNotEmpty()) Text(detail, color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Text(
                text = if (on) "On" else "Off",
                color = when {
                    focused -> colors.onHighlight
                    on -> okColor(colors.isDark)
                    else -> colors.textDim
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun artworkDetail(art: AppArtwork?): String {
    if (art == null) return ""
    return buildList {
        if (art.posters > 0) add("${art.posters} posters")
        if (art.videos > 0) add("${art.videos} videos")
    }.joinToString("  ·  ")
}

/**
 * Make Tarang the device's home screen: live status for the redirect accessibility service and the
 * default-Home app, with deep-links into the relevant system settings. Status refreshes whenever the
 * screen resumes (e.g. when returning from system settings after toggling the service).
 */
@Composable
private fun HomeSetupPane(
    onOpenAccessibility: () -> Unit,
    onChooseHome: (() -> Unit)?,
) {
    val colors = LocalLauncherColors.current
    val status = rememberHomeSetupStatus()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        PaneTitle("Home setup")
        Text(
            "Make Tarang your TV's home screen. Google TV won't let an app set itself as the default " +
                "Home, so Tarang uses an accessibility service to return here whenever the stock " +
                "launcher appears.",
            color = colors.textDim,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(0.8f),
        )

        StatusRow("Home redirect service", ok = status.isRedirectEnabled, okText = "On", offText = "Off")
        Text(
            "Bounces back to Tarang whenever the stock launcher comes forward — the reliable way to run " +
                "Tarang as Home here. Open Accessibility, find Tarang, and turn it on.",
            color = colors.textDim,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        ToggleChip(
            if (status.isRedirectEnabled) "Open accessibility settings" else "Enable redirect service",
            active = false,
        ) { onOpenAccessibility() }

        StatusRow("Default Home app", ok = status.isDefaultHome, okText = "Tarang", offText = "Not set")
        if (onChooseHome != null) {
            ToggleChip("Choose Home app", active = false) { onChooseHome() }
        } else {
            Text(
                "This device doesn't offer a Home-app chooser, so the redirect service above is what " +
                    "keeps Tarang in front.",
                color = colors.textDim,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
        }

        Spacer(Modifier.height(44.dp))
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String, offText: String) {
    val colors = LocalLauncherColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(
            text = if (ok) okText else offText,
            color = if (ok) okColor(colors.isDark) else warningColor(colors.isDark),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class HomeSetupStatus(val isDefaultHome: Boolean, val isRedirectEnabled: Boolean)

/** Reads the home-setup status, re-reading each time the screen resumes (e.g. back from settings). */
@Composable
private fun rememberHomeSetupStatus(): HomeSetupStatus {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var nonce by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) nonce++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(nonce) {
        HomeSetupStatus(
            isDefaultHome = HomeSetup.isDefaultHome(context),
            isRedirectEnabled = HomeSetup.isRedirectServiceEnabled(context),
        )
    }
}

/**
 * Manage apps the user has hidden from the grid: list them with an "Unhide" action. Hiding itself
 * happens from the home-screen long-press menu; this is where they come back.
 */
@Composable
private fun HiddenAppsPane(hiddenApps: List<AppInfo>, onUnhide: (String) -> Unit) {
    val colors = LocalLauncherColors.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        PaneTitle("Hidden apps")
        Text(
            "Apps hidden from the home grid. They stay installed and keep working — unhide to show one again.",
            color = colors.textDim,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        if (hiddenApps.isEmpty()) {
            Text(
                "Nothing hidden. Long-press an app on the home screen and choose “Hide app”.",
                color = colors.textDim,
                fontSize = 13.sp,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hiddenApps.sortedBy { it.label.lowercase() }.forEach { app ->
                    HiddenAppRow(label = app.label) { onUnhide(app.packageName) }
                }
            }
        }
        Spacer(Modifier.height(44.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HiddenAppRow(label: String, onUnhide: () -> Unit) {
    val colors = LocalLauncherColors.current
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) colors.onHighlight else colors.text
    Surface(
        onClick = onUnhide,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.chip,
            focusedContainerColor = colors.highlight,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                text = "Unhide",
                color = if (focused) colors.onHighlight else okColor(colors.isDark),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DiagnosticsPane(onScanTvContent: () -> Unit) {
    val colors = LocalLauncherColors.current
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PaneTitle("Diagnostics")

        SectionLabel("TV content")
        Text(
            "Check whether installed apps publish home-screen rows (channels & preview programs) " +
                "this launcher can read — the basis for a content carousel.",
            color = colors.textDim,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        ToggleChip("Scan TV content", active = false) { onScanTvContent() }
    }
}

@Composable
private fun PaneTitle(text: String) {
    Text(text, color = LocalLauncherColors.current.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = LocalLauncherColors.current.textDim, fontSize = 15.sp, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PresetSwatch(
    preset: WallpaperPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 72.dp, height = 44.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = preset.base, focusedContainerColor = preset.base),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(preset.blobA, preset.blobB, preset.blobC))),
        ) {
            if (focused) FocusRing()
            if (selected) SelectedBadge()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhotoSwatch(
    thumb: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLauncherColors.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 72.dp, height = 44.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.chip,
            focusedContainerColor = colors.chip,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = "Photo wallpaper",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("＋ Photo", color = colors.text, fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
            if (focused) FocusRing()
            if (selected) SelectedBadge()
        }
    }
}

/** Focus (hover) indicator for swatches — a ring that appears only while focused. */
@Composable
private fun FocusRing() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(3.dp, LocalLauncherColors.current.text, RoundedCornerShape(14.dp)),
    )
}

/** Selected indicator for swatches — a check badge in the corner (distinct from the focus ring). */
@Composable
private fun BoxScope.SelectedBadge() {
    val colors = LocalLauncherColors.current
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(5.dp)
            .size(20.dp)
            .background(colors.highlight, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", color = colors.onHighlight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalLauncherColors.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(percent = 50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) colors.highlight else colors.chip,
            focusedContainerColor = colors.highlight,
        ),
    ) {
        Text(
            text = label,
            color = if (active || focused) colors.onHighlight else colors.text,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

private fun timeoutLabel(sec: Int) = when {
    sec <= 0 -> "Off"
    sec < 60 -> "${sec}s"
    else -> "${sec / 60} min"
}

private fun warningColor(isDark: Boolean) = if (isDark) Color(0xFFFFE082) else Color(0xFF8A6D00)
private fun okColor(isDark: Boolean) = if (isDark) Color(0xFF80E27E) else Color(0xFF1B8A3A)
