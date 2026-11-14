// The Telegram bot: channel title, commands, /map, the calendar feed. Hosts the
// ingest module's /pub in the same process and container.
plugins {
    id("com.google.cloud.tools.jib")
}

val ktorVersion = "3.6.1"

dependencies {
    implementation(project(":ingest"))
    implementation("com.github.centralhardware:ktgbotapi-commons:6018de0c")

    implementation("dev.inmo:krontab:3.1.0")
    implementation("com.neovisionaries:nv-i18n:1.29")
    implementation("net.fellbaum:jemoji:2.1.0")

    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    implementation("org.mnode.ical4j:ical4j:4.4.0")
}

jib {
    from {
        image = System.getenv("JIB_FROM_IMAGE") ?: "eclipse-temurin:24-jre"
    }
    to {
    }
    container {
        mainClass = "MainKt"
        // /health, served by ktgbotapi-commons
        ports = listOf("8081")
        jvmFlags = listOf("-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
        creationTime = "USE_CURRENT_TIMESTAMP"
        labels = mapOf(
            "org.opencontainers.image.source" to (System.getenv("GITHUB_SERVER_URL")?.let { server ->
                val repo = System.getenv("GITHUB_REPOSITORY")
                if (repo != null) "$server/$repo" else ""
            } ?: ""),
            "org.opencontainers.image.revision" to (System.getenv("GITHUB_SHA") ?: "")
        )
        user = "10001"
    }
}

