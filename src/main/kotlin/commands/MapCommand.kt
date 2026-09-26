package commands

import Config
import DatabaseService
import WebService
import dev.inmo.tgbotapi.extensions.api.EditLiveLocationInfo
import dev.inmo.tgbotapi.extensions.api.handleLiveLocation
import dev.inmo.tgbotapi.extensions.api.deleteMessage
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.types.MessageId
import kotlinx.coroutines.flow.FlowCollector
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onGuestRequestMessage
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.InlineQueryResultLocation
import dev.inmo.tgbotapi.types.InlineQueryId
import dev.inmo.tgbotapi.types.message.abstracts.FromUserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

private val LIVE_PERIOD = 8.hours

/** One live location per chat: a new /map replaces the previous one. */
private val liveJobs = ConcurrentHashMap<Long, Job>()

/** The live location message last sent to each chat, deleted when a new /map arrives. */
private val liveMessages = ConcurrentHashMap<Long, MessageId>()


private fun liveLocations(lat: Double, lon: Double): Flow<EditLiveLocationInfo> =
    WebService.pings
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
                    latitude = lat,
                    longitude = lon,
                )
            )
        }

/**
 * Shares the current location as a Telegram live location, moved on every
 * incoming ping for [LIVE_PERIOD] instead of a one-off static pin.
 */
fun BehaviourContext.registerMapCommand() {
    onCommand("map", initialFilter = { (it as? FromUserMessage)?.from?.id?.chatId?.long == Config.MAP_OWNER_ID }) { message ->
        val lastLocation = DatabaseService.getLastLocation()
        if (lastLocation == null) {
            reply(message, "No location data available")
            return@onCommand
        }

        val chatId = message.chat.id
        liveJobs.remove(chatId.chatId.long)?.cancel()
        liveMessages.remove(chatId.chatId.long)?.let { runCatching { deleteMessage(chatId, it) } }

        val locations = liveLocations(lastLocation.latitude.toDouble(), lastLocation.longitude.toDouble())

        val job = this@registerMapCommand.launch {
            handleLiveLocation(
                chatId,
                locations,
                liveTimeMillis = LIVE_PERIOD.inWholeMilliseconds,
                sentMessageFlow = FlowCollector { liveMessages[chatId.chatId.long] = it.messageId },
            )
        }
        liveJobs[chatId.chatId.long] = job
        job.invokeOnCompletion { liveJobs.remove(chatId.chatId.long, job) }
    }

    // Guest answers are a static pin for now: a live one would need edits by
    // inline message id, and Telegram duplicates every answerGuestQuery.
    onGuestRequestMessage(initialFilter = { message ->
        val from = message.from.id.chatId.long
        val allowed = from == Config.MAP_OWNER_ID ||
            (from == Config.MAP_GUEST_ID && message.chat.id.chatId.long == Config.MAP_OWNER_ID)
        allowed && message.text?.contains("/map") == true
    }) { message ->
        val lastLocation = DatabaseService.getLastLocation() ?: return@onGuestRequestMessage
        reply(
            message,
            InlineQueryResultLocation(
                id = InlineQueryId(message.guestQueryId.string),
                latitude = lastLocation.latitude.toDouble(),
                longitude = lastLocation.longitude.toDouble(),
                title = "Current location",
            ),
        )
    }
}
