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
import java.time.ZoneId
import java.time.ZoneOffset
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
    val vel: Int = 0,
    val cog: Int = 0,
    val m: Int = 0,
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
                get("/calendar/${Config.CALENDAR_SECRET}/calendar.ics") {
                    handleCalendar(call)
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
                val zone = body.timezone.toZone()
                DatabaseService.save(
                    body.timestamp,
                    zone.rules.getOffset(body.timestamp).totalSeconds,
                    body.latitude,
                    body.longitude,
                    zone.id,
                    body.country.toCountry(),
                    validatedAlt,
                    body.batt,
                    body.acc,
                    body.vac,
                    body.conn,
                    LocalityOverrideManager.resolve(body.locality),
                    body.ghash,
                    body.p,
                    body.addr,
                    body.vel,
                    body.cog,
                    body.m,
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

    private suspend fun handleCalendar(call: ApplicationCall) {
        call.respondText(CalendarService.buildCalendar(), ContentType("text", "calendar"))
    }

    /**
     * The `timezone` field is whatever the phone had to hand. It is usually an
     * IANA name ("Asia/Bangkok"), but it arrives as a bare offset often enough
     * to matter -- "+07:00" from a device with no zone database, and the
     * Android-style "GMT-06:00".
     *
     * `TimeZone.getTimeZone` must not be used for this: for anything it does
     * not recognise it silently returns GMT. A phone reporting "+07:00" was
     * being logged as UTC, seven hours out, with nothing in the logs to say
     * so -- which is where the 22108 rows of plain `GMT` in the table came
     * from.
     *
     * `ZoneId.of` parses the IANA names and the offset forms alike, and throws
     * on anything it cannot read rather than guessing. SHORT_IDS covers the
     * three-letter aliases ("PST", "IST"). A string past all of those is
     * logged and treated as UTC -- the same result as before, but said out
     * loud.
     *
     * Whatever it resolves to, only `zone.id` is stored and only for the
     * record: the arithmetic runs on the offset, so an offset-only zone is
     * exactly as usable here as a named one.
     */
    private fun String.toZone(): ZoneId =
        runCatching { ZoneId.of(this) }
            .recoverCatching { ZoneId.of(this, ZoneId.SHORT_IDS) }
            .recoverCatching { ZoneOffset.of(this) }
            .getOrElse {
                KSLog.info("Unrecognised timezone \"$this\", falling back to UTC")
                ZoneOffset.UTC
            }

    private fun String.toCountry() = Locale.of("en", this).displayCountry
}