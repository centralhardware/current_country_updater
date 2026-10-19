import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebServiceTest {

    @Test
    fun `timezone forms the phone sends`() {
        val now = Instant.parse("2026-01-15T00:00:00Z")
        for ((input, offsetHours) in listOf(
            "Asia/Bangkok" to 7,
            "+07:00" to 7,
            "GMT-06:00" to -6,
            "UTC" to 0,
            "Europe/London" to 0,
            "PST" to -8,
        )) {
            assertEquals(offsetHours * 3600, input.toZone().rules.getOffset(now).totalSeconds, input)
        }
    }

    @Test
    fun `unreadable timezone falls back to UTC`() {
        assertEquals(ZoneOffset.UTC, "not a zone".toZone())
        assertEquals(ZoneOffset.UTC, "".toZone())
    }

    private fun basic(s: String) = "Basic " + Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun `basic auth`() {
        assertTrue(authorized(basic("me:secret"), "me", "secret"))
        assertFalse(authorized(basic("me:wrong"), "me", "secret"))
        assertFalse(authorized(basic("you:secret"), "me", "secret"))
        assertFalse(authorized(null, "me", "secret"))
        assertFalse(authorized("Basic !!!", "me", "secret"))
        assertFalse(authorized("Bearer secret", "me", "secret"))
        assertTrue(authorized(null, null, null), "no password configured leaves it open")
    }

    @Test
    fun `location request decoding`() {
        val json = Json { ignoreUnknownKeys = true }
        val body = """
            {"latitude":13.7,"longitude":100.5,"timezone":"Asia/Bangkok","country":"TH",
             "timestamp":1760000000,"alt":5,"batt":80,"acc":10,"vac":3,"conn":"w",
             "locality":"Bangkok","ghash":"w4rq","p":101.3,"addr":"x","unknown":"ignored"}
        """.trimIndent()
        val r = json.decodeFromString<LocationRequest>(body)
        assertEquals(Instant.ofEpochSecond(1760000000), r.timestamp)
        assertEquals(0, r.vel)
        assertEquals(null, r.bs)
    }
}
