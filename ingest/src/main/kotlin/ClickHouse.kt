import com.clickhouse.jdbc.DataSourceImpl
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import java.sql.SQLException
import java.time.Instant
import java.util.Properties
import javax.sql.DataSource

/** The ClickHouse location table: connection, migrations, and the one write. */
object ClickHouse {

    val dataSource: DataSource = initializeDataSource()

    private fun initializeDataSource(): DataSource {
        return try {
            val props = Properties().apply {
                IngestConfig.CLICKHOUSE_USER?.let { put("user", it) }
                IngestConfig.CLICKHOUSE_PASSWORD?.let { put("password", it) }
            }
            val ds = DataSourceImpl(IngestConfig.CLICKHOUSE_URL, props)

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
    fun insert(
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
                        SELECT
                            toDateTime(toInt64(?), 'UTC') AS ts,
                            toInt32(?) AS tz_offset,
                            toFloat32(?) AS latitude,
                            toFloat32(?) AS longitude,
                            toString(?) AS country,
                            toString(?) AS tzname,
                            toUInt16(?) AS alt,
                            toUInt8(?) AS batt,
                            toUInt16(?) AS acc,
                            toUInt16(?) AS vac,
                            toString(?) AS conn,
                            toString(?) AS locality,
                            toString(?) AS ghash,
                            toFloat64(?) AS p,
                            toString(?) AS addr,
                            '' AS bssid,
                            '' AS ssid,
                            toUInt16(?) AS vel,
                            toUInt16(?) AS cog,
                            toInt8(?) AS m,
                            toUInt8(?) AS bs
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

}
