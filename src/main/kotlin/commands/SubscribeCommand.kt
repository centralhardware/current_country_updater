package commands

import DatabaseService
import LocationSubscriptionManager
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.sendLocation
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand

fun BehaviourContext.registerSubscribeCommand() {
    onCommand("subscribe") { message ->
        val chatId = message.chat.id

        val lastLocation = DatabaseService.getLastLocation()
        val lat = lastLocation?.latitude?.toDouble() ?: 0.0
        val lon = lastLocation?.longitude?.toDouble() ?: 0.0

        val locationMessage = sendLocation(
            chatId = chatId,
            latitude = lat,
            longitude = lon,
            livePeriod = 1800
        )

        LocationSubscriptionManager.addSubscription(chatId, locationMessage.messageId)

        reply(message, "📍 Subscribed! Live location will update automatically when new coordinates arrive.")
    }
}
