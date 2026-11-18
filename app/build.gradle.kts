import java.util.Properties

plugins {
    id("com.android.application")
    // No separate org.jetbrains.kotlin.android plugin here: AGP 9.0+ has
    // built-in Kotlin support and applying both now conflicts
    // ("Cannot add extension with name 'kotlin'").
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release version lives in /version.properties (see docs/RELEASING.md).
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME").trim()
val appVersionCode: Int = appVersionName.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    major * 10000 + minor * 100 + patch
}

// Release signing comes from environment variables (GitHub Actions secrets or
// your own shell), never from a file in the repo. Without them a release build
// is simply unsigned and the workflow ships the debug-signed APK instead.
val releaseKeystorePath: String? = System.getenv("TIFFINBOX_KEYSTORE_PATH")

android {
    namespace = "com.ratulhossen.tiffinbox"
    // vico:compose 3.x moved to Compose Multiplatform and its resolved
    // AndroidX Compose transitive deps require compileSdk 37 / AGP 9.1.0+
    // (see the matching com.android.application version bump in the root
    // build.gradle.kts) — bumped from 34 to clear AAR metadata check
    // failures, not a deliberate feature choice.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ratulhossen.tiffinbox"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseKeystorePath != null && file(releaseKeystorePath).exists()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("TIFFINBOX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TIFFINBOX_KEY_ALIAS")
                keyPassword = System.getenv("TIFFINBOX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // No separate kotlinOptions { jvmTarget = "17" } block: that was part of
    // the org.jetbrains.kotlin.android plugin we removed above. AGP 9's
    // built-in Kotlin support picks up the JVM target from compileOptions
    // above instead.

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Bumped from 2024.06.00: vico:compose 3.3.1 resolves Compose runtime to
    // 1.12.0 (HorizontalPager's old signature is gone there), so compile-time
    // and runtime Compose versions must match or the app crashes on launch.
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Lottie: plays the app's own vector motion graphics (res/raw/lottie_*.json). Free, Apache-2.0.
    implementation("com.airbnb.android:lottie-compose:6.6.0")
    // OpenStreetMap map view for "chefs near me" - free, no API key, no billing (not Google Maps).
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.patrykandpatrick.vico:compose:3.3.1")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Plain Play Services location (FusedLocationProviderClient) + Android's
    // own on-device Geocoder for the free, no-map "nearby chefs" feature -
    // distinct from Google Maps Platform, so it needs no Google Cloud
    // billing account.
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Real "Continue with Google" sign-in (Credential Manager is the
    // Google-recommended replacement for the older GoogleSignInClient API).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// The Google Services plugin hard-fails Gradle sync if google-services.json is
// missing, so it's only applied once that file actually exists. Add the file
// (from your Firebase console project, package name com.ratulhossen.tiffinbox)
// to this app/ directory, re-sync, and Firebase will initialize automatically.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
