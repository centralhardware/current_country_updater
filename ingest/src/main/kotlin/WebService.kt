import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** The web server: OwnTracks /pub, the calendar feed, and /health for the container probe. */
object WebService {

    fun start(port: Int = 80): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        KSLog.info("Starting web service on port $port")
        return embeddedServer(Netty, port = port) {
            install(ContentNegotiation) { json() }
            routing {
                ownTracks()
                get("/health") { call.respondText("OK") }
                get("/calendar/${IngestConfig.CALENDAR_SECRET}/calendar.ics") {
                    call.respondText(CalendarService.buildCalendar(), ContentType("text", "calendar"))
                }
            }
        }.start(wait = true)
    }
}
