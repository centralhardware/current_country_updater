import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.kslog.common.warning
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * `POST /pub` -- the OwnTracks app's HTTP mode, answered the way the OwnTracks
 * Recorder (with the fork's revgeo-required patch) answered it, so the phone
 * can be pointed here without changing anything in the app:
 *
 *  - HTTP Basic auth (same LOCATION_USER / LOCATION_PASSWORD as /location);
 *  - the body is one OwnTracks JSON message, `_type` says what it is;
 *  - the reply is a JSON array (the recorder's "friends" list) -- always `[]`;
 *  - a location that cannot be reverse-geocoded or stored gets a 503, so the
 *    phone keeps it in its queue and sends it again.
 *
 * Only `location` messages are recorded; everything else (lwt, transition,
 * waypoint, card, status...) is acknowledged and dropped, as the recorder's
 * hook did.
 *
 * Contract drift: a location message missing lat/lon/tst is answered 503,
 * never 200, so the phone keeps the points and the "no OwnTracks ping for
 * 12 h" alert fires instead of data vanishing quietly. Unknown `_type`s and
 * unknown location keys are logged as warnings.
 */
object OwnTracks {

    private val json = Json { ignoreUnknownKeys = true }

    /** Location keys the app is documented to send; anything else is logged once. */
    private val KNOWN_KEYS = setOf(
        "_type", "_id", "_http", "lat", "lon", "tst", "created_at", "alt", "batt", "bs", "acc", "vac",
        "conn", "p", "vel", "cog", "m", "t", "tid", "topic", "inregions", "inrids", "SSID", "BSSID",
        "ssid", "bssid", "poi", "tag", "image", "imagename", "motionactivities", "source", "_geoprec",
    )
    private val KNOWN_TYPES = setOf(
        "location", "lwt", "transition", "waypoint", "waypoints", "card", "cmd", "beacon",
        "status", "steps", "encrypted", "configuration", "dump", "request",
    )
    private val warnedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun handlePub(call: ApplicationCall) {
        if (!authorized(call.request.headers[HttpHeaders.Authorization], Config.LOCATION_USER, Config.LOCATION_PASSWORD)) {
            call.response.headers.append(HttpHeaders.WWWAuthenticate, "Basic realm=\"owntracks\"")
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        val msg = runCatching { json.parseToJsonElement(call.receiveText()).jsonObject }.getOrElse {
            KSLog.warning("OwnTracks: unreadable body: ${it.message}")
            call.respond(HttpStatusCode.BadRequest, "Invalid JSON")
            return
        }

        when (val type = msg.str("_type")) {
            "location" -> {}
            in KNOWN_TYPES -> return call.ok()
            else -> {
                KSLog.warning("OwnTracks: unknown _type \"$type\", ignored")
                return call.ok()
            }
        }

        (msg.keys - KNOWN_KEYS).filter { warnedKeys.add(it) }.takeIf { it.isNotEmpty() }?.let {
            KSLog.warning("OwnTracks: new location keys from the app: $it")
        }

        val ping = toPing(msg) ?: run {
            KSLog.warning("OwnTracks: location without lat/lon/tst, keys ${msg.keys}; rejecting so the phone retries")
            call.respond(HttpStatusCode.ServiceUnavailable, "location lacks lat/lon/tst")
            return
        }

        val geo = Geocoder.lookup(ping.ghash, ping.lat, ping.lon) ?: run {
            KSLog.warning("OwnTracks: reverse geocoding failed for ${ping.ghash}; rejecting so the phone retries")
            call.respond(HttpStatusCode.ServiceUnavailable, "reverse geocoding failed")
            return
        }

        WebService.save(ping.toRequest(geo))
            .onSuccess { call.ok() }
            .onFailure { call.respond(HttpStatusCode.ServiceUnavailable, "Not saved: ${it.message}") }
    }

    private suspend fun ApplicationCall.ok() =
        respondText("[]", ContentType.Application.Json)

    /** The raw fields of one location message, before geocoding. */
    data class Ping(val msg: JsonObject, val lat: Double, val lon: Double, val tst: Long, val ghash: String) {
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

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.int(k: String) = num(k)?.toInt() ?: 0
}
