import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(21)
    androidTarget()
    jvm("desktop")

    sourceSets {
        val desktopMain by getting
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.core.ktx)
            implementation(libs.coroutines.android)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.coroutines.swing)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "buddy.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "net.focusbuddy.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties", "META-INF/DEPENDENCIES")
    }
}

compose.desktop {
    application {
        mainClass = "buddy.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Rpm, TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "focus-buddy"
            packageVersion = "0.1.0"
            linux { iconFile.set(project.file("icon.png")) }
        }
    }
}
