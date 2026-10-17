import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.error
import dev.inmo.kslog.common.info
import dev.inmo.kslog.common.warning
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
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.message.content.TextedContent
import dev.inmo.tgbotapi.types.message.content.TextedMediaContent
import dev.inmo.tgbotapi.types.toChatId
import dev.inmo.tgbotapi.utils.buildEntities
import dev.inmo.tgbotapi.utils.regular
import java.time.Duration
import java.time.Instant

object ChannelManager {

    private var cachedCountry: String? = null

    // Telegram's limits for a caption and a message text.
    private const val CAPTION_LIMIT = 1024
    private const val TEXT_LIMIT = 4096

    // Past this the last ping no longer says where the post was written.
    private val STALE_AFTER: Duration = Duration.ofHours(24)

    fun BehaviourContext.registerHashtagHandler(channelId: Long) {
        onContentMessage {
            if (it.chat.id.chatId.long != channelId) return@onContentMessage

            val hashtags = buildLocationHashtags()
            if (hashtags.isEmpty()) return@onContentMessage

            val limit = when (it.content) {
                is TextedMediaContent, is MediaGroupContent<*> -> CAPTION_LIMIT
                is TextContent -> TEXT_LIMIT
                else -> null
            }
            val length = (it.content as? TextedContent)?.text?.length ?: 0
            if (limit != null && length + hashtags.length > limit) {
                KSLog.warning("Skipping hashtags: ${length + hashtags.length} chars would exceed the $limit limit")
                return@onContentMessage
            }

            runCatching {
                when (it.content) {
                    // Photo, video, GIF, document, audio, voice -- anything that carries a caption
                    is TextedMediaContent, is MediaGroupContent<*> -> {
                        val msgSources = it.caption_entities ?: emptyList()
                        editMessageCaption(
                            chatId = it.chat.id,
                            messageId = it.messageId,
                            entities = msgSources + buildEntities { regular(hashtags) }
                        )
                    }
                    is TextContent -> {
                        val msgSources = it.entities ?: emptyList()
                        editMessageText(
                            chatId = it.chat.id,
                            messageId = it.messageId,
                            entities = msgSources + buildEntities { regular(hashtags) }
                        )
                    }
                    // Stickers, video notes, polls, locations, dice: no text to append to
                    else -> KSLog.info("Skipping hashtags for ${it.content::class.simpleName}")
                }
            }.onFailure { e -> KSLog.error("Failed to add hashtags to message ${it.messageId}", e) }
        }
    }

    suspend fun BehaviourContext.updateChannelTitle(channelId: Long, channelTitlePattern: String) {
        val currentCountry = getCurrentCountry()
        if (currentCountry == null) {
            KSLog.info("Current country is not available")
            return
        }

        if (cachedCountry.equals(currentCountry, ignoreCase = true)) {
            KSLog.info("Channel title is already up to date (cached): $currentCountry")
            return
        }

        if (cachedCountry == null) {
            val channelChat = bot.getChat(channelId.toChatId()).asChannelChat()
            if (channelChat == null) {
                KSLog.info("Failed to get channel chat")
                return
            }
            val channelCountry = extractCountryCode(channelChat.title)
            KSLog.info("Current: $currentCountry. Channel: $channelCountry")
            if (channelCountry.equals(currentCountry, ignoreCase = true)) {
                KSLog.info("Channel title is already up to date")
                cachedCountry = currentCountry
                return
            }
        }

        bot.setChatTitle(
            channelId.toChatId(),
            channelTitlePattern.format(countryCodeToEmoji(currentCountry))
        )
        cachedCountry = currentCountry
        KSLog.info("Changed country to $currentCountry")
    }


    private fun getCurrentCountry(): String? {
        val location = DatabaseService.getLastLocation()
        return location?.country?.ifEmpty { null }
    }

    private fun buildLocationHashtags(): String {
        val location = DatabaseService.getLastLocation() ?: return ""
        val age = Duration.between(location.ts, Instant.now())
        if (age > STALE_AFTER) {
            KSLog.warning("Last ping is ${age.toHours()}h old; hashtags may name the wrong place")
        }
        val country = TagManager.sanitizeForHashtag(location.country)
        val city = if (location.locality.isNotEmpty()) {
            "#${TagManager.sanitizeForHashtag(location.locality)}"
        } else {
            ""
        }
        val extraTags = TagManager.getActiveTags()
        val extra = if (extraTags.isNotEmpty()) " ${extraTags.joinToString(" ")}" else ""
        return "\n\n#$country $city$extra"
    }
}
