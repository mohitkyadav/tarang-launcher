import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

// Self-update (see UpdateInstaller) checks that a downloaded APK is signed with the same
// certificate as the installed app. That only holds across releases if every release uses the
// same stable key, so release builds sign with a dedicated keystore instead of the ephemeral
// per-machine debug key. Reads from keystore.properties, which is never committed (see
// .gitignore) — see README-signing.md for how to generate that keystore and file.
val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.tarang.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tarang.launcher"
        minSdk = 28
        targetSdk = 35
        versionCode = 25
        versionName = "0.3.16"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 strips Compose's debug and source-information calls and inlines small functions.
            // Without it every composition did that extra work (64% janky frames on a 32-bit MiTV).
            isMinifyEnabled = true
            isShrinkResources = true
            // Falls back to the debug key when keystore.properties is absent (e.g. a fresh
            // checkout before the release keystore is set up), so the project still builds.
            signingConfig = if (keystoreProperties.containsKey("storeFile")) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.datastore.preferences)
    // Applies the bundled baseline profile on-device (esp. for sideloaded APKs, which don't get
    // Play's install-time AOT — ProfileInstaller writes it and ART compiles during idle).
    implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // The :baselineprofile module produces app/src/<variant>/generated/baselineProfiles/*.txt,
    // which the baselineprofile plugin merges into the release APK at build time.
    "baselineProfile"(project(":baselineprofile"))
}
