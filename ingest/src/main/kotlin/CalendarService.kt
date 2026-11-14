import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Method
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.XProperty

object CalendarService {

    fun buildCalendar(): String {
        val sessions = DatabaseService.getCountrySessions()

        val calendar = Calendar()
            .withProdId("-//Current Country Updater//EN")
            .withDefaults()
            .withProperty(XProperty("X-WR-CALNAME", "Country Visits"))
            .withProperty(Method(Method.VALUE_PUBLISH))
            .getFluentTarget()

        for (session in sessions) {
            val emoji = runCatching { countryCodeToEmoji(session.country) }.getOrDefault("")
            val summary = "$emoji ${session.country}".trim()
            val endExclusive = session.endDay.plusDays(1)

            val event = VEvent(session.startDay, endExclusive, summary)
                .add<VEvent>(Color().apply { setValue(countryColor(session.country)) })
                .add<VEvent>(Uid("${session.startDay}-${session.country.replace(" ", "-").lowercase()}@country-tracker"))

            calendar.add<Calendar>(event)
        }

        return calendar.toString()
    }

    private fun countryColor(country: String): String {
        val hue = (country.hashCode().and(Int.MAX_VALUE)) % 360
        return "hsl($hue, 70%, 50%)"
    }
}
