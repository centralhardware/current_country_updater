import kotliquery.queryOf
import kotliquery.sessionOf

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
                    from,
                    to
                )
            )
        }
    }

    fun removeOverride(from: String): Boolean {
        return sessionOf(PostgresService.dataSource).use { session ->
            session.update(
                queryOf("DELETE FROM locality_overrides WHERE from_locality = ?", from)
            ) > 0
        }
    }

    fun getOverrides(): List<Pair<String, String>> {
        return sessionOf(PostgresService.dataSource).use { session ->
            session.run(
                queryOf("SELECT from_locality, to_locality FROM locality_overrides ORDER BY from_locality")
                    .map { Pair(it.string("from_locality"), it.string("to_locality")) }
                    .asList
            )
        }
    }

    /**
     * Returns the override target if geocoding produced a [locality] that matches a
     * configured `from` value, otherwise returns [locality] unchanged.
     */
    fun resolve(locality: String): String {
        return sessionOf(PostgresService.dataSource).use { session ->
            session.run(
                queryOf("SELECT to_locality FROM locality_overrides WHERE from_locality = ?", locality)
                    .map { it.string("to_locality") }
                    .asSingle
            )
        } ?: locality
    }
}
