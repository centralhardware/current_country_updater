import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.edit.location.live.editLiveLocation
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object LocationSubscriptionManager {
    private val activeSubscriptions = ConcurrentHashMap<IdChatIdentifier, Pair<MessageId, Instant>>()

    fun addSubscription(chatId: IdChatIdentifier, messageId: MessageId) {
        activeSubscriptions[chatId] = Pair(messageId, Instant.now())
    }

    fun cleanupExpiredSubscriptions() {
        val now = Instant.now()
        activeSubscriptions.entries.removeIf { (_, pair) ->
            val (_, startTime) = pair
            val duration = Duration.between(startTime, now)
            duration.toMinutes() >= Config.SUBSCRIPTION_DURATION_MINUTES
        }
    }

    suspend fun BehaviourContext.updateAllSubscribers(latitude: Float, longitude: Float) {
        activeSubscriptions.forEach { (chatId, pair) ->
            val (messageId, _) = pair
            runCatching {
                editLiveLocation(
                    chatId = chatId,
                    messageId = messageId,
                    latitude = latitude.toDouble(),
                    longitude = longitude.toDouble()
                )
                KSLog.info("Updated location for chat $chatId to $latitude, $longitude")
            }.onFailure { e ->
                KSLog.info("Failed to update location for chat $chatId: ${e.message}")
            }
        }
    }
}
