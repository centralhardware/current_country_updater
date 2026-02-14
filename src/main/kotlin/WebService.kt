import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.*

object EpochSecondsInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("EpochSecondsInstant", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochSecond(decoder.decodeLong())
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.epochSecond)
}

@Serializable
data class LocationRequest(
    val latitude: Float,
    val longitude: Float,
    val timezone: String,
    val country: String,
    @Serializable(with = EpochSecondsInstantSerializer::class)
    val timestamp: Instant,
    val alt: Int,
    val batt: Int,
    val acc: Int,
    val vac: Int,
    val conn: String,
    val locality: String,
    val ghash: String,
    val p: Double,
    val addr: String,
    val bs: Int? = null
)

private val json = Json { ignoreUnknownKeys = true }

object WebService {

    fun start(port: Int = 80): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        KSLog.info("Starting web service on port $port")

        return embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(json)
            }

            routing {
                post("/location") {
                    handleLocationUpdate(call)
                }
            }
        }.start(wait = false)
    }

    private suspend fun handleLocationUpdate(call: ApplicationCall) {
        val bodyString = call.receiveText()
        val body = try {
            json.decodeFromString<LocationRequest>(bodyString)
        } catch (e: Exception) {
            KSLog.info("Failed to parse location update: ${e.message}. Body: $bodyString")
            call.respond(HttpStatusCode.BadRequest, "Invalid request: ${e.message}")
            return
        }

        KSLog.info("Processing location update: $body")

        val validatedAlt = if (body.alt !in 0..14000) {
            KSLog.info("Altitude ${body.alt} is out of 0..14000 range, setting to 0")
            0
        } else {
            body.alt
        }

        call.application.launch(Dispatchers.IO) {
            runCatching {
                DatabaseService.save(
                    body.timestamp
                        .atZone(body.timezone.toTimeZone())
                        .toLocalDateTime(),
                    body.latitude,
                    body.longitude,
                    body.timezone.toTimeZone(),
                    body.country.toCountry(),
                    validatedAlt,
                    body.batt,
                    body.acc,
                    body.vac,
                    body.conn,
                    body.locality,
                    body.ghash,
                    body.p,
                    body.addr,
                    body.bs ?: 0
                )
            }.onSuccess {
                KSLog.info("Successfully saved location update")
            }.onFailure { error ->
                KSLog.info("Failed to save location update: ${error.message}. Body: $bodyString")
            }
        }

        call.respond(HttpStatusCode.OK)
    }

    private fun String.toTimeZone() = TimeZone.getTimeZone(this).toZoneId()

    private fun String.toCountry() = Locale.of("en", this).displayCountry
}