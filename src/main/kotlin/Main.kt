import com.clickhouse.jdbc.ClickHouseDataSource
import com.neovisionaries.i18n.CountryCode
import dev.inmo.krontab.doInfinity
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.micro_utils.common.Warning
import dev.inmo.tgbotapi.AppConfig
import dev.inmo.tgbotapi.Trace
import dev.inmo.tgbotapi.extensions.api.chat.get.getChat
import dev.inmo.tgbotapi.extensions.api.chat.modify.setChatTitle
import dev.inmo.tgbotapi.extensions.api.edit.caption.editMessageCaption
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.extensions.utils.asChannelChat
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.caption
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.longPolling
import dev.inmo.tgbotapi.types.message.content.MediaGroupContent
import dev.inmo.tgbotapi.types.message.content.PhotoContent
import dev.inmo.tgbotapi.types.message.content.VideoContent
import dev.inmo.tgbotapi.types.toChatId
import dev.inmo.tgbotapi.utils.RiskFeature
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotliquery.queryOf
import kotliquery.sessionOf
import net.fellbaum.jemoji.EmojiManager
import java.sql.SQLException
import javax.sql.DataSource
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.engine.cio.*

@OptIn(Warning::class, RiskFeature::class)
suspend fun main() {
    AppConfig.init("current_country_updater")
    val bot = longPolling {
        onContentMessage {
            if (it.chat.id.chatId.long != Config.CHANNEL_ID) return@onContentMessage

            if (it.content is PhotoContent || it.content is MediaGroupContent<*> || it.content is VideoContent) {
                val messageText = it.caption ?: ""
                editMessageCaption(
                    chatId = it.chat.id,
                    messageId =  it.messageId,
                    text = "$messageText\n\n#${getCurrentCountry()} ${getCityName()}",
                )
            } else {
                val messageText = it.text ?: ""
                editMessageText(
                    chatId =  it.chat.id,
                    messageId =  it.messageId,
                    text = "$messageText\n\n#${getCurrentCountry()} ${getCityName()}",
                )
            }
        }
    }.first
    coroutineScope {
        launch {
            doInfinity("0 /10 * * *") {
                val channelCountry =
                    extractCountryCode(
                        bot.getChat(Config.CHANNEL_ID.toChatId()).asChannelChat()!!.title
                    )
                val currentCountry = getCurrentCountry()!!
                KSLog.info("Current $currentCountry channel $channelCountry")
                Trace.save(
                    "checkCountry",
                    mapOf("current" to currentCountry, "channelCountry" to channelCountry),
                )
                if (channelCountry == currentCountry) {
                    KSLog.info("don't need to update country.")
                    return@doInfinity
                }

                bot.setChatTitle(
                    Config.CHANNEL_ID.toChatId(),
                    Config.CHANEL_TITLE_PATTERN.format(countryCodeToEmoji(currentCountry)),
                )
                Trace.save("setCountry", mapOf("old" to channelCountry, "new" to currentCountry))
                KSLog.info("Change country to $currentCountry")
            }
        }
    }
}

fun getCountryCode(country: String): String = CountryCode.findByName("(?i)" + country.replace("&", "and").replace("vietnam", "Viet Nam"))[0].name

fun extractCountryCode(title: String): String {
    val emoji = EmojiManager.extractEmojis(title).first()
    return emoji.description.replace("flag: ", "").lowercase()
}

fun countryCodeToEmoji(country: String): String {
    val upperCaseCountryCode = getCountryCode(country).uppercase()

    val firstChar = upperCaseCountryCode[0] - 'A' + 0x1F1E6
    val secondChar = upperCaseCountryCode[1] - 'A' + 0x1F1E6

    return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
}

val dataSource: DataSource =
    try {
        ClickHouseDataSource(Config.CLICKHOUSE_URL)
    } catch (e: SQLException) {
        throw RuntimeException(e)
    }

fun getCurrentCountry() =
    sessionOf(dataSource)
        .run(
            queryOf(
                    """
            SELECT lower(country) as country
            FROM  country_days_tracker_bot.country_days_tracker
            ORDER BY date_time DESC
            LIMIT 1
         """
                )
                .map { it.string("country") }
                .asSingle
        )

fun getCoordinates() =
    sessionOf(dataSource)
        .run(
            queryOf(
                """
                    SELECT latitude, longitude
                    FROM country_days_tracker_bot.country_days_tracker
                    ORDER BY date_time DESC
                """
            ).map { Pair(it.string("latitude"), it.string("longitude")) }
                .asSingle
        )

@Serializable
data class NominatimResponse(val address: Address?)

@Serializable
data class Address(
    var city: String?,
    val state: String?
)

val json =  Json {ignoreUnknownKeys = true}
suspend fun getCityName(): String? {
    val coordinates = getCoordinates()!!
    return runCatching {
        val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=${coordinates.first}&lon=${coordinates.second}"
        val response: String = HttpClient(CIO).get(url) {
            header("User-Agent", "Mozilla/5.0")
        }.bodyAsText()
        val json = json.decodeFromString<NominatimResponse>(response)
        "#" + (json.address?.city ?: json.address?.state)?.replace(" ", "_")
    }.onFailure { it.printStackTrace() }.getOrDefault("")
}
