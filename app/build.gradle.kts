import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Surface the pinned SingR core version as the app's versionName suffix, so a
// build self-reports which core it carries (matches SINGR_VERSION verbatim).
val singrVersion = rootProject.file("SINGR_VERSION").readText().trim().removePrefix("v")

// Optional signing config from keystore.properties (git-ignored). Absent → the
// release build stays unsigned (CI injects real signing via secrets).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.singr.node"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.singr.node"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0+singr$singrVersion"

        ndk {
            // Node boxes are arm64. Add "armeabi-v7a" here (and the matching
            // SingR release artifact) if you need 32-bit devices.
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }

    // CRITICAL: extract libsingr.so to nativeLibraryDir so it can be exec'd.
    // Without legacy packaging the .so stays compressed inside the APK and is
    // not a runnable file on disk.
    packaging {
        jniLibs {
            useLegacyPackaging = true
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
}
