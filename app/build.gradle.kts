plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val ciDebugStoreFile = providers.environmentVariable("WPM_PLUS_DEBUG_STORE_FILE")
val ciDebugStorePassword = providers.environmentVariable("WPM_PLUS_DEBUG_STORE_PASSWORD")
val ciDebugKeyAlias = providers.environmentVariable("WPM_PLUS_DEBUG_KEY_ALIAS")
    .orElse("wpm-plus-ci-debug")
val ciDebugKeyPassword = providers.environmentVariable("WPM_PLUS_DEBUG_KEY_PASSWORD")
val useCiDebugSigning = listOf(
    ciDebugStoreFile.orNull,
    ciDebugStorePassword.orNull,
    ciDebugKeyAlias.orNull,
    ciDebugKeyPassword.orNull,
).all { !it.isNullOrBlank() } && ciDebugStoreFile.orNull?.let { rootProject.file(it).isFile } == true

val releaseStoreFile = providers.environmentVariable("WPM_PLUS_RELEASE_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("WPM_PLUS_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("WPM_PLUS_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("WPM_PLUS_RELEASE_KEY_PASSWORD")
val useReleaseSigning = listOf(
    releaseStoreFile.orNull,
    releaseStorePassword.orNull,
    releaseKeyAlias.orNull,
    releaseKeyPassword.orNull,
).all { !it.isNullOrBlank() } && releaseStoreFile.orNull?.let { rootProject.file(it).isFile } == true

android {
    namespace = "com.sampple.wifivaultrestore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sampple.wifivaultrestore"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "0.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        if (useCiDebugSigning) {
            create("ciDebug") {
                storeFile = rootProject.file(ciDebugStoreFile.get())
                storePassword = ciDebugStorePassword.get()
                keyAlias = ciDebugKeyAlias.get()
                keyPassword = ciDebugKeyPassword.get()
            }
        }
        if (useReleaseSigning) {
            create("releaseUpload") {
                storeFile = rootProject.file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            if (useCiDebugSigning) {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (useReleaseSigning) {
                signingConfig = signingConfigs.getByName("releaseUpload")
            }
        }
    }

    buildFeatures {
        aidl = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
