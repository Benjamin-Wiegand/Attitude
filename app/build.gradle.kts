plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.benwiegand.attitude"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.benwiegand.attitude"
        minSdk = 28
        //noinspection ExpiredTargetSdkVersion: SDK 29 breaks WifiManager
        targetSdk = 28
        versionCode = 3
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.preference)
    implementation(libs.libadb)
    implementation(libs.bouncycastle)
    implementation(libs.conscrypt)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}