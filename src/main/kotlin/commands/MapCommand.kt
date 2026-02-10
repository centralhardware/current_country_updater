package commands

import DatabaseService
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.sendLocation
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand

fun BehaviourContext.registerMapCommand() {
    onCommand("map") { message ->
        val lastLocation = DatabaseService.getLastLocation()
        if (lastLocation == null) {
            reply(message, "No location data available")
            return@onCommand
        }

        sendLocation(
            chatId = message.chat.id,
            latitude = lastLocation.latitude.toDouble(),
            longitude = lastLocation.longitude.toDouble()
        )
    }
}
