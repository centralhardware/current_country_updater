@Suppress("unused")
object Config {

    val CHANNEL_ID: Long = System.getenv("CHANEL_ID").toLong()
    val CHANEL_TITLE_PATTERN: String = System.getenv("CHANEL_TITLE_PATTERN")
    val CLICKHOUSE_URL: String = System.getenv("CLICKHOUSE_URL")
    val CLICKHOUSE_USER: String? = System.getenv("CLICKHOUSE_USER")
    val CLICKHOUSE_PASSWORD: String? = System.getenv("CLICKHOUSE_PASSWORD")

    const val CHANNEL_UPDATE_CRON = "0 /10 * * *"

    const val TOTAL_COUNTRIES = 193

}