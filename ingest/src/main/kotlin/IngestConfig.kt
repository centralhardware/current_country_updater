/** Environment for the ingest side: storage, /pub credentials, geocoding. */
object IngestConfig {

    val CLICKHOUSE_URL: String = System.getenv("CLICKHOUSE_URL")
    val CLICKHOUSE_USER: String? = System.getenv("CLICKHOUSE_USER")
    val CLICKHOUSE_PASSWORD: String? = System.getenv("CLICKHOUSE_PASSWORD")

    val DATABASE_URL: String = System.getenv("DATABASE_URL")

    // HTTP Basic credentials for POST /pub (OwnTracks HTTP mode). Unset
    // password leaves the endpoint open.
    val LOCATION_USER: String? = System.getenv("LOCATION_USER")
    val LOCATION_PASSWORD: String? = System.getenv("LOCATION_PASSWORD")

    // OpenCage key for POST /pub reverse geocoding; the recorder's "opencage:KEY" form is accepted.
    val OPENCAGE_KEY: String? = System.getenv("OPENCAGE_KEY")?.removePrefix("opencage:")?.takeIf { it.isNotBlank() }
}
