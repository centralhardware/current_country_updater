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
 *  - HTTP Basic auth (LOCATION_USER / LOCATION_PASSWORD);
 *  - the body is one OwnTracks JSON message, `_type` says what it is;
 *  - the reply is a JSON array (the recorder's "friends" list) -- always `[]`;
 *  - a location that cannot be reverse-geocoded or stored gets a 503, so the
 *    phone keeps it in its queue and sends it again.
 *
 * Only `location` messages are recorded; everything else (lwt, transition,
 * waypoint, card, status...) is acknowledged and dropped, as the recorder's
 * hook did.
 *
 * Contract drift: the request fails (503) when the JSON differs from what we
 * expect at all -- an unknown `_type`, an unknown location key, a missing
 * lat/lon/tst, or a field of the wrong JSON type. The phone keeps those points
 * queued and the "no OwnTracks ping for 12 h" alert fires, instead of data
 * being silently misread.
 */
object OwnTracks {

    private enum class Kind { NUM, STR, BOOL, ARR }

    /** Every key a location may carry, with its JSON type; anything else is a contract change. */
    private val SCHEMA: Map<String, Kind> = buildMap {
        listOf("lat", "lon", "tst", "created_at", "alt", "batt", "bs", "acc", "vac", "vel", "cog", "m", "p", "_geoprec")
            .forEach { put(it, Kind.NUM) }
        listOf("_type", "_id", "tid", "topic", "conn", "t", "SSID", "BSSID", "ssid", "bssid", "poi", "tag",
            "image", "imagename", "source").forEach { put(it, Kind.STR) }
        listOf("inregions", "inrids", "motionactivities").forEach { put(it, Kind.ARR) }
        put("_http", Kind.BOOL)
    }
    private val REQUIRED = listOf("lat", "lon", "tst")
    private val KNOWN_TYPES = setOf(
        "location", "lwt", "transition", "waypoint", "waypoints", "card", "cmd", "beacon",
        "status", "steps", "encrypted", "configuration", "dump", "request",
    )

    /** Every way [msg] differs from the location contract; empty when it matches exactly. */
    internal fun violations(msg: JsonObject): List<String> = buildList {
        REQUIRED.filter { it !in msg }.forEach { add("missing $it") }
        for ((k, v) in msg) {
            val kind = SCHEMA[k] ?: run { add("unknown key $k"); continue }
            val ok = when (kind) {
                Kind.NUM -> v is JsonPrimitive && !v.isString && v.doubleOrNull != null
                Kind.STR -> v is JsonPrimitive && v.isString
                Kind.BOOL -> v is JsonPrimitive && !v.isString && v.booleanOrNull != null
                Kind.ARR -> v is JsonArray
            }
            if (!ok) add("$k is not ${kind.name.lowercase()}: $v")
        }
    }

    suspend fun handlePub(call: ApplicationCall) {
        if (!authorized(call.request.headers[HttpHeaders.Authorization], Config.LOCATION_USER, Config.LOCATION_PASSWORD)) {
            call.response.headers.append(HttpHeaders.WWWAuthenticate, "Basic realm=\"owntracks\"")
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        val msg = runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }.getOrElse {
            KSLog.warning("OwnTracks: unreadable body: ${it.message}")
            call.respond(HttpStatusCode.BadRequest, "Invalid JSON")
            return
        }

        when (val type = msg.str("_type")) {
            "location" -> {}
            in KNOWN_TYPES -> return call.ok()
            else -> return call.reject("unknown _type \"$type\"")
        }

        violations(msg).takeIf { it.isNotEmpty() }?.let { return call.reject("location contract changed: ${it.joinToString("; ")}") }
        val ping = toPing(msg)!!

        val geo = Geocoder.lookup(ping.ghash, ping.lat, ping.lon) ?: run {
            KSLog.warning("OwnTracks: reverse geocoding failed for ${ping.ghash}; rejecting so the phone retries")
            call.respond(HttpStatusCode.ServiceUnavailable, "reverse geocoding failed")
            return
        }

        WebService.save(ping.toRequest(geo))
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
