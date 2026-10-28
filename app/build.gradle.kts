plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing values come from the CI environment (GitHub Secrets), never from the repository.
val signingKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "io.github.ard9.linguaplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ard9.linguaplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"

        // 64-bit ARM only: keeps the APK small (libVLC is large per ABI).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (signingKeystorePath != null) {
            create("release") {
                storeFile = file(signingKeystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinking and optimization without obfuscation (proguard-rules.pro).
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Native libraries (libVLC, ONNX Runtime: ~74 MB) are compressed in the APK, which halves
    // the download; Android unpacks them at install (more space used on the phone).
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { test ->
            test.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val media3 = "1.5.1"
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // The optional daily review reminder (survives restarts of the phone).
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Video playback (all formats VLC supports).
    implementation("org.videolan.android:libvlc-all:3.7.6")

    // Finds sentence boundaries by detecting speech (Silero VAD model in assets).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    // Audio clipping (fast path) and playing saved clips.
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-transformer:$media3")

    testImplementation("junit:junit:4.13.2")
    // The real org.json; Android's copy is only a stub in local unit tests.
    testImplementation("org.json:json:20240303")
}
