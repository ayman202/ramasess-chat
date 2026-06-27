
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.chat"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.compression)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.requestValidation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.websockets)
///////////////////////////////////////////////////
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.java.time)
    implementation(libs.logback.classic)
    implementation(libs.postgresql)
    implementation(libs.commons.logging)

    implementation(libs.koin.ktor)
    implementation(libs.koin.logger)
    implementation(libs.bcprov.jdk18on)
    implementation(libs.crypto)
    implementation(libs.java.jwt)
    implementation(libs.lettuce.core)
    implementation(libs.hikariCP)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
