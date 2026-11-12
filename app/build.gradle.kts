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
        versionCode = 4
        versionName = "1.3"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    androidResources {
        // The tokenizer model is already gzip-compressed. Avoid compressing it a second time in the APK.
        // The app expands it once into private storage and memory-maps the resulting binary index.
        noCompress += "gz"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions")
    }
}
