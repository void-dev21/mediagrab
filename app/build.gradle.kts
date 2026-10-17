plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mediagrab"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mediagrab"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // yt-dlp / ffmpeg binaries are shipped per CPU architecture.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        // Required so the bundled native binaries can be extracted and executed.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // yt-dlp + ffmpeg wrapper for Android
    val ytdlVersion = "0.17.2"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlVersion")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdlVersion")
}
