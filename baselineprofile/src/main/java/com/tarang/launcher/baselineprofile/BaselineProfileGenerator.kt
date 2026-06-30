package com.tarang.launcher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records a Baseline Profile for the launcher: the classes/methods that run during cold start, the
 * first grid scroll, and an app launch + return. ART AOT-compiles exactly these at install time, so
 * the hot paths are native the first time they execute instead of being interpreted then JIT'd
 * mid-interaction — the main jank source on weak hardware (e.g. the Chromecast).
 *
 * Run with: ./gradlew :app:generateReleaseBaselineProfile
 * Output:   app/src/release/generated/baselineProfiles/baseline-prof.txt (packaged into the APK).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.tarang.launcher",
        // Also feed these methods into the startup profile (compiled even more aggressively).
        includeInStartupProfile = true,
    ) {
        // Cold start the home screen.
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Reveal and scroll the grid (D-pad DOWN slides it up; this exercises the LazyColumn/AppRow
        // render path and the frosted-glass dock).
        repeat(6) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        // Move across a row, then back up to the dock.
        repeat(4) {
            device.pressDPadRight()
            device.waitForIdle()
        }
        repeat(6) {
            device.pressDPadUp()
            device.waitForIdle()
        }

        // Launch the focused app and return — exercises the scale-up launch + spring return path.
        device.pressDPadCenter()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
