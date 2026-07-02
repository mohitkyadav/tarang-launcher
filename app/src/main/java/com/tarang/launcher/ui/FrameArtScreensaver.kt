package com.tarang.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tarang.launcher.data.FrameSource
import com.tarang.launcher.data.LauncherSettings
import com.tarang.launcher.data.WeatherUnit

/**
 * The full-screen Frame Art "painting" as a standalone screen — the same art/clock/weather/dim the
 * launcher shows in Frame Art mode, but self-contained so it can also be hosted by the system
 * screensaver ([com.tarang.launcher.dream.FrameArtDreamService]). Always fully revealed (no chrome
 * transition to sequence against).
 */
@Composable
fun FrameArtScreensaver(
    settings: LauncherSettings,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val nav = remember { FrameNavState() }
    val motionOn = settings.frameMotion && !settings.reduceMotion
    val frameConfigured = (settings.frameSource == FrameSource.FOLDER && settings.frameFolderId != null) ||
        (settings.frameSource == FrameSource.SINGLE && settings.frameImagePath != null)
    val weather = rememberWeather(
        enabled = settings.frameWeather,
        fahrenheit = settings.weatherUnit == WeatherUnit.FAHRENHEIT,
        lat = settings.weatherLat,
        lon = settings.weatherLon,
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Base: the configured art (folder/single), else the current photo/gradient wallpaper.
        when {
            frameConfigured -> FrameArtContent(
                settings = settings,
                drift = motionOn,
                driftAmount = { 1f },
                cycle = true,
                isDark = isDark,
                nav = nav,
                modifier = Modifier.fillMaxSize(),
            )

            settings.useImageWallpaper && settings.wallpaperImagePath != null -> ImageWallpaper(
                path = settings.wallpaperImagePath!!,
                isDark = isDark,
                modifier = Modifier.fillMaxSize(),
            )

            else -> AnimatedWallpaper(
                preset = WallpaperPresets.getOrElse(settings.wallpaperId) { WallpaperPresets.first() },
                animated = false,
                ambient = null,
                isDark = isDark,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Dim the art (not the clock) during the deep-night hours.
        if (settings.frameNightDim) NightDimScrim(modifier = Modifier.fillMaxSize())

        if (settings.frameClock) {
            FrameClock(
                position = settings.frameClockPosition,
                size = settings.frameClockSize,
                showDate = settings.frameShowDate,
                weather = weather,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
