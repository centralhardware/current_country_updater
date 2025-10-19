import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object GeocodingService {

    private val httpClient = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCityName(latitude: Float, longitude: Float): String? {
        return runCatching {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude"
            val response: String = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0")
            }.bodyAsText()
            val nominatimResponse = json.decodeFromString<NominatimResponse>(response)
            val cityName = nominatimResponse.address?.city ?: nominatimResponse.address?.state
            cityName?.let { "#${it.replace(" ", "_")}" }
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    @Serializable
    data class NominatimResponse(val address: Address?)

    @Serializable
    data class Address(
        val city: String?,
        val state: String?
    )
}
