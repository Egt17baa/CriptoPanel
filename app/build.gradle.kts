import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun loadProps(name: String) = Properties().apply {
    rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

val localProps = loadProps("local.properties")

// Upload key for Google Play: keystore.properties (never committed) or, on CI, environment variables.
val keystoreProps = loadProps("keystore.properties")
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "CRIPTOPANEL_KEYSTORE_FILE")

android {
    namespace = "app.criptopanel"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.criptopanel"
        minSdk = 26
        targetSdk = 36
        // Every Play upload needs a higher versionCode: pass -PversionCode=N (CI uses the run number).
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "1.9.3"
        buildConfigField("String", "COINGECKO_DEMO_KEY", "\"${localProps.getProperty("COINGECKO_DEMO_KEY", "")}\"")
    }

    signingConfigs {
        // Shared debug key (not secret, never accepted by Play) so local and CI test builds update each other.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "CRIPTOPANEL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "CRIPTOPANEL_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "CRIPTOPANEL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without an upload key the release build is signed with the debug key so it can be installed
            // for testing; Google Play rejects that signature, so a real upload always needs keystore.properties.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
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
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bouncycastle)
    implementation(libs.androidx.biometric)
    implementation(libs.code.scanner)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}
