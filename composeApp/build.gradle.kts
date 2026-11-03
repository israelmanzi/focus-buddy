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
        all { languageSettings.optIn("kotlin.time.ExperimentalTime") }
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
            // StatusNotifierItem tray: D-Bus over the session bus's unix socket.
            implementation(libs.dbus.java.core)
            implementation(libs.dbus.java.transport.native.unixsocket)
            implementation(libs.slf4j.nop)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            // Scenario/sync harness: run the real Ktor server in-process.
            implementation(project(":server"))
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.auth)
            implementation(libs.ktor.serialization.json)
            implementation(libs.sqlite.jdbc)
            implementation(libs.logback)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
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
        versionCode = 2
        versionName = "0.2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // CI provides a real key through BUDDY_KEYSTORE + passwords; local builds fall back to the
    // debug key so `assembleRelease` keeps working without secrets. Note: an APK signed with a
    // different key cannot be installed over an existing one.
    val ciKeystore = System.getenv("BUDDY_KEYSTORE")?.let { file(it) }
    signingConfigs {
        if (ciKeystore != null && ciKeystore.exists()) {
            create("release") {
                storeFile = ciKeystore
                storePassword = System.getenv("BUDDY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BUDDY_KEY_ALIAS") ?: "focus-buddy"
                keyPassword = System.getenv("BUDDY_KEY_PASSWORD") ?: System.getenv("BUDDY_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties", "META-INF/DEPENDENCIES")
    }
    androidResources {
        // Only the desktop tray reads this drawable; aapt drops it from the phone APK.
        ignoreAssetsPattern = "focus_buddy.png"
    }
}

compose.desktop {
    application {
        mainClass = "buddy.app.MainKt"
        jvmArgs += listOf(
            "-Xms64m", "-Xmx256m",
            "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Rpm, TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "focus-buddy"
            packageVersion = "0.1.0"
            linux { iconFile.set(project.file("icon.png")) }
        }
    }
}

compose.resources {
    packageOfResClass = "buddy.resources"
}
