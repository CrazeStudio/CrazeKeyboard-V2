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
        versionCode = 7
        versionName = "1.6"
        ndk {
            // Keep the runtime binaries for Android ARM devices; omit desktop emulator ABIs.
            abiFilters += setOf("armeabi-v7a", "arm64-v8a")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    androidResources {
        // Keep the compressed English dictionary asset unchanged inside the APK.
        noCompress += "gz"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions")
    }
}


dependencies {
    // LiteRT (TensorFlow Lite flatbuffer) CPU runtime for on-device CrazeLM inference.
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
}
