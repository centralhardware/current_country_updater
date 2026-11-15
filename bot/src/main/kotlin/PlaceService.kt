import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.warning
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * The public place at a coordinate, from the place describer (`PLACE_URL`, a small
 * headless-claude service over OpenStreetMap data; OpenCage named places poorly).
 * Answers are cached per geohash-7 cell, empty ones too, so a cell costs one call.
 */
object PlaceService {
    @Serializable
    private data class Request(val lat: Double, val lon: Double, val acc: Int? = null)

    @Serializable
    private data class Response(val place: String = "")

    private val url: String? = System.getenv("PLACE_URL")?.trimEnd('/')?.ifBlank { null }
    private val cache = ConcurrentHashMap<String, String>()

    private val client = HttpClient(CIO) {
        install(HttpTimeout) { connectTimeoutMillis = 5_000; requestTimeoutMillis = 70_000 }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        expectSuccess = false
    }

    /** The place's one-line description, or null: no service, no answer, or not a public place. */
    suspend fun describe(ghash: String, lat: Double, lon: Double, acc: Int?): String? {
        val base = url ?: return null
        cache[ghash]?.let { return it.ifEmpty { null } }
        val place = runCatching {
            val response = client.post("$base/place") {
                contentType(ContentType.Application.Json)
                setBody(Request(lat, lon, acc))
            }
            check(response.status.isSuccess()) { "${response.status}: ${response.bodyAsText().take(200)}" }
            response.body<Response>().place.trim()
        }.onFailure { KSLog.warning("Place describer failed for $ghash: ${it.message}") }.getOrNull() ?: return null
        cache[ghash] = place
        return place.ifEmpty { null }
    }
}
