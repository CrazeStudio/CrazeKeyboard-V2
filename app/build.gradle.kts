plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.crazestudio.crazekeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crazestudio.crazekeyboard"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "2.0.0"
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
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
