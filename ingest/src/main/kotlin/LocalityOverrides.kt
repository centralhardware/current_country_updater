
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.warning
import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.Duration
import java.time.Instant

/**
 * Read side of the bot's /addoverride table, used on the ping save path.
 * The bot writes the table; this process only reads it, so an edit takes
 * effect within [TTL].
 */
object LocalityOverrides {

    private val TTL: Duration = Duration.ofMinutes(1)

    @Volatile
    private var cache: Pair<Map<String, String>, Instant>? = null

    fun all(): List<Pair<String, String>> =
        sessionOf(PostgresService.dataSource).use { session ->
            session.run(
                queryOf("SELECT from_locality, to_locality FROM locality_overrides ORDER BY from_locality")
                    .map { Pair(it.string("from_locality"), it.string("to_locality")) }
                    .asList
            )
        }

    /**
     * The override target when the geocoded [locality] matches a `from` value,
     * compared in hashtag form (`#Chiang_Mai` matches `Chiang Mai`); otherwise
     * [locality] unchanged.
     */
    fun resolve(locality: String): String = overrides()[normalize(locality)] ?: locality

    /** Postgres being down must not fail a ping: keep the last loaded set. */
    private fun overrides(): Map<String, String> {
        val cached = cache
        if (cached != null && cached.second.plus(TTL).isAfter(Instant.now())) return cached.first
        return runCatching { all().associate { it } }
            .onSuccess { cache = it to Instant.now() }
            .getOrElse {
                KSLog.warning("Locality overrides unavailable, using ${if (cached == null) "none" else "the last loaded set"}: ${it.message}")
                cached?.first ?: emptyMap()
            }
    }

    fun sanitizeForHashtag(value: String): String =
        value.replace(" ", "_").replace(Regex("[^\\p{L}\\p{N}_]"), "")

    fun normalize(locality: String) = sanitizeForHashtag(locality.removePrefix("#"))
}
