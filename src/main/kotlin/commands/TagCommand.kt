package commands

import TagManager
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import java.time.Instant
import java.time.temporal.ChronoUnit

fun BehaviourContext.registerTagCommands() {
    onCommand("addtag") { message ->
        val args = message.content.text
            .removePrefix("/addtag")
            .trim()

        if (args.isEmpty()) {
            reply(message, "Usage: /addtag <tag> [duration]\nDuration examples: 1h, 2d, 30m")
            return@onCommand
        }

        val parts = args.split("\\s+".toRegex(), limit = 2)
        val tagName = parts[0]
        val expiresAt = if (parts.size > 1) parseDuration(parts[1]) else null

        TagManager.addTag(tagName, expiresAt)

        val expiryText = if (expiresAt != null) " (expires: $expiresAt)" else " (until removed or restart)"
        reply(message, "Tag ${TagManager.getActiveTags().last { it.contains(tagName.removePrefix("#").replace(" ", "_")) }} added$expiryText")
    }

    onCommand("removetag") { message ->
        val tagName = message.content.text
            .removePrefix("/removetag")
            .trim()

        if (tagName.isEmpty()) {
            reply(message, "Usage: /removetag <tag>")
            return@onCommand
        }

        if (TagManager.removeTag(tagName)) {
            reply(message, "Tag removed")
        } else {
            reply(message, "Tag not found")
        }
    }

    onCommand("tags") { message ->
        val tags = TagManager.getActiveTags()
        if (tags.isEmpty()) {
            reply(message, "No active tags")
        } else {
            reply(message, "Active tags:\n${tags.joinToString("\n")}")
        }
    }

    onCommand("cleartags") { message ->
        TagManager.clear()
        reply(message, "All tags cleared")
    }
}

private fun parseDuration(input: String): Instant? {
    val match = Regex("^(\\d+)([mhd])$").matchEntire(input.trim().lowercase()) ?: return null
    val amount = match.groupValues[1].toLong()
    val unit = when (match.groupValues[2]) {
        "m" -> ChronoUnit.MINUTES
        "h" -> ChronoUnit.HOURS
        "d" -> ChronoUnit.DAYS
        else -> return null
    }
    return Instant.now().plus(amount, unit)
}
