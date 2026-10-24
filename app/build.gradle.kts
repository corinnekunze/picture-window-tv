import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The release key never lives in the repo. scripts/release.sh points this at a properties file
// (storeFile, storePassword, keyAlias, keyPassword); without one, release builds come out unsigned.
val signingFile = providers.environmentVariable("PICTURE_WINDOW_SIGNING")
    .orElse("${System.getProperty("user.home")}/.picture-window-tv/signing.properties")
    .map(::file)
    .get()
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use(::load) }

android {
    namespace = "com.mtdetc.picturewindow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mtdetc.picturewindow"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("pictureWindowVersionCode").get().toInt()
        versionName = providers.gradleProperty("pictureWindowVersionName").get()
    }

    signingConfigs {
        if (signingFile.exists()) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
