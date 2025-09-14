import com.neovisionaries.i18n.CountryCode
import net.fellbaum.jemoji.EmojiManager

fun getCountryCode(country: String): String {
    val normalized = country
        .replace("&", "and")
        .replace("vietnam", "Viet Nam")
        .replace("South Korea", "Korea, Republic of")
        .trim()

    val matches = CountryCode.findByName("(?i)" + normalized)
    require(matches.isNotEmpty()) { "Unknown country: $country" }
    return matches[0].name
}

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
