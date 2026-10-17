package commands

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ParseDurationTest {
    @Test
    fun `accepts minutes hours and days`() {
        listOf("30m", "1h", "2D").forEach { assertNotNull(parseDuration(it), it) }
    }

    @Test
    fun `rejects unknown units, zero and garbage`() {
        listOf("2w", "0m", "h", "1.5h", "99999999999999999999d").forEach { assertNull(parseDuration(it), it) }
    }
}
