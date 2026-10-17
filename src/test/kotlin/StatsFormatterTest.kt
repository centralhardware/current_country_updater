import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StatsFormatterTest {
    @Test
    fun `singular units for one`() {
        assertEquals("1 year, 1 month, 3 days", formatDuration(400))
    }

    @Test
    fun `plural units`() {
        assertEquals("2 weeks", formatDuration(14))
    }

    @Test
    fun `bare days are not repeated`() {
        assertEquals("", formatDuration(5))
    }

    @Test
    fun `stat lines keep the country name and space the duration`() {
        val text = formatCountryStats(listOf("Malaysia" to 400, "Oman" to 5), "Malaysia" to 30)
        val lines = text.lines()
        assertEquals("1 - Malaysia - 400 (1 year, 1 month, 3 days)", lines[0])
        assertEquals("2 - Oman - 5", lines[1])
        assertEquals("Visited 2 countries and territories (1.04% of 193 UN states)", lines[3])
    }
}
