import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val ksProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

// Environment variable first, then keystore.properties (same order as release.sh)
fun signing(prop: String, env: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: ksProps.getProperty(prop)?.takeIf { it.isNotBlank() }

android {
    namespace = "dev.zbxmon"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.zbxmon"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("zbxmon.versionCode").get().toInt()
        versionName = providers.gradleProperty("zbxmon.versionName").get()
    }

    signingConfigs {
        create("release") {
            signing("storeFile", "ZBXMON_KEYSTORE_FILE")?.let { storeFile = rootProject.file(it) }
            storePassword = signing("storePassword", "ZBXMON_KEYSTORE_PASSWORD")
            keyAlias = signing("keyAlias", "ZBXMON_KEY_ALIAS")
            keyPassword = signing("keyPassword", "ZBXMON_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signing("storeFile", "ZBXMON_KEYSTORE_FILE") != null)
                signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
