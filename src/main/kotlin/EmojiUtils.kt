import com.neovisionaries.i18n.CountryCode
import net.fellbaum.jemoji.EmojiManager
import java.util.Locale

/**
 * The tracker stores `Locale.of("en", code).displayCountry`, so the exact
 * inverse is the first lookup: it knows every name the tracker can have
 * written ("Vietnam", "Türkiye", "Bosnia & Herzegovina"), which nv-i18n's
 * official names do not.
 */
private val codeByDisplayName: Map<String, String> =
    Locale.getISOCountries().associateBy { Locale.of("en", it).displayCountry.lowercase() }

fun getCountryCode(country: String): String {
    codeByDisplayName[country.trim().lowercase()]?.let { return it }

    // Older rows and hand-written trips may use other spellings.
    val normalized = country
        .replace("&", "and")
        .replace("South Korea", "Korea, Republic of")
        .trim()

    val matches = CountryCode.findByName("(?i)" + Regex.escape(normalized))
    require(matches.isNotEmpty()) { "Unknown country: $country" }
    return matches[0].name
}

fun extractCountryCode(title: String): String? {
    val emoji = EmojiManager.extractEmojisInOrder(title).firstOrNull() ?: return null
    return emoji.description.replace("flag: ", "").lowercase()
}

fun countryCodeToEmoji(country: String): String {
    val upperCaseCountryCode = getCountryCode(country).uppercase()

    val firstChar = upperCaseCountryCode[0] - 'A' + 0x1F1E6
    val secondChar = upperCaseCountryCode[1] - 'A' + 0x1F1E6

    return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
}
