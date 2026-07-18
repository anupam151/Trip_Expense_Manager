import org.gradle.api.tasks.compile.JavaCompile

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val androidCompileSdk = 36
val androidTargetSdk = 36
val androidMinSdk = 26

android {
    namespace = "com.example.tripexpensemanager"
    compileSdk = androidCompileSdk

    // Signing config MUST be defined at the top level inside android {}
    signingConfigs {
        create("release") {
            // Use the absolute path as a test
            storeFile = file("../keystore/trip_expense_manager.jks")
            storePassword = "Anupam@1997"
            keyAlias = "tripexpensemanager"
            keyPassword = "Anupam@1997"
        }
    }

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
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true

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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        ignoreWarnings = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
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

    implementation(libs.googleAuth)
    implementation(libs.google.api.client)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.http.gson)

    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))

    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")


    val workVersion = "2.9.0"
    implementation("androidx.work:work-runtime:$workVersion")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    implementation("androidx.biometric:biometric:1.2.0-alpha05")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-deprecation")
}