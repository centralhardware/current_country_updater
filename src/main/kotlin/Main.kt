import com.clickhouse.jdbc.ClickHouseDataSource
import com.neovisionaries.i18n.CountryCode
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.micro_utils.common.Warning
import dev.inmo.tgbotapi.AppConfig
import dev.inmo.tgbotapi.extensions.api.chat.get.getChat
import dev.inmo.tgbotapi.extensions.api.chat.modify.setChatTitle
import dev.inmo.tgbotapi.extensions.utils.asChannelChat
import dev.inmo.tgbotapi.longPolling
import dev.inmo.tgbotapi.types.toChatId
import java.lang.Thread.sleep
import java.sql.SQLException
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import net.fellbaum.jemoji.EmojiManager

@OptIn(Warning::class)
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun main() {
    AppConfig.init("current_country_updater")
    val bot = longPolling {}.first
    while (true) {
        val channelCountry =
            extractCountryCode(
                bot.getChat(System.getenv("CHANEL_ID").toLong().toChatId()).asChannelChat()!!.title
            )
        val currentCountry = getCurrentCountry()!!
        KSLog.info("Current $currentCountry channel $channelCountry")
        if (channelCountry == currentCountry) {
            KSLog.info("don't need to update country.")
            sleep(600000)
            continue
        }

        bot.setChatTitle(
            System.getenv("CHANEL_ID").toLong().toChatId(),
            System.getenv("CHANEL_TITLE_PATTERN").format(countryCodeToEmoji(currentCountry)),
        )
        KSLog.info("Change country to $currentCountry")
        sleep(600000)
    }
}

fun getCountryCode(country: String): String = CountryCode.findByName("(?i)" + country)[0].name

fun extractCountryCode(title: String): String {
    val emoji = EmojiManager.extractEmojis(title).first()
    return emoji.description.replace("flag: ", "").toLowerCase()
}

fun countryCodeToEmoji(country: String): String {
    val upperCaseCountryCode = getCountryCode(country).uppercase()

    val firstChar = upperCaseCountryCode[0] - 'A' + 0x1F1E6
    val secondChar = upperCaseCountryCode[1] - 'A' + 0x1F1E6

    return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
}

val dataSource: DataSource =
    try {
        ClickHouseDataSource(System.getenv("CLICKHOUSE_URL"))
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
