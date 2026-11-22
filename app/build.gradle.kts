plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.oddf0x.halynmotion"
    compileSdk = 35
    defaultConfig {
        val previewBuild = project.findProperty("quietPreview")?.toString() == "true"
        val livingPreview = project.findProperty("livingPreview")?.toString() == "true"
        applicationId = when {
            livingPreview -> "com.oddf0x.halynmotion.livingpreview"
            previewBuild -> "com.oddf0x.halynmotion.quietpreview"
            else -> "com.oddf0x.halynmotion"
        }
        manifestPlaceholders["appTitle"] = when {
            livingPreview -> "HALYN Living Preview"
            previewBuild -> "HALYN Quiet Preview"
            else -> "@string/app_name"
        }
        manifestPlaceholders["wallpaperTitle"] = when {
            livingPreview -> "HALYN Living Wallpaper"
            previewBuild -> "HALYN Quiet Preview Wallpaper"
            else -> "@string/wallpaper_name"
        }
        minSdk = 33
        targetSdk = 35
        versionCode = 34
        versionName = "0.28.0"
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
