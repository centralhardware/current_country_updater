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

    // The place line needs a ping this fresh: it names a spot, not a city.
    private val PLACE_FRESH_FOR: Duration = Duration.ofMinutes(15)

    // Anything this close to where he sleeps is home, whatever OpenCage calls the cell.
    private const val HOME_RADIUS_METERS = 300.0

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
            // In an album the hashtags go to the item that shows the caption: Telegram lays
            // per-item captions over the album, so tagging the first photo hides them when
            // another photo carries the text.
            val target = (it.content as? MediaGroupContent<*>)?.group?.let { group ->
                group.lastOrNull { part -> !part.content.text.isNullOrEmpty() } ?: group.first()
            }
            val length = (target?.content ?: it.content as? TextedContent)?.text?.length ?: 0
            if (limit != null && length + hashtags.length > limit) {
                KSLog.warning("Skipping hashtags: ${length + hashtags.length} chars would exceed the $limit limit")
                return@onContentMessage
            }

            runCatching {
                when (it.content) {
                    // Photo, video, GIF, document, audio, voice -- anything that carries a caption
                    is MediaGroupContent<*> -> {
                        editMessageCaption(
                            chatId = it.chat.id,
                            messageId = target!!.sourceMessage.messageId,
                            entities = target.content.textSources + buildEntities { regular(hashtags) }
                        )
                    }
                    is TextedMediaContent -> {
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
        val place = currentPlace(location)?.let { "📍 $it\n" }.orEmpty()
        return "\n\n$place#$country $city$extra"
    }

    /** The public place he is at right now, or null: stale ping, no named place, near home or home unknown. */
    private fun currentPlace(location: DatabaseService.Location): String? {
        if (location.ghash.isEmpty() || Duration.between(location.ts, Instant.now()) > PLACE_FRESH_FOR) return null
        val place = Geocoder.place(location.ghash) ?: return null
        val homes = runCatching { DatabaseService.getSleepPlaces() }
            .onFailure { KSLog.warning("Home lookup failed, leaving the place out: ${it.message}") }
            .getOrElse { return null }
        // Not knowing where home is means not knowing it isn't here: say nothing.
        if (homes.isEmpty()) return null
        val lat = location.latitude.toDouble()
        val lon = location.longitude.toDouble()
        if (homes.any { (hLat, hLon) -> distanceMeters(hLat, hLon, lat, lon) < HOME_RADIUS_METERS }) return null
        return place
    }
}

/** Great-circle distance, haversine. */
internal fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(a))
}
