plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Properties
import java.io.FileInputStream

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}

fun propOrEnv(key: String, default: String): String =
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: default

android {
    namespace = "com.veplayer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.veplayer.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.10.0"
        buildConfigField("String", "SENSEFLOW_URL", "\"http://10.0.2.2:4100\"")
        buildConfigField("String", "PLAYER_URL", "\"https://vescreenflow.com/play\"")
        buildConfigField(
            "String",
            "SPOTIFY_CLIENT_ID",
            "\"${propOrEnv("SPOTIFY_CLIENT_ID", "YOUR_SPOTIFY_CLIENT_ID")}\"",
        )
        buildConfigField(
            "String",
            "SPOTIFY_REDIRECT_URI",
            "\"${propOrEnv("SPOTIFY_REDIRECT_URI", "veplayer://callback")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(files("libs/spotify-auth-release-2.1.0.aar"))
    implementation("com.google.code.gson:gson:2.11.0")

    // Vision: MediaPipe Object Detector (person / bicycle / car / motorcycle / bus / truck)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
