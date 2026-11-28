import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotliquery.queryOf
import kotliquery.sessionOf

/** What each OpenCage key has been used for, and how much quota it last reported. */
object GeoUsage {

    @Serializable
    data class KeyUsage(
        val keyId: String,
        val requestsToday: Long,
        val requests30d: Long,
        val quotaHits30d: Long,
        val errors30d: Long,
        val lastRequest: String?,
        val rateLimit: Int?,
        val rateRemaining: Int?,
        val rateReset: String?,
        val blockedUntil: String?,
    )

    @Serializable
    data class Report(
        val keys: List<KeyUsage>,
        val cachedCells: Long,
        /** Pings stored without geocoding because the key was out of quota, last 30 days. */
        val ungeocodedRows30d: Long,
    )

    fun report(): Report {
        val blocked = Geocoder.blocked()
        val current = IngestConfig.OPENCAGE_KEY?.let(Geocoder::keyId)
        val configured = listOfNotNull(current)
        val rows = sessionOf(ClickHouse.dataSource).use { s ->
            s.run(
                queryOf(
                    """
                    SELECT key_id,
                           countIf(toDate(ts) = today()) AS today,
                           count() AS d30,
                           countIf(outcome IN ('quota', 'rate_limited')) AS quota,
                           countIf(outcome IN ('error', 'invalid_key')) AS errors,
                           toString(max(ts)) AS last,
                           argMaxIf(rate_limit, ts, rate_limit > 0) AS lim,
                           argMaxIf(rate_remaining, ts, rate_limit > 0) AS rem,
                           toString(argMaxIf(rate_reset, ts, rate_limit > 0)) AS reset,
                           countIf(rate_limit > 0) AS with_rate
                    FROM country_days_tracker_bot.geo_api_usage
                    WHERE ts >= now() - INTERVAL 30 DAY
                    GROUP BY key_id
                    """.trimIndent()
                ).map {
                    val hasRate = it.long("with_rate") > 0
                    KeyUsage(
                        keyId = it.string("key_id"),
                        requestsToday = it.long("today"),
                        requests30d = it.long("d30"),
                        quotaHits30d = it.long("quota"),
                        errors30d = it.long("errors"),
                        lastRequest = it.string("last"),
                        rateLimit = it.int("lim").takeIf { hasRate },
                        rateRemaining = it.int("rem").takeIf { hasRate },
                        rateReset = it.string("reset").takeIf { hasRate },
                        blockedUntil = blocked?.takeIf { _ -> it.string("key_id") == current }?.toString(),
                    )
                }.asList
            )
        }.associateBy { it.keyId }
        val keys = configured.map {
            rows[it] ?: KeyUsage(it, 0, 0, 0, 0, null, null, null, null, blocked?.toString())
        } + rows.filterKeys { it !in configured }.values
        val cached = sessionOf(ClickHouse.dataSource).use { s ->
            s.run(queryOf("SELECT uniqExact(ghash) AS n FROM country_days_tracker_bot.geo_cache").map { it.long("n") }.asSingle)
        } ?: 0
        val ungeocoded = sessionOf(ClickHouse.dataSource).use { s ->
            s.run(
                queryOf(
                    "SELECT count() AS n FROM country_days_tracker_bot.country_days_tracker " +
                        "WHERE country = '' AND tzname = 'Z' AND ts >= now() - INTERVAL 30 DAY"
                ).map { it.long("n") }.asSingle
            )
        } ?: 0
        return Report(keys, cached, ungeocoded)
    }
}

/** `GET /geo/usage`, behind the same Basic auth as /pub. */
fun Routing.geo() {
    get("/geo/usage") {
        if (!authorized(call.request.headers[HttpHeaders.Authorization], IngestConfig.LOCATION_USER, IngestConfig.LOCATION_PASSWORD)) {
            call.response.headers.append(HttpHeaders.WWWAuthenticate, "Basic realm=\"owntracks\"")
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }
        call.respond(withContext(Dispatchers.IO) { GeoUsage.report() })
    }
}
