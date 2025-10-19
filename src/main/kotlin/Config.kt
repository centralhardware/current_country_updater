@Suppress("unused")
object Config {

    // Environment variables
    val CHANNEL_ID: Long = System.getenv("CHANEL_ID").toLong()
    val CHANEL_TITLE_PATTERN: String = System.getenv("CHANEL_TITLE_PATTERN")
    val CLICKHOUSE_URL: String = System.getenv("CLICKHOUSE_URL")
    val CLICKHOUSE_USER: String? = System.getenv("CLICKHOUSE_USER")
    val CLICKHOUSE_PASSWORD: String? = System.getenv("CLICKHOUSE_PASSWORD")

    // Application constants
    const val CHANNEL_UPDATE_CRON = "0 /10 * * *"

    // Location subscription constants
    const val SUBSCRIPTION_DURATION_MINUTES = 30L

    // Statistics constants
    const val TOTAL_COUNTRIES = 193

}