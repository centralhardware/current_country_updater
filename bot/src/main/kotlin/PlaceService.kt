import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.warning
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * The public place at a coordinate, from the place describer (`PLACE_URL`, a small
 * headless-claude service over OpenStreetMap data; OpenCage named places poorly).
 * Answers are cached per geohash-7 cell, empty ones too, so a cell costs one call.
 */
object PlaceService {
    private val url: String? = System.getenv("PLACE_URL")?.trimEnd('/')?.ifBlank { null }
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val cache = ConcurrentHashMap<String, String>()

    /** The place's one-line description, or null: no service, no answer, or not a public place. */
    fun describe(ghash: String, lat: Double, lon: Double, acc: Int?): String? {
        val base = url ?: return null
        cache[ghash]?.let { return it.ifEmpty { null } }
        val body = buildJsonObject { put("lat", lat); put("lon", lon); acc?.let { put("acc", it) } }.toString()
        val place = runCatching {
            val response = client.send(
                HttpRequest.newBuilder(URI("$base/place"))
                    .timeout(Duration.ofSeconds(70))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            check(response.statusCode() == 200) { "HTTP ${response.statusCode()}: ${response.body().take(200)}" }
            (Json.parseToJsonElement(response.body()).jsonObject["place"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        }.onFailure { KSLog.warning("Place describer failed for $ghash: ${it.message}") }.getOrNull() ?: return null
        cache[ghash] = place
        return place.ifEmpty { null }
    }
}
