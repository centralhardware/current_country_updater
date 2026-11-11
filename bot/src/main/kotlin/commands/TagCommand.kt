package commands

import TagManager
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import java.time.Instant
import java.time.temporal.ChronoUnit

fun BehaviourContext.registerTagCommands() {
    onCommandWithArgs("addtag", initialFilter = fromOwner) { message, args ->
        if (args.isEmpty()) {
            reply(message, "Usage: /addtag <tag> [duration]\nDuration examples: 1h, 2d, 30m")
            return@onCommandWithArgs
        }

        val tagName = args[0]
        val durationText = args.getOrNull(1)
        val expiresAt = durationText?.let { parseDuration(it) }
        if (durationText != null && expiresAt == null) {
            reply(message, "Unknown duration \"$durationText\". Use a positive number with m, h or d: 30m, 1h, 2d")
            return@onCommandWithArgs
        }

        val tag = TagManager.addTag(tagName, expiresAt)
        if (tag == null) {
            reply(message, "\"$tagName\" has no letters or digits to make a tag from")
            return@onCommandWithArgs
        }

        val expiryText = if (durationText != null) " (expires in $durationText)" else ""
        reply(message, "Tag $tag added$expiryText")
    }

    onCommandWithArgs("removetag", initialFilter = fromOwner) { message, args ->
        val tagName = args.firstOrNull()
        if (tagName == null) {
            reply(message, "Usage: /removetag <tag>")
            return@onCommandWithArgs
        }

        if (TagManager.removeTag(tagName)) {
            reply(message, "Tag removed")
        } else {
            reply(message, "Tag not found")
        }
    }

    onCommand("tags", initialFilter = fromOwner) { message ->
        val tags = TagManager.getActiveTags()
        if (tags.isEmpty()) {
            reply(message, "No active tags")
        } else {
            reply(message, "Active tags:\n${tags.joinToString("\n")}")
        }
    }

    onCommand("cleartags", initialFilter = fromOwner) { message ->
        TagManager.clear()
        reply(message, "All tags cleared")
    }
}

internal fun parseDuration(input: String): Instant? {
    val match = Regex("^(\\d+)([mhd])$").matchEntire(input.trim().lowercase()) ?: return null
    val amount = match.groupValues[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
    val unit = when (match.groupValues[2]) {
        "m" -> ChronoUnit.MINUTES
        "h" -> ChronoUnit.HOURS
        "d" -> ChronoUnit.DAYS
        else -> return null
    }
    return Instant.now().plus(amount, unit)
}
