plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.tarang.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tarang.launcher"
        minSdk = 28
        targetSdk = 35
        versionCode = 8
        versionName = "0.2.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sign release with the debug key so it can be sideloaded directly (this is a personal
            // launcher, not a Play Store app). A release build is far faster than debug on-device.
            signingConfig = signingConfigs.getByName("debug")
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
