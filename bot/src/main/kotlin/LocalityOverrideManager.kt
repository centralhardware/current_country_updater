import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.warning
import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.Duration
import java.time.Instant

object LocalityOverrideManager {

    /** How long a loaded set of overrides is trusted before it is read again. */
    private val TTL: Duration = Duration.ofMinutes(10)

    /** Normalized `from` -> `to`, with the moment it was read. */
    @Volatile
    private var cache: Pair<Map<String, String>, Instant>? = null

    fun addOverride(from: String, to: String) {
        sessionOf(PostgresService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO locality_overrides (from_locality, to_locality)
                    VALUES (?, ?)
                    ON CONFLICT (from_locality) DO UPDATE SET to_locality = EXCLUDED.to_locality
                    """.trimIndent(),
                    normalize(from),
                    to
                )
            )
        }
        cache = null
    }

    fun removeOverride(from: String): Boolean {
        val removed = sessionOf(PostgresService.dataSource).use { session ->
            session.update(
                queryOf("DELETE FROM locality_overrides WHERE from_locality = ?", normalize(from))
            ) > 0
        }
        cache = null
        return removed
    }

    fun getOverrides(): List<Pair<String, String>> {
        return sessionOf(PostgresService.dataSource).use { session ->
            session.run(
                queryOf("SELECT from_locality, to_locality FROM locality_overrides ORDER BY from_locality")
                    .map { Pair(it.string("from_locality"), it.string("to_locality")) }
                    .asList
            )
        }
    }

    /**
     * Returns the override target if geocoding produced a [locality] that matches a
     * configured `from` value, otherwise returns [locality] unchanged.
     *
     * Matching is done on the sanitized (hashtag) form so that the value the user sees
     * in the channel (e.g. `#Chiang_Mai_City_Municipality`) matches the raw geocoded
     * locality (`Chiang Mai City Municipality`), regardless of spaces vs underscores.
     */
    fun resolve(locality: String): String = overrides()[normalize(locality)] ?: locality

    /**
     * The overrides, from memory. This sits on the ping save path, so Postgres
     * being down must not fail a ping: the last loaded set is kept (or none, if
     * nothing was ever loaded) and the read is retried on the next ping.
     */
    private fun overrides(): Map<String, String> {
        val cached = cache
        if (cached != null && cached.second.plus(TTL).isAfter(Instant.now())) return cached.first
        return runCatching {
            getOverrides().associate { (from, to) -> from to to }
        }.onSuccess {
            cache = it to Instant.now()
        }.getOrElse {
            KSLog.warning("Locality overrides unavailable, using ${if (cached == null) "none" else "the last loaded set"}: ${it.message}")
            cached?.first ?: emptyMap()
        }
    }

    private fun normalize(locality: String) =
        TagManager.sanitizeForHashtag(locality.removePrefix("#"))
}
