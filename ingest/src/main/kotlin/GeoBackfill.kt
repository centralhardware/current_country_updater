import LocationStore.toCountry
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotliquery.queryOf
import kotliquery.sessionOf

/**
 * Pings stored while every OpenCage key was out of quota carry no geocoding:
 * empty country, tzname "Z", tz_offset 0. They are told apart from open-sea
 * pings (also empty country) by their cell: a sea cell is in `geo_cache`, an
 * ungeocoded one never got there. The `tzname = 'Z'` condition keeps out the
 * ~31k legacy rows from the phone-reported era (tzname GMT, GMT-06:00, ...),
 * which also have no country and no cached cell; those are left as they are.
 *
 * The backfill geocodes those cells -- one lookup each, through the same
 * cache and key rotation as /pub -- and rewrites their rows in
 * `country_days_tracker_data` with an ALTER UPDATE. tz_offset is recomputed
 * per row from the zone, so summer time is right. Rows still in the Buffer
 * (the last minute) are picked up by the next run.
 */
object GeoBackfill {

    private const val DATA = "country_days_tracker_bot.country_days_tracker_data"

    private const val PENDING = """
        FROM $DATA
        WHERE country = '' AND tzname = 'Z' AND ghash != ''
          AND ghash NOT IN (SELECT ghash FROM country_days_tracker_bot.geo_cache)
    """

    private val running = Mutex()

    @Serializable
    data class Result(
        val cellsGeocoded: Int,
        val rowsUpdated: Long,
        val stoppedOnQuota: Boolean,
        val failedCells: Int,
        val cellsPending: Long,
        val rowsPending: Long,
    )

    data class Cell(val ghash: String, val lat: Double, val lon: Double, val rows: Long)

    fun pending(): Pair<Long, Long> = sessionOf(ClickHouse.dataSource).use { s ->
        s.run(
            queryOf("SELECT uniqExact(ghash) AS cells, count() AS rows $PENDING")
                .map { it.long("cells") to it.long("rows") }.asSingle
        ) ?: (0L to 0L)
    }

    private fun cells(limit: Int): List<Cell> = sessionOf(ClickHouse.dataSource).use { s ->
        s.run(
            queryOf(
                "SELECT ghash, any(latitude) AS lat, any(longitude) AS lon, count() AS rows $PENDING " +
                    "GROUP BY ghash ORDER BY min(ts) LIMIT ?",
                limit
            ).map { Cell(it.string("ghash"), it.double("lat"), it.double("lon"), it.long("rows")) }.asList
        )
    }

    private fun apply(cell: Cell, geo: Geo) {
        // An empty zone (open sea) has no offset to compute: keep UTC.
        val tz = geo.tzname.ifBlank { "UTC" }
        sessionOf(ClickHouse.dataSource).use { s ->
            s.execute(
                queryOf(
                    """
                    ALTER TABLE $DATA UPDATE
                        country = toString(?),
                        tzname = toString(?),
                        tz_offset = toInt32(timeZoneOffset(toTimeZone(ts, ?))),
                        locality = toString(?),
                        addr = toString(?)
                    WHERE ghash = ? AND country = '' AND tzname = 'Z'
                    """.trimIndent(),
                    geo.cc.toCountry(), tz, tz,
                    LocationStore.localityResolver(geo.locality), geo.addr, cell.ghash
                )
            )
        }
    }

    /** Geocodes up to [limit] pending cells, oldest first; null when a run is already going. */
    suspend fun run(limit: Int): Result? {
        if (!running.tryLock()) return null
        try {
            var done = 0; var rows = 0L; var failed = 0; var quota = false
            for (cell in withContext(Dispatchers.IO) { cells(limit) }) {
                when (val r = Geocoder.lookup(cell.ghash, cell.lat, cell.lon)) {
                    is GeoResult.Found -> {
                        withContext(Dispatchers.IO) { apply(cell, r.geo) }
                        done++; rows += cell.rows
                    }
                    GeoResult.QuotaExhausted -> { quota = true; break }
                    GeoResult.Failed -> failed++
                }
            }
            val (cellsLeft, rowsLeft) = withContext(Dispatchers.IO) { pending() }
            KSLog.info("Geo backfill: $done cells / $rows rows geocoded, $failed failed, quota hit: $quota")
            return Result(done, rows, quota, failed, cellsLeft, rowsLeft)
        } finally {
            running.unlock()
        }
    }
}

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
        val keysConfigured: Int,
        val keys: List<KeyUsage>,
        val cachedCells: Long,
        val ungeocodedCells: Long,
        val ungeocodedRows: Long,
    )

    fun report(): Report {
        val blocked = Geocoder.blocked()
        val configured = IngestConfig.OPENCAGE_KEYS.map(Geocoder::keyId)
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
                        blockedUntil = blocked[it.string("key_id")]?.toString(),
                    )
                }.asList
            )
        }.associateBy { it.keyId }
        val keys = configured.map {
            rows[it] ?: KeyUsage(it, 0, 0, 0, 0, null, null, null, null, blocked[it]?.toString())
        } + rows.filterKeys { it !in configured }.values
        val cached = sessionOf(ClickHouse.dataSource).use { s ->
            s.run(queryOf("SELECT uniqExact(ghash) AS n FROM country_days_tracker_bot.geo_cache").map { it.long("n") }.asSingle)
        } ?: 0
        val (cells, pendingRows) = GeoBackfill.pending()
        return Report(configured.size, keys, cached, cells, pendingRows)
    }
}

/** `GET /geo/usage` and `POST /geo/backfill?limit=N`, behind the same Basic auth as /pub. */
fun Routing.geo() {
    suspend fun ApplicationCall.allowed(): Boolean {
        if (authorized(request.headers[HttpHeaders.Authorization], IngestConfig.LOCATION_USER, IngestConfig.LOCATION_PASSWORD)) return true
        response.headers.append(HttpHeaders.WWWAuthenticate, "Basic realm=\"owntracks\"")
        respond(HttpStatusCode.Unauthorized)
        return false
    }
    get("/geo/usage") {
        if (!call.allowed()) return@get
        call.respond(withContext(Dispatchers.IO) { GeoUsage.report() })
    }
    post("/geo/backfill") {
        if (!call.allowed()) return@post
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 2500) ?: 100
        val result = GeoBackfill.run(limit)
            ?: return@post call.respond(HttpStatusCode.Conflict, "a backfill is already running")
        call.respond(result)
    }
}
