plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.oddf0x.halynmotion"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.oddf0x.halynmotion"
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
