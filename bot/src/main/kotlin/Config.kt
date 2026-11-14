@Suppress("unused")
object Config {

    val CHANNEL_ID: Long = System.getenv("CHANEL_ID").toLong()
    val CHANEL_TITLE_PATTERN: String = System.getenv("CHANEL_TITLE_PATTERN")

    // May run /map anywhere, directly or via guest mode.
    val MAP_OWNER_ID: Long = System.getenv("MAP_OWNER_ID").toLong()

    // May run /map only through guest mode in their private chat with the owner.
    val MAP_GUEST_ID: Long = System.getenv("MAP_GUEST_ID").toLong()

    const val TOTAL_COUNTRIES = 193

}
