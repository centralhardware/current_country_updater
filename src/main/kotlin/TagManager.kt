import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object TagManager {

    data class Tag(
        val name: String,
        val expiresAt: Instant? = null
    ) {
        val isExpired: Boolean get() = expiresAt != null && Instant.now().isAfter(expiresAt)
    }

    private val tags = ConcurrentHashMap<String, Tag>()

    fun addTag(name: String, expiresAt: Instant? = null) {
        val normalized = normalizeTag(name)
        tags[normalized] = Tag(normalized, expiresAt)
    }

    fun removeTag(name: String): Boolean {
        return tags.remove(normalizeTag(name)) != null
    }

    fun getActiveTags(): List<String> {
        tags.entries.removeIf { it.value.isExpired }
        return tags.keys().toList().sorted()
    }

    fun clear() {
        tags.clear()
    }

    private fun normalizeTag(name: String): String {
        val tag = name.removePrefix("#").replace(" ", "_")
        return "#$tag"
    }
}
