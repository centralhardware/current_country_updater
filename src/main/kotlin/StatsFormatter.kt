import java.util.concurrent.atomic.AtomicInteger

object StatsFormatter {

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
            val period = if (prettyDuration.isEmpty()) "" else "($prettyDuration)"
            "${counter.getAndIncrement()} - $name - $days$period"
        }
    }

    private fun calculateVisitedPercentage(visitedCountries: Int): String {
        val percent = (visitedCountries.toDouble() / Config.TOTAL_COUNTRIES) * 100
        return "You have visited %.2f%% of the world".format(percent)
    }

    private fun formatDuration(totalDays: Int): String {
        val years = (totalDays / 365.25).toInt()
        val remainingDaysAfterYears = (totalDays % 365.25).toInt()

        val months = (remainingDaysAfterYears / 30.44).toInt()
        val remainingDaysAfterMonths = (remainingDaysAfterYears % 30.44).toInt()

        val weeks = remainingDaysAfterMonths / 7
        val days = remainingDaysAfterMonths % 7

        val parts = mutableListOf<String>()

        if (years > 0) parts.add("$years years")
        if (months > 0) parts.add("$months months")
        if (weeks > 0) parts.add("$weeks weeks")
        if (weeks > 0 || months > 0 || years > 0) {
            if (days > 0) parts.add("$days days")
        }

        return parts.joinToString(", ")
    }
}
