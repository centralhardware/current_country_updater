import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EmojiFlagTest {
    @Test
    fun `countryCodeToEmoji creates correct regional indicators for US`() {
        val emoji = countryCodeToEmoji("United States")
        // 🇺🇸 as composed string
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", emoji)
    }

    @Test
    fun `countryCodeToEmoji supports South Korea`() {
        // Use the official name used by nv-i18n for South Korea
        val emoji = countryCodeToEmoji("South Korea")
        // 🇰🇷 as composed string
        assertEquals("\uD83C\uDDF0\uD83C\uDDF7", emoji)
        // round trip: should parse to "south korea"
        val parsed = extractCountryCode("Title $emoji")
        assertEquals("south korea", parsed)
    }

    @Test
    fun `countryCodeToEmoji handles vietnam alias`() {
        val emoji = countryCodeToEmoji("vietnam")
        // Vietnam flag 🇻🇳
        assertEquals("\uD83C\uDDFB\uD83C\uDDF3", emoji)
    }

    @Test
    fun `getCountryCode replaces ampersand with and`() {
        val code = getCountryCode("Trinidad & Tobago")
        assertEquals("TT", code)
    }

    @Test
    fun `extractCountryCode parses country name from title with single flag`() {
        val emoji = countryCodeToEmoji("Ukraine") // 🇺🇦
        val title = "My Channel $emoji"
        val country = extractCountryCode(title)
        assertEquals("ukraine", country)
    }

    @Test
    fun `extractCountryCode takes first emoji when multiple present`() {
        val first = countryCodeToEmoji("Germany")
        val second = countryCodeToEmoji("France")
        val title = "News $first vs $second"
        val country = extractCountryCode(title)
        assertEquals("germany", country)
    }

    @Test
    fun `countryCodeToEmoji and extractCountryCode round trip`() {
        val countries = listOf("Poland", "Canada", "Japan")
        for (c in countries) {
            val e = countryCodeToEmoji(c)
            val parsed = extractCountryCode("Title $e")
            assertEquals(c.lowercase(), parsed)
        }
    }
}
