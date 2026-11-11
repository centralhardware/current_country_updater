import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.Instant
import javax.sql.DataSource

object DatabaseService {

    private val dataSource: DataSource get() = ClickHouse.dataSource

    fun getCountryStats(): List<Pair<String, Int>> {
        return sessionOf(dataSource).run(
            queryOf(
                // language=SQL
                """
                    SELECT country, COUNT(*) AS count_of_days
                    FROM (
                        SELECT DISTINCT country, toDate(date_time)
                        FROM country_days_tracker_bot.country_days_tracker
                        WHERE country != ''
                    )
                    GROUP BY country
                    ORDER BY COUNT(*) DESC
                """.trimIndent()
            ).map { row ->
                Pair(row.string("country"), row.int("count_of_days"))
            }.asList
        )
    }

    data class Location(
        val latitude: Float,
        val longitude: Float,
        val country: String,
        val locality: String,
        val ts: Instant
    )

    fun getLastLocation(): Location? {
        return sessionOf(dataSource).run(
            queryOf(
                // language=SQL
                """
                    SELECT latitude, longitude, country, locality, toUnixTimestamp(ts) AS ts_epoch
                    FROM country_days_tracker_bot.country_days_tracker
                    -- ts, not date_time: local wall time runs backwards on a
                    -- westward flight, and ts is the table's sorting key.
                    ORDER BY ts DESC
                    LIMIT 1
                """.trimIndent()
            ).map { row ->
                Location(
                    row.float("latitude"),
                    row.float("longitude"),
                    row.string("country"),
                    row.string("locality"),
                    Instant.ofEpochSecond(row.long("ts_epoch"))
                )
            }.asSingle
        )
    }

    data class CountrySession(
        val country: String,
        val startDay: java.time.LocalDate,
        val endDay: java.time.LocalDate
    )

    fun getCountrySessions(): List<CountrySession> {
        return sessionOf(dataSource).run(
            queryOf(
                // language=SQL
                """
                    WITH
                        data AS (
                            SELECT DISTINCT
                                toDate(date_time) AS day,
                                country
                            FROM country_days_tracker_bot.country_days_tracker
                            WHERE country != ''
                        ),
                        marked AS (
                            SELECT
                                country,
                                day,
                                lagInFrame(day) OVER (
                                    PARTITION BY country ORDER BY day
                                    ROWS BETWEEN 1 PRECEDING AND CURRENT ROW
                                ) AS prev_same
                            FROM data
                        ),
                        with_sessions AS (
                            SELECT
                                country,
                                day,
                                -- A calendar event covers only strictly-logged days, so
                                -- its span equals the number of logged days — matching the
                                -- /stat count (getCountryStats). Any day without a log for
                                -- the country breaks the run: a new session starts unless
                                -- the previous logged day is exactly the day before.
                                sum(
                                    if(dateDiff('day', prev_same, day) = 1, 0, 1)
                                ) OVER (
                                    PARTITION BY country ORDER BY day
                                    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                                ) AS session_id
                            FROM marked
                        )
                    SELECT
                        country,
                        toString(MIN(day)) AS start_day,
                        toString(MAX(day)) AS end_day
                    FROM with_sessions
                    GROUP BY country, session_id
                    ORDER BY start_day
                """.trimIndent()
            ).map { row ->
                CountrySession(
                    row.string("country"),
                    java.time.LocalDate.parse(row.string("start_day")),
                    java.time.LocalDate.parse(row.string("end_day"))
                )
            }.asList
        )
    }

    fun getCurrentCountryLength(): Pair<String, Int> {
        return sessionOf(dataSource).run(
            queryOf(
                // language=SQL
                """
                    WITH
                        data AS (
                            SELECT
                                toDate(date_time) AS day,
                                country,
                                max(ts) AS last_ts
                            FROM country_days_tracker_bot.country_days_tracker
                            WHERE country != ''
                            GROUP BY day, country
                        ),
                        all_days AS (
                            SELECT
                                day,
                                lagInFrame(day) OVER (
                                    ORDER BY day ROWS BETWEEN 1 PRECEDING AND CURRENT ROW
                                ) AS prev_any
                            FROM (SELECT DISTINCT day FROM data)
                        ),
                        marked AS (
                            SELECT
                                d.country AS country,
                                d.day AS day,
                                d.last_ts AS last_ts,
                                lagInFrame(d.day) OVER (
                                    PARTITION BY d.country ORDER BY d.day
                                    ROWS BETWEEN 1 PRECEDING AND CURRENT ROW
                                ) AS prev_same,
                                a.prev_any AS prev_any
                            FROM data AS d
                            INNER JOIN all_days AS a ON d.day = a.day
                        ),
                        with_sessions AS (
                            SELECT
                                country,
                                day,
                                last_ts,
                                -- Deliberately looser than getCountrySessions, which breaks a
                                -- calendar event on any day without a log: here a new session
                                -- starts only on a real departure (another country logged in
                                -- the gap, or a > 30-day gap with nothing logged), so the
                                -- "current" stay isn't cut short by a few missing days.
                                sum(
                                    if(
                                        prev_same = toDate(0)
                                        OR prev_any > prev_same
                                        OR dateDiff('day', prev_same, day) > 30,
                                        1, 0
                                    )
                                ) OVER (
                                    PARTITION BY country ORDER BY day
                                    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                                ) AS session_id
                            FROM marked
                        ),
                        sessions_grouped AS (
                            SELECT
                                country,
                                MIN(day) AS start_day,
                                MAX(day) AS end_day,
                                COUNT() AS days_in_country,
                                MAX(last_ts) AS last_ts
                            FROM with_sessions
                            GROUP BY country, session_id
                        )
                    SELECT
                        country,
                        days_in_country
                    FROM (
                        SELECT
                            *,
                            -- On a border-crossing day both sessions end on the
                            -- same day; the one pinged last is the current one.
                            row_number() OVER (ORDER BY end_day DESC, last_ts DESC) AS rn
                        FROM sessions_grouped
                    )
                    WHERE rn = 1
                """.trimIndent()
            ).map { row ->
                Pair(row.string("country"), row.int("days_in_country"))
            }.asSingle
        ) ?: throw IllegalStateException("No country data found in database")
    }

}
