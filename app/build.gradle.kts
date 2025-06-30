import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Load environment variables from .env file
val envFile = rootProject.file(".env")
val envProps = Properties()

if (envFile.exists()) {
    envFile.inputStream().use { stream ->
        envProps.load(stream)
    }
}

android {
    namespace = "com.example.hama2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hama2"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    implementation("com.github.IsmaelDivita:chip-navigation-bar:1.4.0")
    implementation(libs.dotenv.kotlin)
    implementation (libs.androidx.localbroadcastmanager)
    implementation(libs.logging.interceptor.v490)
    implementation(libs.org.eclipse.paho.client.mqttv3)
    implementation(libs.org.eclipse.paho.android.service)
    implementation(libs.material.v1110)
    implementation(libs.okhttp.sse)
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation(libs.okhttp)
    implementation(libs.mpandroidchart)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}