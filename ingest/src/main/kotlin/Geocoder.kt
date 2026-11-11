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

/** What reverse geocoding gives a ping, mirroring the OwnTracks Recorder's `geo` record. */
data class Geo(val cc: String, val addr: String, val locality: String, val tzname: String)

/**
 * Reverse geocoding the way the OwnTracks Recorder did it: one OpenCage lookup
 * per geohash-7 cell, kept forever in Postgres (the recorder's LMDB `ghash`
 * cache, with its default clean_age of 0).
 *
 * The time zone comes from OpenCage's `annotations.timezone.name` -- the same
 * IANA name the recorder's offline zone database produced -- so it is cached
 * with the cell instead of needing a lookup library.
 */
object Geocoder {

    private const val URL = "https://api.opencagedata.com/geocode/v1/json"

    private val client = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 4000 }
        expectSuccess = false
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Cached or fresh geo for [ghash]; null when OpenCage could not answer. */
    suspend fun lookup(ghash: String, lat: Double, lon: Double): Geo? {
        cached(ghash)?.let { return it }
        val geo = fetch(lat, lon) ?: return null
        runCatching { store(ghash, geo) }
            .onFailure { KSLog.warning("Geo cache write failed for $ghash: ${it.message}") }
        return geo
    }

    private suspend fun fetch(lat: Double, lon: Double): Geo? {
        val key = IngestConfig.OPENCAGE_KEY ?: run {
            KSLog.warning("OPENCAGE_KEY not set: cannot reverse-geocode")
            return null
        }
        return runCatching {
            val response = client.get(URL) {
                parameter("q", "$lat+$lon")
                parameter("key", key)
                parameter("abbrv", 1)
                parameter("no_record", 1)
                parameter("limit", 1)
                parameter("format", "json")
            }
            if (response.status != HttpStatusCode.OK) {
                KSLog.warning("OpenCage answered ${response.status}")
                return null
            }
            parseOpenCage(response.bodyAsText())
        }.onFailure {
            KSLog.warning("OpenCage lookup failed: ${it.message}")
        }.getOrNull()
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

    private fun cached(ghash: String): Geo? = runCatching {
        sessionOf(PostgresService.dataSource).use { s ->
            s.run(
                queryOf("SELECT cc, addr, locality, tzname FROM geo_cache WHERE ghash = ?", ghash)
                    .map { Geo(it.string("cc"), it.string("addr"), it.string("locality"), it.string("tzname")) }
                    .asSingle
            )
        }
    }.onFailure { KSLog.warning("Geo cache read failed for $ghash: ${it.message}") }.getOrNull()

    private fun store(ghash: String, geo: Geo) {
        sessionOf(PostgresService.dataSource).use { s ->
            s.update(
                queryOf(
                    """
                    INSERT INTO geo_cache (ghash, cc, addr, locality, tzname) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (ghash) DO NOTHING
                    """.trimIndent(),
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
