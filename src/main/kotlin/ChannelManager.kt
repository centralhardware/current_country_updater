import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.chat.get.getChat
import dev.inmo.tgbotapi.extensions.api.chat.modify.setChatTitle
import dev.inmo.tgbotapi.extensions.api.edit.caption.editMessageCaption
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.extensions.utils.asChannelChat
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.caption_entities
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.entities
import dev.inmo.tgbotapi.types.message.content.MediaGroupContent
import dev.inmo.tgbotapi.types.message.content.PhotoContent
import dev.inmo.tgbotapi.types.message.content.VideoContent
import dev.inmo.tgbotapi.types.toChatId
import dev.inmo.tgbotapi.utils.buildEntities
import dev.inmo.tgbotapi.utils.regular

object ChannelManager {

    fun BehaviourContext.registerHashtagHandler(channelId: Long) {
        onContentMessage {
            if (it.chat.id.chatId.long != channelId) return@onContentMessage

            val hashtags = buildLocationHashtags()

            when (it.content) {
                is PhotoContent, is MediaGroupContent<*>, is VideoContent -> {
                    val msgSources = it.caption_entities ?: emptyList()
                    editMessageCaption(
                        chatId = it.chat.id,
                        messageId = it.messageId,
                        entities = msgSources + buildEntities { regular(hashtags) }
                    )
                }
                else -> {
                    val msgSources = it.entities ?: emptyList()
                    editMessageText(
                        chatId = it.chat.id,
                        messageId = it.messageId,
                        entities = msgSources + buildEntities { regular(hashtags) }
                    )
                }
            }
        }
    }

    suspend fun BehaviourContext.updateChannelTitle(channelId: Long, channelTitlePattern: String) {
        val channelChat = bot.getChat(channelId.toChatId()).asChannelChat()
        if (channelChat == null) {
            KSLog.info("Failed to get channel chat")
            return
        }

        val channelCountry = extractCountryCode(channelChat.title)
        val currentCountry = getCurrentCountry()
        if (currentCountry == null) {
            KSLog.info("Current country is not available")
            return
        }

        KSLog.info("Current: $currentCountry. Channel: $channelCountry")
        if (channelCountry == currentCountry) {
            KSLog.info("Channel title is already up to date")
            return
        }

        bot.setChatTitle(
            channelId.toChatId(),
            channelTitlePattern.format(countryCodeToEmoji(currentCountry))
        )
        KSLog.info("Changed country to $currentCountry")
    }


    private fun getCurrentCountry(): String? {
        val location = DatabaseService.getLastLocation()
        return location?.country
    }

    private fun buildLocationHashtags(): String {
        val location = DatabaseService.getLastLocation() ?: return ""
        val country = location.country.replace(" ", "_")
        val city = if (location.locality.isNotEmpty()) {
            "#${location.locality.replace(" ", "_")}"
        } else {
            ""
        }
        return "\n\n#$country $city"
    }
}
