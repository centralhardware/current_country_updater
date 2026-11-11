plugins {
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
    id("com.google.cloud.tools.jib") version "3.5.4" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    group = "me.centralhardware.telegram"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        "testImplementation"(kotlin("test"))
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:6.1.3")
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    }

    tasks.withType<Test> { useJUnitPlatform() }
}
