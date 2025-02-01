object Config {

    val CHANNEL_ID: Long = System.getenv("CHANEL_ID").toLong()
    val CHANEL_TITLE_PATTERN: String = System.getenv("CHANEL_TITLE_PATTERN")
    val CLICKHOUSE_URL = System.getenv("CLICKHOUSE_URL")

}