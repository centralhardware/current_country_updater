import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.Instant

object TagManager {

    /** Saves the tag and returns its normalised form, or null when nothing is left of [name]. */
    fun addTag(name: String, expiresAt: Instant? = null): String? {
        val normalized = normalizeTag(name) ?: return null
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
        return normalized
    }

    fun removeTag(name: String): Boolean {
        return sessionOf(PostgresService.dataSource).use { session ->
            session.update(
                queryOf("DELETE FROM tags WHERE name = ?", normalizeTag(name) ?: return false)
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

    private fun normalizeTag(name: String): String? {
        val tag = sanitizeForHashtag(name.removePrefix("#"))
        return if (tag.isEmpty()) null else "#$tag"
    }
}
