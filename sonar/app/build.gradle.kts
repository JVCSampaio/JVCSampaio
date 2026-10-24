import java.time.LocalDate
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// CI runs pass GITHUB_RUN_NUMBER so every build installs as an update over the previous one.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "io.github.jvcsampaio.sonar"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.jvcsampaio.sonar"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.1.$buildNumber"
        // Shown on the identification plate (Placa) as the date of manufacture.
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now()}\"")
    }

    signingConfigs {
        // The committed keystore keeps the SHA-1 stable, which the Google OAuth client needs.
        // Set SONAR_KEYSTORE_FILE (and the passwords) to sign with a private key instead.
        create("sonar") {
            storeFile = System.getenv("SONAR_KEYSTORE_FILE")?.let { file(it) }
                ?: rootProject.file("keystore/sonar.jks")
            storePassword = System.getenv("SONAR_KEYSTORE_PASSWORD") ?: "sonar-public"
            keyAlias = System.getenv("SONAR_KEY_ALIAS") ?: "sonar"
            keyPassword = System.getenv("SONAR_KEY_PASSWORD") ?: "sonar-public"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sonar")
        }
        debug {
            signingConfig = signingConfigs.getByName("sonar")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        // yt-dlp, Python and ffmpeg ship as native libraries that must be extracted to disk.
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.auth)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)

    testImplementation(libs.junit)
}
