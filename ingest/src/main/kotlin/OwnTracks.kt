import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.kslog.common.warning
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import java.time.Instant

/**
 * `POST /pub` -- the OwnTracks app's HTTP mode, answered the way the OwnTracks
 * Recorder (with the fork's revgeo-required patch) answered it, so the phone
 * can be pointed here without changing anything in the app:
 *
 *  - HTTP Basic auth (LOCATION_USER / LOCATION_PASSWORD);
 *  - the body is one OwnTracks JSON message, `_type` says what it is;
 *  - the reply is a JSON array (the recorder's "friends" list) -- always `[]`;
 *  - a location that cannot be reverse-geocoded or stored gets a 503, so the
 *    phone keeps it in its queue and sends it again -- except when the
 *    OpenCage key is out of quota: then it is stored ungeocoded (empty
 *    country, UTC) and `POST /geo/backfill` geocodes it later.
 *
 * Only `location` messages are recorded; everything else (lwt, transition,
 * waypoint, card, status...) is acknowledged and dropped, as the recorder's
 * hook did.
 *
 * Contract drift: every body is validated against the JSON Schema in
 * resources/owntracks-message.schema.json; the request fails (503) when the JSON differs from what we
 * expect at all -- an unknown `_type`, an unknown location key, a missing
 * lat/lon/tst, or a field of the wrong JSON type. The phone keeps those points
 * queued and the "no OwnTracks ping for 12 h" alert fires, instead of data
 * being silently misread.
 */
object OwnTracks {

    /** The contract, as a standard JSON Schema (draft 2020-12) in resources. */
    private val schema: Schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) { b ->
            // The official schema, vendored unmodified, is resolved from the classpath, never fetched.
            b.schemaIdResolvers { it.mapPrefix("https://owntracks.org/schemas/", "classpath:owntracks/") }
        }
        .getSchema(OwnTracks::class.java.getResourceAsStream("/owntracks-message.schema.json"))

    /** Every way [body] differs from the schema; empty when it matches exactly. */
    internal fun violations(body: String): List<String> =
        schema.validate(body, InputFormat.JSON).map { it.toString() }.sorted()

    suspend fun handlePub(call: ApplicationCall) {
        if (!authorized(call.request.headers[HttpHeaders.Authorization], IngestConfig.LOCATION_USER, IngestConfig.LOCATION_PASSWORD)) {
            call.response.headers.append(HttpHeaders.WWWAuthenticate, "Basic realm=\"owntracks\"")
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        val body = call.receiveText()
        val problems = runCatching { violations(body) }.getOrElse {
            KSLog.warning("OwnTracks: unreadable body: ${it.message}")
            call.respond(HttpStatusCode.BadRequest, "Invalid JSON")
            return
        }
        if (problems.isNotEmpty()) return call.reject("contract changed: ${problems.joinToString("; ")}")

        val msg = Json.parseToJsonElement(body).jsonObject
        if (msg.str("_type") != "location") return call.ok()
        val ping = toPing(msg)!!

        val geo = if (ping.inFlight()) {
            // Airborne: the cell under a plane is a new one every few seconds
            // and its address means nothing, so don't spend quota on it. No
            // country is stored (the air is not a country day); the zone is
            // the last ground ping's (the departure), so wall time stays sane.
            KSLog.info("OwnTracks: in flight (${ping.msg.int("vel")} km/h), skipping geocoding")
            Geocoder.lastGround()?.let { Geo.IN_FLIGHT.copy(tzname = it.tzname.ifBlank { "UTC" }) } ?: Geo.IN_FLIGHT
        } else when (val r = Geocoder.lookup(ping.ghash, ping.lat, ping.lon)) {
            is GeoResult.Found -> r.geo
            // Key out of quota: keep the ping, ungeocoded, rather than
            // let the phone's queue grow until tomorrow. POST /geo/backfill
            // fills it in once a key has quota again.
            GeoResult.QuotaExhausted -> Geo.NONE
            GeoResult.Failed -> {
                KSLog.warning("OwnTracks: reverse geocoding failed for ${ping.ghash}; rejecting so the phone retries")
                call.respond(HttpStatusCode.ServiceUnavailable, "reverse geocoding failed")
                return
            }
        }

        LocationStore.save(ping.toRequest(geo))
            .onSuccess { call.ok() }
            .onFailure { call.respond(HttpStatusCode.ServiceUnavailable, "Not saved: ${it.message}") }
    }

    /**
     * 503, not 4xx: the phone keeps the message queued and resends it, so
     * nothing is lost while the server is taught the new shape, and the
     * "no OwnTracks ping for 12 h" alert fires.
     */
    private suspend fun ApplicationCall.reject(why: String) {
        KSLog.warning("OwnTracks: rejecting: $why")
        respond(HttpStatusCode.ServiceUnavailable, why)
    }

    private suspend fun ApplicationCall.ok() =
        respondText("[]", ContentType.Application.Json)

    /** The raw fields of one location message, before geocoding. */
    data class Ping(val msg: JsonObject, val lat: Double, val lon: Double, val tst: Long, val ghash: String) {
        /**
         * 200 km/h and up is a plane. Over the whole history, ground pings
         * (alt near 0) thin out from 150 km/h -- trains -- and stop under 200;
         * past 275 km/h it is all climb, cruise and descent. The 4 pings
         * between 175 and 275 are take-off and landing rolls.
         */
        fun inFlight() = msg.int("vel") >= IN_FLIGHT_KMH

        fun toRequest(geo: Geo) = LocationRequest(
            latitude = lat.toFloat(),
            longitude = lon.toFloat(),
            timezone = geo.tzname,
            country = geo.cc,
            timestamp = Instant.ofEpochSecond(tst),
            alt = msg.int("alt"),
            batt = msg.int("batt"),
            acc = msg.int("acc"),
            vac = msg.int("vac"),
            conn = msg.str("conn").orEmpty(),
            locality = geo.locality,
            ghash = ghash,
            p = msg.num("p") ?: 0.0,
            addr = geo.addr,
            vel = msg.int("vel"),
            cog = msg.int("cog"),
            m = msg.int("m"),
            bs = msg.int("bs"),
        )
    }

    internal fun toPing(msg: JsonObject): Ping? {
        val lat = msg.num("lat") ?: return null
        val lon = msg.num("lon") ?: return null
        val tst = msg.num("tst")?.toLong() ?: return null
        return Ping(msg, lat, lon, tst, geohash(lat, lon))
    }

    const val IN_FLIGHT_KMH = 200

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.int(k: String) = num(k)?.toInt() ?: 0
}

/** Mounts `POST /pub` on the host app's routing. */
fun Routing.ownTracks() {
    if (IngestConfig.LOCATION_PASSWORD.isNullOrEmpty()) {
        KSLog.warning("LOCATION_PASSWORD not set: POST /pub accepts unauthenticated pings")
    }
    post("/pub") { OwnTracks.handlePub(call) }
}
