/** Environment for the web server: storage, /pub credentials, geocoding, the calendar feed. */
object IngestConfig {

    val CLICKHOUSE_URL: String = System.getenv("CLICKHOUSE_URL")
    val CLICKHOUSE_USER: String? = System.getenv("CLICKHOUSE_USER")
    val CLICKHOUSE_PASSWORD: String? = System.getenv("CLICKHOUSE_PASSWORD")

    val DATABASE_URL: String = System.getenv("DATABASE_URL")

    // HTTP Basic credentials for POST /pub (OwnTracks HTTP mode). Unset
    // password leaves the endpoint open.
    val LOCATION_USER: String? = System.getenv("LOCATION_USER")
    val LOCATION_PASSWORD: String? = System.getenv("LOCATION_PASSWORD")

    // OpenCage keys for POST /pub reverse geocoding, comma-separated and tried
    // in order; the recorder's "opencage:KEY" form is accepted for each.
    val OPENCAGE_KEYS: List<String> = System.getenv("OPENCAGE_KEY").orEmpty()
        .split(',').map { it.trim().removePrefix("opencage:").trim() }.filter { it.isNotBlank() }

    // Path secret of the public calendar feed.
    val CALENDAR_SECRET: String = System.getenv("CALENDAR_SECRET")

    val PORT: Int = System.getenv("PORT")?.toIntOrNull() ?: 80
}
