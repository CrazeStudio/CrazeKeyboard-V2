plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Use the cached CI debug keystore when present. If a developer has no keystore yet,
// keep Android Gradle Plugin's normal local debug-signing behavior.
val stableDebugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")

android {
    signingConfigs {
        if (stableDebugKeystore.exists()) {
            create("stableDebug") {
                storeFile = stableDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    namespace = "com.crazestudio.crazekeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crazestudio.crazekeyboard"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "2.0.0"
    }

    buildTypes {
        getByName("debug") {
            // CI creates/caches this key before Gradle configuration. Local builds
            // without it continue to use Android Gradle Plugin's default debug key.
            if (stableDebugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
