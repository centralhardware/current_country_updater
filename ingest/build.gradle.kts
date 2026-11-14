// The web server: OwnTracks POST /pub, the JSON Schema contract, reverse
// geocoding, the calendar feed, and the storage the bot also reads. Its own
// container; no Telegram here.
plugins {
    id("com.google.cloud.tools.jib")
}

val ktorVersion = "3.6.1"
val clickhouseVersion = "0.11.1"

dependencies {
    api("dev.inmo:kslog:2.1.0")

    api("com.clickhouse:clickhouse-jdbc:$clickhouseVersion")
    implementation("com.clickhouse:clickhouse-http-client:$clickhouseVersion")
    api("com.github.seratch:kotliquery:1.9.1")
    api("org.postgresql:postgresql:42.7.14")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.flywaydb:flyway-core:13.12.1")
    implementation("org.flywaydb:flyway-database-clickhouse:10.26.2")
    implementation("org.flywaydb:flyway-database-postgresql:13.12.1")

    api("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("com.networknt:json-schema-validator:3.0.9")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.mnode.ical4j:ical4j:4.4.0")
    api("com.neovisionaries:nv-i18n:1.29")
    api("net.fellbaum:jemoji:2.1.0")
}

jib {
    from {
        image = System.getenv("JIB_FROM_IMAGE") ?: "eclipse-temurin:24-jre"
    }
    to {
    }
    container {
        mainClass = "WebMainKt"
        // POST /pub, the calendar feed and /health all on one port
        ports = listOf("80")
        environment = mapOf("HEALTHCHECK_PORT" to "80")
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

