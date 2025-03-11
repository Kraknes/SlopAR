plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-android")
//    id("com.google.gms.google-services") // If using Firebase or Google services
}

android {
    namespace = "com.example.ny_slopar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ny_slopar"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") // Ensure native support
        }
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx.v262)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.scenecore)
    implementation(libs.protolite.well.known.types)
    testImplementation(libs.junit)
    implementation(libs.material.v1100)
    implementation(libs.core) // ARCore
    implementation(libs.filament.android) // Main Filament library
    implementation(libs.gltfio.android) // GLTF support
    implementation(libs.filament.utils.android) // 🔥 Utility functions
    implementation(libs.play.services.location) // GPS
    implementation(libs.retrofit) // API Requests
    implementation(libs.converter.gson) // JSON Parsing
    implementation(libs.play.services.maps)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.appcompat)
    implementation(libs.material.v1110) // Latest MaterialComponents
    implementation(libs.filament.android.v1571) // Main Filament
    implementation(libs.gltfio.android.v1571) // GLTF support
    implementation(libs.filament.utils.android.v1571) // Utility functions
    implementation(libs.okhttp3.okhttp) // ✅ Ensure OkHttp is included

}