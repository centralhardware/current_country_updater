package commands

import Config
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.kslog.common.warning
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import DatabaseService
import WebService
import dev.inmo.tgbotapi.extensions.api.EditLiveLocationInfo
import dev.inmo.tgbotapi.extensions.api.handleLiveLocation
import dev.inmo.tgbotapi.extensions.api.edit.location.live.editLiveLocation
import dev.inmo.tgbotapi.extensions.api.deleteMessage
import dev.inmo.tgbotapi.extensions.api.edit.location.live.stopLiveLocation
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.types.InlineMessageId
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
import kotlinx.coroutines.flow.takeWhile
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

/** Guest answers are inline messages, which a bot cannot delete, so the old one is only stopped. */
private val guestJobs = ConcurrentHashMap<Long, Pair<Job, InlineMessageId>>()

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

    onGuestRequestMessage(initialFilter = { message ->
        val from = message.from.id.chatId.long
        val allowed = from == Config.MAP_OWNER_ID ||
            (from == Config.MAP_GUEST_ID && message.chat.id.chatId.long == Config.MAP_OWNER_ID)
        allowed && message.text?.contains("/map") == true
    }) { message ->
        val lastLocation = DatabaseService.getLastLocation() ?: return@onGuestRequestMessage
        val guestChat = message.chat.id.chatId.long
        guestJobs.remove(guestChat)?.let { (job, inlineMessageId) ->
            job.cancel()
            runCatching { stopLiveLocation(inlineMessageId) }
        }
        val lat = lastLocation.latitude.toDouble()
        val lon = lastLocation.longitude.toDouble()
        val answer = runCatching {
            execute(
                AnswerGuestQueryRaw(
                    message.guestQueryId,
                    InlineQueryResultLocation(
                        id = InlineQueryId(message.guestQueryId.string),
                        latitude = lat,
                        longitude = lon,
                        title = "Current location",
                        livePeriod = LIVE_PERIOD.inWholeSeconds.toInt(),
                    ),
                )
            )
        }.onFailure { KSLog.warning("answerGuestQuery failed", it) }.getOrNull()
        KSLog.info("answerGuestQuery response: $answer")
        val inlineMessageId = (answer as? JsonObject)?.get("inline_message_id")?.jsonPrimitive?.contentOrNull
            ?.let(::InlineMessageId)
            ?: return@onGuestRequestMessage
        val until = System.currentTimeMillis() + LIVE_PERIOD.inWholeMilliseconds
        val job = this@registerMapCommand.launch {
            WebService.pings
                .takeWhile { System.currentTimeMillis() < until }
                .collect { ping ->
                    runCatching {
                        editLiveLocation(
                            inlineMessageId,
                            ping.latitude.toDouble(),
                            ping.longitude.toDouble(),
                            ping.acc.takeIf { it in 1..1500 }?.toFloat(),
                            ping.cog.takeIf { ping.vel > 0 && it in 1..360 },
                        )
                    }
                }
        }
        val entry = job to inlineMessageId
        guestJobs[guestChat] = entry
        job.invokeOnCompletion { guestJobs.remove(guestChat, entry) }
    }
}
