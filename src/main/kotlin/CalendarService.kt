import java.time.format.DateTimeFormatter

object CalendarService {

    private val icalDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun buildCalendar(): String {
        val sessions = DatabaseService.getCountrySessions()
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//Current Country Updater//EN")
            appendLine("X-WR-CALNAME:Country Visits")
            appendLine("METHOD:PUBLISH")
            for (session in sessions) {
                val emoji = runCatching { countryCodeToEmoji(session.country) }.getOrDefault("")
                val summary = "$emoji ${session.country}".trim()
                // iCal DTEND for all-day events is exclusive, so add 1 day
                val endExclusive = session.endDay.plusDays(1)
                appendLine("BEGIN:VEVENT")
                appendLine("DTSTART;VALUE=DATE:${session.startDay.format(icalDateFormat)}")
                appendLine("DTEND;VALUE=DATE:${endExclusive.format(icalDateFormat)}")
                appendLine("SUMMARY:$summary")
                appendLine("COLOR:${countryColor(session.country)}")
                appendLine("UID:${session.startDay}-${session.country.replace(" ", "-").lowercase()}@country-tracker")
                appendLine("END:VEVENT")
            }
            appendLine("END:VCALENDAR")
        }
    }

    private fun countryColor(country: String): String {
        val hue = (country.hashCode().and(Int.MAX_VALUE)) % 360
        return "hsl($hue, 70%, 50%)"
    }
}
