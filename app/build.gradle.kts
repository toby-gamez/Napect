plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tkolymp.napect"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.tkolymp.napect"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Configure Kotlin compiler options using the compilerOptions DSL (recommended for Kotlin Gradle Plugin 2.x)
kotlin {
    // Ensure we use Java 11 toolchain for Kotlin compilation
    jvmToolchain(11)
    compilerOptions {
        // Set JVM target to Java 11 using the typed JvmTarget enum
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    // Navigation for Compose (via version catalog)
    implementation(libs.androidx.navigation.compose)
    // Animated navigation handled by AndroidX navigation-compose now; remove accompanist
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // CameraX removed in favor of using the platform camera app (ActivityResultContracts.TakePicture)
    // ML Kit text recognition (on-device) via version catalog
    implementation(libs.mlkit.text.recognition)
    // (Hilt removed for now)
    // Network (URL import) via version catalog
    implementation(libs.okhttp)
    // Image decoding (for reading picked images) - removed unused Glide dependency
    // DataStore Preferences for app settings via version catalog
    implementation(libs.androidx.datastore.preferences)
    // Firebase BOM and AI SDK (via version catalog)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
