import kotliquery.queryOf
import kotliquery.sessionOf

/** Write side of the overrides; the web server reads them via [LocalityOverrides]. */
object LocalityOverrideManager {

    fun addOverride(from: String, to: String) {
        sessionOf(PostgresService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO locality_overrides (from_locality, to_locality)
                    VALUES (?, ?)
                    ON CONFLICT (from_locality) DO UPDATE SET to_locality = EXCLUDED.to_locality
                    """.trimIndent(),
                    normalize(from),
                    to
                )
            )
        }
    }

    fun removeOverride(from: String): Boolean {
        val removed = sessionOf(PostgresService.dataSource).use { session ->
            session.update(
                queryOf("DELETE FROM locality_overrides WHERE from_locality = ?", normalize(from))
            ) > 0
        }
        return removed
    }

    fun getOverrides(): List<Pair<String, String>> = LocalityOverrides.all()

    private fun normalize(locality: String) = LocalityOverrides.normalize(locality)
}
