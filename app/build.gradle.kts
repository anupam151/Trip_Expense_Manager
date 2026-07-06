import org.gradle.api.tasks.compile.JavaCompile // <-- 1. This import is required

plugins {
    alias(libs.plugins.android.application)
}

// Keep these as constants at the top of your file.
val androidCompileSdk = 37
val androidTargetSdk = 37
val androidMinSdk = 26

android {
    namespace = "com.example.tripexpensemanager"
    compileSdk = androidCompileSdk

    defaultConfig {
        applicationId = "com.example.tripexpensemanager"
        minSdk = androidMinSdk
        targetSdk = androidTargetSdk
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

// <-- 2. The perfectly formatted Kotlin syntax with angle brackets
tasks.withType<JavaCompile>().configureEach {
    options.isWarnings = false
    options.compilerArgs.add("-Xlint:-deprecation")
}