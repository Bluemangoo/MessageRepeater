import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
    id("com.google.devtools.ksp") version "2.3.6"
    kotlin("plugin.serialization") version "2.3.10"
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "compose-group-mapping") {
            useVersion("2.3.10")
        }
    }
}

android {
    namespace = "net.bluemangoo.messagerepeater"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.bluemangoo.messagerepeater"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget("11"))
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    implementation(platform("androidx.compose:compose-bom:2026.03.00"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
}