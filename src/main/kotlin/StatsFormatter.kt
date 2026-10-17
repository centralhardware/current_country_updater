import java.util.concurrent.atomic.AtomicInteger

fun formatCountryStats(stats: List<Pair<String, Int>>, currentCountry: Pair<String, Int>): String {
    return buildString {
        append(formatStatsList(stats))
        append("\n\n")
        append("${calculateVisitedPercentage(stats.size)}\n")
        append("Current country: ${currentCountry.first} ${currentCountry.second}")
    }
}

private fun formatStatsList(stats: List<Pair<String, Int>>): String {
    val counter = AtomicInteger(1)
    return stats.joinToString("\n") { (name, days) ->
        val prettyDuration = formatDuration(days)
        val period = if (prettyDuration.isEmpty()) "" else " ($prettyDuration)"
        "${counter.getAndIncrement()} - $name - $days$period"
    }
}

// The stats count every distinct place the geocoder names, territories included
// (Hong Kong, Macau...), so this is a share of the UN-member total, not an exact one.
private fun calculateVisitedPercentage(visitedCountries: Int): String {
    val percent = (visitedCountries.toDouble() / Config.TOTAL_COUNTRIES) * 100
    return "Visited $visitedCountries countries and territories (%.2f%% of ${Config.TOTAL_COUNTRIES} UN states)".format(percent)
}

private fun plural(n: Int, unit: String) = if (n == 1) "$n $unit" else "$n ${unit}s"

internal fun formatDuration(totalDays: Int): String {
    val years = (totalDays / 365.25).toInt()
    val remainingDaysAfterYears = (totalDays % 365.25).toInt()

    val months = (remainingDaysAfterYears / 30.44).toInt()
    val remainingDaysAfterMonths = (remainingDaysAfterYears % 30.44).toInt()

    val weeks = remainingDaysAfterMonths / 7
    val days = remainingDaysAfterMonths % 7

    val parts = mutableListOf<String>()

    if (years > 0) parts.add(plural(years, "year"))
    if (months > 0) parts.add(plural(months, "month"))
    if (weeks > 0) parts.add(plural(weeks, "week"))
    if (weeks > 0 || months > 0 || years > 0) {
        if (days > 0) parts.add(plural(days, "day"))
    }

    return parts.joinToString(", ")
}