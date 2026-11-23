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
        val fluidPreview = project.findProperty("fluidPreview")?.toString() == "true"
        applicationId = when {
            fluidPreview -> "com.oddf0x.halynmotion.fluidpreview"
            livingPreview -> "com.oddf0x.halynmotion.livingpreview"
            previewBuild -> "com.oddf0x.halynmotion.quietpreview"
            else -> "com.oddf0x.halynmotion"
        }
        manifestPlaceholders["appTitle"] = when {
            fluidPreview -> "HALYN Fluid Preview"
            livingPreview -> "HALYN Living Preview"
            previewBuild -> "HALYN Quiet Preview"
            else -> "@string/app_name"
        }
        manifestPlaceholders["wallpaperTitle"] = when {
            fluidPreview -> "HALYN Fluid Wallpaper"
            livingPreview -> "HALYN Living Wallpaper"
            previewBuild -> "HALYN Quiet Preview Wallpaper"
            else -> "@string/wallpaper_name"
        }
        minSdk = 33
        targetSdk = 35
        versionCode = 38
        versionName = "0.32.0"
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

dependencies {
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    implementation("com.google.android.gms:play-services-base:18.11.1")
}
