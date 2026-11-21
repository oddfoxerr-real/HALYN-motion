plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.oddf0x.halynmotion"
    compileSdk = 35
    defaultConfig {
        val previewBuild = project.findProperty("quietPreview")?.toString() == "true"
        applicationId = if (previewBuild) "com.oddf0x.halynmotion.quietpreview" else "com.oddf0x.halynmotion"
        manifestPlaceholders["appTitle"] = if (previewBuild) "HALYN Quiet Preview" else "@string/app_name"
        manifestPlaceholders["wallpaperTitle"] = if (previewBuild) "HALYN Quiet Preview Wallpaper" else "@string/wallpaper_name"
        minSdk = 33
        targetSdk = 35
        versionCode = 33
        versionName = "0.27.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
