plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.21"
    application
}

group = "me.centralhardware.telegram"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val clickhouseVersion = "0.8.5"

dependencies {
    implementation("dev.inmo:tgbotapi:24.0.2")
    implementation("com.github.centralhardware:ktgbotapi-commons:6ef1dde4fe")

    implementation("dev.inmo:krontab:2.7.2")

    implementation("com.clickhouse:clickhouse-jdbc:$clickhouseVersion")
    implementation("com.clickhouse:clickhouse-http-client:$clickhouseVersion")
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("com.github.seratch:kotliquery:1.9.1")

    implementation("com.neovisionaries:nv-i18n:1.29")

    implementation("net.fellbaum:jemoji:1.7.3")

    implementation("io.ktor:ktor-client-core:3.1.2")
    implementation("io.ktor:ktor-client-cio:3.1.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}
