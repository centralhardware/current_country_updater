plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "me.centralhardware.telegram"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val ktorVersion = "3.2.2"

dependencies {
    implementation("dev.inmo:tgbotapi:27.0.0")
    implementation("com.github.centralhardware:ktgbotapi-commons:ef9a97ef58")

    implementation("dev.inmo:krontab:2.7.2")

    implementation("com.clickhouse:clickhouse-jdbc:0.9.0")
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("com.github.seratch:kotliquery:1.9.1")

    implementation("com.neovisionaries:nv-i18n:1.29")

    implementation("net.fellbaum:jemoji:1.7.4")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}
