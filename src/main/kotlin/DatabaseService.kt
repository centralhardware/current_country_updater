import com.clickhouse.jdbc.DataSourceImpl
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import java.sql.SQLException
import java.time.Instant
import java.util.Properties
import javax.sql.DataSource

object DatabaseService {

    private val dataSource: DataSource = initializeDataSource()

    private fun initializeDataSource(): DataSource {
        return try {
            val props = Properties().apply {
                Config.CLICKHOUSE_USER?.let { put("user", it) }
                Config.CLICKHOUSE_PASSWORD?.let { put("password", it) }
                // Batching is the server's job. A ping is one row, and one row
                // per INSERT is one part per ping -- a few thousand parts a day
                // for a table that needs a handful. With async_insert the server
                // collects the pings in its own buffer and writes one part per
                // flush instead.
                //
                // wait_for_async_insert keeps the call synchronous: it returns
                // once the row is really in the table, so a failed insert is
                // still an exception here rather than a ping lost in a buffer
                // nobody is watching, and getLastLocation right after a save
                // sees the ping it just wrote.
                put("custom_settings", "async_insert=1,wait_for_async_insert=1")
            }
            val ds = DataSourceImpl(Config.CLICKHOUSE_URL, props)

            val flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration", "filesystem:/app/resources/db/migration")
                .baselineOnMigrate(true)
                .load()

            // ClickHouse has no transactional DDL, so a migration that fails
            // half way leaves a `success = 0` row in flyway_schema_history that
            // blocks every later deploy until someone deletes it by hand -- with
            // the bot refusing to start, and dropping pings, the whole time.
            // repair() clears those rows. It also realigns the checksums of
            // already-applied migrations, so an edit to a migration that has
            // run will be adopted silently rather than reported: don't edit
            // them, add a new one.
            flyway.repair()
            flyway.migrate()

            ds
        } catch (e: SQLException) {
            throw RuntimeException("Failed to initialize database", e)
        }
    }

    /**
     * The table stores the instant and the offset it was recorded at; local
     * wall time is the ALIAS column `date_time` = `ts + tz_offset`, which is
     * what the country statistics below still count the days of. Both values
     * come straight from the OwnTracks payload, so neither is reconstructed.
     *
     * `tzOffset` is seconds east of UTC *at that instant*, not the zone's
     * standard offset -- a zone on summer time is a different offset under the
     * same name, and a row has to carry the one that was actually in force.
     */
    fun save(
        ts: Instant,
        tzOffset: Int,
        latitude: Float,
        longitude: Float,
        tzname: String,
        country: String,
        alt: Int,
        batt: Int,
        acc: Int,
        vac: Int,
        conn: String,
        locality: String,
        ghash: String,
        p: Double,
        addr: String,
        vel: Int,
        cog: Int,
        m: Int,
        bs: Int
    ) {
        sessionOf(dataSource).use { session ->
            // VALUES rather than SELECT on purpose: ClickHouse only batches
            // inserts that carry their data in the request, and ignores
            // async_insert for an INSERT ... SELECT.
            session.execute(
                queryOf(
                    // language=SQL
                    """
                        INSERT INTO country_days_tracker_bot.country_days_tracker (
                            ts,
                            tz_offset,
                            latitude,
                            longitude,
                            country,
                            tzname,
                            alt,
                            batt,
                            acc,
                            vac,
                            conn,
                            locality,
                            ghash,
                            p,
                            addr,
                            bssid,
                            ssid,
                            vel,
                            cog,
                            m,
                            bs
                        )
                        VALUES (
                            toDateTime(toInt64(?), 'UTC'),
                            toInt32(?),
                            toFloat32(?),
                            toFloat32(?),
                            toString(?),
                            toString(?),
                            toUInt16(?),
                            toUInt8(?),
                            toUInt8(?),
                            toUInt8(?),
                            toString(?),
                            toString(?),
                            toString(?),
                            toFloat64(?),
                            toString(?),
                            '',
                            '',
                            toUInt16(?),
                            toUInt16(?),
                            toInt8(?),
                            toUInt8(?)
                        )
                    """.trimIndent(),
                    ts.epochSecond,
                    tzOffset,
                    latitude,
                    longitude,
                    country,
                    tzname,
                    alt,
                    batt,
                    acc,
                    vac,
                    conn,
                    locality,
                    ghash,
                    p,
                    addr,
                    vel,
                    cog,
                    m,
                    bs
                )
            )
        }
    }

    fun getCountryStats(): List<Pair<String, Int>> {
        return sessionOf(dataSource).run(
            queryOf(
                // language=SQL
                """
                    SELECT country, COUNT(*) AS count_of_days
                    FROM (
                        SELECT DISTINCT LOWER(country) AS country, toStartOfDay(date_time)
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
        val locality: String
    )

    fun getLastLocation(): Location? {
        return sessionOf(dataSource).run(
            queryOf(
                // language=SQL
                """
                    SELECT latitude, longitude, country, locality
                    FROM country_days_tracker_bot.country_days_tracker
                    ORDER BY date_time DESC
                    LIMIT 1
                """.trimIndent()
            ).map { row ->
                Location(
                    row.float("latitude"),
                    row.float("longitude"),
                    row.string("country"),
                    row.string("locality")
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
                                country
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
                                -- Same session rule as getCountrySessions: a new session
                                -- starts only on a real departure (another country logged
                                -- in the gap, or a > 30-day gap with nothing logged), not
                                -- on a 1-2 day logging gap. So the "current" session is the
                                -- latest continuous stay, not fragmented by missing days.
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
                                COUNT() AS days_in_country
                            FROM with_sessions
                            GROUP BY country, session_id
                        )
                    SELECT
                        country,
                        days_in_country
                    FROM (
                        SELECT
                            *,
                            row_number() OVER (ORDER BY end_day DESC) AS rn
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
