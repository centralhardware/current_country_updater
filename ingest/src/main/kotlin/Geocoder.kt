import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.kslog.common.warning
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotliquery.queryOf
import kotliquery.sessionOf
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** What reverse geocoding gives a ping, mirroring the OwnTracks Recorder's `geo` record. */
data class Geo(val cc: String, val addr: String, val locality: String, val tzname: String) {
    companion object {
        /** No geocoding at all: what a ping is stored with while every key is out of quota. */
        val NONE = Geo("", "", "", "")
        /** A ping from a plane with no recent ground ping to take the zone of. */
        val IN_FLIGHT = Geo("", "", "", "UTC")
    }
}

sealed interface GeoResult {
    data class Found(val geo: Geo) : GeoResult
    /** Every configured key is out of quota (or rate limited) right now. */
    data object QuotaExhausted : GeoResult
    /** OpenCage failed in some other way; the phone should retry. */
    data object Failed : GeoResult
}

/** OpenCage's `rate` block: the key's daily limit, what is left, and when it resets. */
data class Rate(val limit: Int, val remaining: Int, val reset: Instant)

/**
 * Reverse geocoding the way the OwnTracks Recorder did it: one OpenCage lookup
 * per geohash-7 cell, kept forever in ClickHouse (the recorder's LMDB `ghash`
 * cache, with its default clean_age of 0).
 *
 * The time zone comes from OpenCage's `annotations.timezone.name` -- the same
 * IANA name the recorder's offline zone database produced -- so it is cached
 * with the cell instead of needing a lookup library.
 *
 * When the key reports its quota spent (402, 429, or `rate.remaining` 0) it
 * is not called again until its `rate.reset`; meanwhile the lookup answers [GeoResult.QuotaExhausted] and the ping is stored without
 * geocoding. Every request is logged to
 * `geo_api_usage`.
 */
object Geocoder {

    private const val URL = "https://api.opencagedata.com/geocode/v1/json"

    private val client = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 4000 }
        expectSuccess = false
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** When the key may be used again. In memory: a restart just re-probes it once. */
    @Volatile
    private var blockedUntil: Instant? = null

    internal fun keyId(key: String) = key.takeLast(4)


    /** Cached or fresh geo for [ghash]. */
    suspend fun lookup(ghash: String, lat: Double, lon: Double): GeoResult {
        cached(ghash)?.let { return GeoResult.Found(it) }
        val result = fetch(lat, lon)
        if (result is GeoResult.Found) {
            runCatching { store(ghash, result.geo) }
                .onFailure { KSLog.warning("Geo cache write failed for $ghash: ${it.message}") }
        }
        return result
    }

    private suspend fun fetch(lat: Double, lon: Double): GeoResult {
        val key = IngestConfig.OPENCAGE_KEY ?: run {
            KSLog.warning("OPENCAGE_KEY not set: cannot reverse-geocode")
            return GeoResult.Failed
        }
        if (blockedUntil?.isAfter(Instant.now()) == true) return GeoResult.QuotaExhausted
        return fetchWith(key, lat, lon)
    }

    private suspend fun fetchWith(key: String, lat: Double, lon: Double): GeoResult {
        val id = keyId(key)
        val response = runCatching {
            client.get(URL) {
                parameter("q", "$lat+$lon")
                parameter("key", key)
                parameter("abbrv", 1)
                parameter("no_record", 1)
                parameter("limit", 1)
                parameter("format", "json")
            }
        }.getOrElse {
            KSLog.warning("OpenCage lookup failed (key …$id): ${it.message}")
            logUsage(id, "error", 0, null)
            return GeoResult.Failed
        }
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        val rate = parseRate(body)
        val status = response.status.value
        return when (status) {
            200 -> {
                val geo = runCatching { parseOpenCage(body) }.getOrElse {
                    logUsage(id, "error", status, rate)
                    return GeoResult.Failed
                }
                logUsage(id, "ok", status, rate)
                if (rate != null && rate.remaining <= 0) block(key, rate.reset)
                GeoResult.Found(geo)
            }
            402, 429 -> {
                logUsage(id, if (status == 402) "quota" else "rate_limited", status, rate)
                block(key, rate?.reset ?: if (status == 402) nextUtcMidnight() else Instant.now().plusSeconds(60))
                GeoResult.QuotaExhausted
            }
            401, 403 -> {
                // Invalid, suspended or disabled key: store ungeocoded for an hour, then retry.
                logUsage(id, "invalid_key", status, rate)
                block(key, Instant.now().plusSeconds(3600))
                GeoResult.QuotaExhausted
            }
            else -> {
                KSLog.warning("OpenCage answered $status (key …$id)")
                logUsage(id, "error", status, rate)
                GeoResult.Failed
            }
        }
    }

    private fun block(key: String, until: Instant) {
        blockedUntil = until
        KSLog.warning("OpenCage key …${keyId(key)} out of quota until $until")
    }

    private fun nextUtcMidnight(): Instant =
        LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

    internal fun parseRate(body: String): Rate? = runCatching {
        val rate = json.parseToJsonElement(body).jsonObject["rate"]?.jsonObject ?: return null
        fun n(k: String) = (rate[k] as? JsonPrimitive)?.longOrNull
        Rate(
            limit = n("limit")?.toInt() ?: return null,
            remaining = n("remaining")?.toInt() ?: return null,
            reset = Instant.ofEpochSecond(n("reset") ?: return null),
        )
    }.getOrNull()

    private fun logUsage(keyId: String, outcome: String, status: Int, rate: Rate?) {
        runCatching {
            sessionOf(ClickHouse.dataSource).use { s ->
                s.update(
                    queryOf(
                        """
                        INSERT INTO country_days_tracker_bot.geo_api_usage
                            (key_id, outcome, status, rate_limit, rate_remaining, rate_reset)
                        SELECT toString(?), toString(?), toUInt16(?), toInt32(?), toInt32(?), toDateTime(toInt64(?), 'UTC')
                        """.trimIndent(),
                        keyId, outcome, status, rate?.limit ?: 0, rate?.remaining ?: 0, rate?.reset?.epochSecond ?: 0L
                    )
                )
            }
        }.onFailure { KSLog.warning("Geo usage write failed: ${it.message}") }
    }

    /**
     * An answer with no results (open sea) is still an answer: empty fields,
     * as the recorder stored it, so the phone is not told to retry forever.
     */
    internal fun parseOpenCage(body: String): Geo {
        val first = json.parseToJsonElement(body).jsonObject["results"]
            ?.jsonArray?.firstOrNull()?.jsonObject
        val components = first?.get("components")?.jsonObject
        fun JsonObject?.str(k: String) = (this?.get(k) as? JsonPrimitive)?.contentOrNull
        return Geo(
            cc = components.str("country_code")?.uppercase().orEmpty(),
            addr = first.str("formatted").orEmpty(),
            locality = components.str("city").orEmpty(),
            tzname = first?.get("annotations")?.jsonObject?.get("timezone")?.jsonObject.str("name").orEmpty(),
        )
    }

    /**
     * The cached geo of the newest ping that was geocoded on the ground, for
     * in-flight pings to inherit. Rows of the plane itself are skipped by
     * speed, quota-skipped ones by their "Z" zone.
     */
    fun lastGround(): Geo? = runCatching {
        sessionOf(ClickHouse.dataSource).use { s ->
            s.run(
                queryOf(
                    """
                    SELECT cc, addr, locality, tzname FROM country_days_tracker_bot.geo_cache
                    WHERE ghash = (
                        SELECT ghash FROM country_days_tracker_bot.country_days_tracker
                        WHERE vel < ? AND tzname != 'Z' AND ghash != ''
                          AND ts > now() - INTERVAL 2 DAY
                        ORDER BY ts DESC LIMIT 1
                    )
                    LIMIT 1
                    """.trimIndent(),
                    OwnTracks.IN_FLIGHT_KMH
                )
                    .map { Geo(it.string("cc"), it.string("addr"), it.string("locality"), it.string("tzname")) }
                    .asSingle
            )
        }
    }.onFailure { KSLog.warning("Last ground geo lookup failed: ${it.message}") }.getOrNull()

    private fun cached(ghash: String): Geo? = runCatching {
        sessionOf(ClickHouse.dataSource).use { s ->
            s.run(
                queryOf(
                    "SELECT cc, addr, locality, tzname FROM country_days_tracker_bot.geo_cache WHERE ghash = ? LIMIT 1",
                    ghash
                )
                    .map { Geo(it.string("cc"), it.string("addr"), it.string("locality"), it.string("tzname")) }
                    .asSingle
            )
        }
    }.onFailure { KSLog.warning("Geo cache read failed for $ghash: ${it.message}") }.getOrNull()

    private fun store(ghash: String, geo: Geo) {
        // No ON CONFLICT here: a duplicate cell is merged away by the
        // ReplacingMergeTree, and both copies hold the same answer.
        sessionOf(ClickHouse.dataSource).use { s ->
            s.update(
                queryOf(
                    "INSERT INTO country_days_tracker_bot.geo_cache (ghash, cc, addr, locality, tzname) VALUES (?, ?, ?, ?, ?)",
                    ghash, geo.cc, geo.addr, geo.locality, geo.tzname
                )
            )
        }
        KSLog.info("Geo cached for $ghash (${geo.cc})")
    }
}

/** Standard geohash, as the recorder's `geohash_encode` (precision 7 there). */
fun geohash(lat: Double, lon: Double, precision: Int = 7): String {
    val base32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    var latMin = -90.0; var latMax = 90.0
    var lonMin = -180.0; var lonMax = 180.0
    val sb = StringBuilder()
    var bit = 0; var ch = 0; var even = true
    while (sb.length < precision) {
        if (even) {
            val mid = (lonMin + lonMax) / 2
            if (lon >= mid) { ch = ch or (16 shr bit); lonMin = mid } else lonMax = mid
        } else {
            val mid = (latMin + latMax) / 2
            if (lat >= mid) { ch = ch or (16 shr bit); latMin = mid } else latMax = mid
        }
        even = !even
        if (bit < 4) bit++ else { sb.append(base32[ch]); bit = 0; ch = 0 }
    }
    return sb.toString()
}
