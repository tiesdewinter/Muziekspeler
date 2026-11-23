plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "nl.muziekslot"
    compileSdk = 34
    defaultConfig {
        applicationId = "nl.muziekslot"
        minSdk = 27
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
