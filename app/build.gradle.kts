
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The Meta Model API key comes from the environment or the gitignored .env at the repo root.
val museKey: String = System.getenv("MODEL_API_KEY") ?: run {
    listOf(rootProject.file(".env"), rootProject.file("../.env"))
        .filter { it.exists() && it.length() > 0 }
        .map { f ->
            f.readLines().map { it.trim().removePrefix("export ").trim() }
                .firstOrNull { it.startsWith("MODEL_API_KEY") }
                ?.substringAfter("=")?.trim()?.trim('"', '\'') ?: ""
        }
        .firstOrNull { it.isNotBlank() } ?: ""
}

android {
    namespace = "app.moment.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.moment.launcher"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "MODEL_API_KEY", "\"$museKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    val bom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
