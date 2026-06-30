import com.android.build.api.dsl.ManagedVirtualDevice

// A `com.android.test` module whose instrumented "user journey" is run by the baselineprofile
// plugin to record which classes/methods execute on startup + first scroll + the launch animation.
// The recorded profile is written back into the app's release source set and packaged into the APK.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.tarang.launcher.baselineprofile"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The app under test.
    targetProjectPath = ":app"

    // Hermetic fallback: a known-good rooted AOSP emulator the plugin can provision itself. Defined
    // but not wired into `baselineProfile` below — enable it there if you'd rather not connect a device.
    @Suppress("UnstableApiUsage")
    testOptions.managedDevices.devices {
        create<ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    // Record on a connected/rooted device or emulator (run: ./gradlew :app:generateReleaseBaselineProfile
    // with a rooted emulator running — e.g. the bundled `tarang_tv` Android-TV AVD). Profile capture
    // needs root, which AOSP / Google APIs / Android-TV images allow but Google Play images do not.
    useConnectedDevices = true

    // Hermetic alternative (no connected device needed) — provisions pixel6Api34 above; downloads its
    // system image on first run:
    //   managedDevices += "pixel6Api34"
    //   useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
