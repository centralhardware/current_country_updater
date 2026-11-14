package commands

import Config
import DatabaseService
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val LIVE_PERIOD = 8.hours

/** One live location per chat: a new /map replaces the previous one. */
private val liveJobs = ConcurrentHashMap<Long, Job>()

/** The live location message last sent to each chat, deleted when a new /map arrives. */
private val liveMessages = ConcurrentHashMap<Long, MessageId>()


private val POLL_INTERVAL = 15.seconds

/**
 * The web server is a separate process, so new pings are picked up from
 * ClickHouse: the newest row, polled, emitted only when it moves forward in
 * time (a late backlog must not drag the pin back).
 */
private fun liveLocations(start: DatabaseService.Location): Flow<EditLiveLocationInfo> = flow {
    var last = start
    emit(EditLiveLocationInfo(latitude = start.latitude.toDouble(), longitude = start.longitude.toDouble()))
    while (true) {
        delay(POLL_INTERVAL)
        val next = runCatching { DatabaseService.getLastLocation() }.getOrNull() ?: continue
        if (!next.ts.isAfter(last.ts)) continue
        last = next
        emit(EditLiveLocationInfo(latitude = next.latitude.toDouble(), longitude = next.longitude.toDouble()))
    }
}

/**
 * Shares the current location as a Telegram live location, moved on every
 * new ping for [LIVE_PERIOD] instead of a one-off static pin.
 */
fun BehaviourContext.registerMapCommand() {
    onCommand("map", initialFilter = fromOwner) { message ->
        val lastLocation = DatabaseService.getLastLocation()
        if (lastLocation == null) {
            reply(message, "No location data available")
            return@onCommand
        }

        val chatId = message.chat.id
        liveJobs.remove(chatId.chatId.long)?.cancel()
        liveMessages.remove(chatId.chatId.long)?.let { runCatching { deleteMessage(chatId, it) } }

        val locations = liveLocations(lastLocation)

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
