plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

kotlin { jvmToolchain(21) }

application {
    mainClass.set("buddy.server.MainKt")
}

ktor {
    fatJar { archiveFileName.set("focus-buddy-server.jar") }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serialization.json)
    implementation(libs.sqlite.jdbc)
    implementation(libs.logback)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}
