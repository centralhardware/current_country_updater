import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.Instant

object TagManager {

    fun addTag(name: String, expiresAt: Instant? = null) {
        val normalized = normalizeTag(name)
        sessionOf(PostgresService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO tags (name, expires_at)
                    VALUES (?, ?)
                    ON CONFLICT (name) DO UPDATE SET expires_at = EXCLUDED.expires_at
                    """.trimIndent(),
                    normalized,
                    expiresAt
                )
            )   
        }
    }

    fun removeTag(name: String): Boolean {
        return sessionOf(PostgresService.dataSource).use { session ->
            session.update(
                queryOf("DELETE FROM tags WHERE name = ?", normalizeTag(name))
            ) > 0
        }
    }

    fun getActiveTags(): List<String> {
        return sessionOf(PostgresService.dataSource).use { session ->
            session.execute(
                queryOf("DELETE FROM tags WHERE expires_at IS NOT NULL AND expires_at <= now()")
            )
            session.run(
                queryOf("SELECT name FROM tags ORDER BY name")
                    .map { it.string("name") }
                    .asList
            )
        }
    }

    fun clear() {
        sessionOf(PostgresService.dataSource).use { session ->
            session.execute(queryOf("DELETE FROM tags"))
        }
    }

    fun sanitizeForHashtag(value: String): String {
        return value.replace(" ", "_").replace(Regex("[^\\p{L}\\p{N}_]"), "")
    }

    private fun normalizeTag(name: String): String {
        val tag = sanitizeForHashtag(name.removePrefix("#"))
        return "#$tag"
    }
}
