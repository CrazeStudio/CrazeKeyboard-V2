plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.crazestudio.crazekeyboard"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.crazestudio.crazekeyboard"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    androidResources {
        // CWP is a read-only memory-mapped language pack; keep it uncompressed in the APK.
        noCompress += "cwp"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions")
    }
}
