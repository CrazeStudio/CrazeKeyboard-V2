import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.crazekeyboard.vxtq"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}

// Keep exactly one APK distribution directory: /download.
tasks.register("uploadApkToDownload") {
  description = "Copies the latest APK to the single download directory and removes duplicate APKs elsewhere."
  group = "distribution"

  val rootDirPath = rootProject.projectDir.absolutePath
  val appBuildDirPath = layout.buildDirectory.get().asFile.absolutePath

  doLast {
    val rootDir = File(rootDirPath)
    val downloadDir = File(rootDir, "download")
    if (!downloadDir.exists()) downloadDir.mkdirs()

    val outputDirs = listOf(
      File(appBuildDirPath, "outputs/apk"),
      File(rootDir, ".build-outputs")
    )

    val builtApk = outputDirs.flatMap { dir ->
      if (dir.exists()) dir.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList() else emptyList()
    }.maxByOrNull { it.lastModified() }

    if (builtApk != null && builtApk.exists()) {
      downloadDir.listFiles()?.forEach { file ->
        if (file.isFile && file.extension == "apk" && file.name != "CrazeKeyboard.apk") file.delete()
      }
      builtApk.copyTo(File(downloadDir, "CrazeKeyboard.apk"), overwrite = true)

      rootDir.listFiles()?.forEach { file ->
        if (file.isFile && file.extension == "apk") file.delete()
      }
      println("Generated latest CrazeKeyboard.apk in /download only.")
    }
  }
}

tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
  finalizedBy("uploadApkToDownload")
}
