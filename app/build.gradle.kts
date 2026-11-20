plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase (Analytics, Crashlytics, Cloud Messaging) reads its project keys from app/google-services.json,
// downloaded from the Firebase console for this applicationId. Without the file the game still builds and
// runs: every Firebase feature simply stays off (see analytics/Telemetry.kt).
val appId = "com.ngstudio.frostyroll"
val firebaseConfig = file("google-services.json")
// The file must contain a Firebase app registered for this exact package name; a file made for another package
// would fail the build ("No matching client found"), so it is skipped with a warning instead.
val hasFirebaseConfig = firebaseConfig.exists() &&
    Regex("\"package_name\"\\s*:\\s*\"${Regex.escape(appId)}\"").containsMatchIn(firebaseConfig.readText())
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
} else if (firebaseConfig.exists()) {
    logger.warn(
        "app/google-services.json has no Firebase app for package '$appId': building WITHOUT Firebase. " +
            "In the Firebase console add an Android app with package '$appId' and download its google-services.json."
    )
} else {
    logger.warn("app/google-services.json is missing: building without Firebase (analytics, crash reports and push are off).")
}

android {
    // Internal code package (R / BuildConfig). The store / Firebase package name is applicationId below.
    namespace = "com.ngstudio.snowrace"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = appId
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", hasFirebaseConfig.toString())
    }

    buildTypes {
        debug {
            // Debug builds: mock ads + debug menu.
            buildConfigField("boolean", "MOCK_ADS", "true")
        }
        release {
            // Release builds never show simulated ads.
            buildConfigField("boolean", "MOCK_ADS", "false")
            optimization {
                enable = true
            }
            // Signing: add your release signingConfig here before publishing.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // Keep audio uncompressed for fast loading.
        noCompress += listOf("wav")
    }
    testOptions {
        unitTests.all {
            it.maxHeapSize = "2g"
            it.systemProperty("fullLevelValidation", System.getProperty("fullLevelValidation") ?: "false")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.play.app.update)
    implementation(libs.androidx.work.runtime)
    // Play / Firebase pull in fragment 1.1; activity-result APIs need 1.3+ to deliver results correctly
    implementation(libs.androidx.fragment)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
