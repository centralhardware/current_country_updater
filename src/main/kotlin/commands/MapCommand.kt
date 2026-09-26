package commands

import DatabaseService
import WebService
import dev.inmo.tgbotapi.extensions.api.EditLiveLocationInfo
import dev.inmo.tgbotapi.extensions.api.handleLiveLocation
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

private val LIVE_PERIOD = 8.hours

/** One live location per chat: a new /map replaces the previous one. */
private val liveJobs = ConcurrentHashMap<Long, Job>()

/**
 * Shares the current location as a Telegram live location, moved on every
 * incoming ping for [LIVE_PERIOD] instead of a one-off static pin.
 */
fun BehaviourContext.registerMapCommand() {
    onCommand("map") { message ->
        val lastLocation = DatabaseService.getLastLocation()
        if (lastLocation == null) {
            reply(message, "No location data available")
            return@onCommand
        }

        val chatId = message.chat.id
        liveJobs.remove(chatId.chatId.long)?.cancel()

        val locations = WebService.pings
            .map { ping ->
                EditLiveLocationInfo(
                    latitude = ping.latitude.toDouble(),
                    longitude = ping.longitude.toDouble(),
                    horizontalAccuracy = ping.acc.takeIf { it in 1..1500 }?.toFloat(),
                    heading = ping.cog.takeIf { ping.vel > 0 && it in 1..360 },
                )
            }
            .onStart {
                emit(
                    EditLiveLocationInfo(
                        latitude = lastLocation.latitude.toDouble(),
                        longitude = lastLocation.longitude.toDouble(),
                    )
                )
            }

        val job = launch {
            handleLiveLocation(chatId, locations, liveTimeMillis = LIVE_PERIOD.inWholeMilliseconds)
        }
        liveJobs[chatId.chatId.long] = job
        job.invokeOnCompletion { liveJobs.remove(chatId.chatId.long, job) }
    }
}
