plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Surface the pinned SingR core version as the app's versionName suffix, so a
// build self-reports which core it carries (matches SINGR_VERSION verbatim).
val singrVersion = rootProject.file("SINGR_VERSION").readText().trim().removePrefix("v")

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
        create("release") {
            // Committed, shared keystore so every build has the SAME signature
            // and the node APK upgrades in place. The password is public on
            // purpose (self-use node app) — the only downside is that anyone
            // could sign an update with it, which is acceptable here.
            storeFile = file("release.jks")
            storePassword = "singr-node"
            keyAlias = "singr"
            keyPassword = "singr-node"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
    // Don't let lintVitalRelease fail the release assemble in CI.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
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
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
