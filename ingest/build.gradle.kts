// OwnTracks intake: POST /pub, the JSON Schema contract, reverse geocoding,
// and the storage it writes to. No Telegram here.
val ktorVersion = "3.6.1"
val clickhouseVersion = "0.11.0"

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
}
