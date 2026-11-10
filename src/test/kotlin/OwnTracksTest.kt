import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnTracksTest {

    @Test
    fun `geohash matches the reference encoder`() {
        assertEquals("u4pruyd", geohash(57.64911, 10.40744))
        assertEquals("ezs42", geohash(42.6, -5.6, 5))
    }

    @Test
    fun `opencage answer`() {
        val g = Geocoder.parseOpenCage(
            """{"results":[{"annotations":{"timezone":{"name":"Asia/Bangkok"}},
               "components":{"city":"Bangkok","country_code":"th"},"formatted":"Silom, Bangkok, Thailand"}]}"""
        )
        assertEquals(Geo("TH", "Silom, Bangkok, Thailand", "Bangkok", "Asia/Bangkok"), g)
        assertEquals(Geo("", "", "", ""), Geocoder.parseOpenCage("""{"results":[]}"""))
    }

    /** A real OwnTracks iOS location message: the contract the phone sends. */
    @Test
    fun `app location message to request`() {
        val msg = Json.parseToJsonElement(
            """{"_type":"location","lat":13.7563,"lon":100.5018,"tst":1762672409,"alt":12,"batt":81,
               "bs":1,"acc":9,"vac":4,"conn":"w","p":100.9,"vel":3,"cog":270,"m":1,"tid":"ab",
               "BSSID":"x","SSID":"y","created_at":1762672410,"topic":"owntracks/u/d"}"""
        ).jsonObject
        val ping = OwnTracks.toPing(msg)!!
        val r = ping.toRequest(Geo("TH", "addr", "Bangkok", "Asia/Bangkok"))
        assertEquals(Instant.ofEpochSecond(1762672409), r.timestamp)
        assertEquals("TH", r.country)
        assertEquals(81, r.batt)
        assertEquals(1, r.bs)
        assertEquals(270, r.cog)
        assertEquals(ping.ghash, r.ghash)
        assertEquals(7, r.ghash.length)
    }

    @Test
    fun `location missing coordinates is not accepted`() {
        assertNull(OwnTracks.toPing(Json.parseToJsonElement("""{"_type":"location","lat":1,"tst":1}""").jsonObject))
        assertNull(OwnTracks.toPing(Json.parseToJsonElement("""{"_type":"location","latitude":1,"longitude":2,"tst":1}""").jsonObject))
    }

    private fun v(js: String) = OwnTracks.violations(Json.parseToJsonElement(js).jsonObject)

    @Test
    fun `exact contract passes, any drift fails`() {
        assertEquals(emptyList(), v("""{"_type":"location","lat":1.5,"lon":2,"tst":1,"batt":80,"conn":"w","inregions":["home"],"_http":true}"""))
        assertEquals(listOf("unknown key foo"), v("""{"_type":"location","lat":1,"lon":2,"tst":1,"foo":1}"""))
        assertEquals(listOf("missing lon"), v("""{"_type":"location","lat":1,"tst":1}"""))
        assertTrue(v("""{"_type":"location","lat":"1","lon":2,"tst":1}""").single().startsWith("lat is not num"))
        assertTrue(v("""{"_type":"location","lat":1,"lon":2,"tst":1,"conn":5}""").single().startsWith("conn is not str"))
    }
}
