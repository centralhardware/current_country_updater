
/** The web server process: everything HTTP, no Telegram. */
fun main() {
    // Both databases are Kotlin objects, so their Flyway migrations would only
    // run on first use -- for Postgres that can be hours after a deploy.
    // Touch them now so migrations apply, and failures show, at startup.
    ClickHouse.dataSource
    PostgresService.dataSource
    LocationStore.localityResolver = LocalityOverrides::resolve
    WebService.start(IngestConfig.PORT)
}
