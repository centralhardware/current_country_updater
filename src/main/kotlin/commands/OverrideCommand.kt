package commands

import LocalityOverrideManager
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs

private const val SEPARATOR = "->"

fun BehaviourContext.registerOverrideCommands() {
    onCommandWithArgs("addoverride") { message, args ->
        val parts = args.joinToString(" ").split(SEPARATOR).map { it.trim() }
        if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            reply(
                message,
                "Usage: /addoverride <from> $SEPARATOR <to>\n" +
                    "Example: /addoverride Muang Chiang Mai $SEPARATOR Chiang Mai"
            )
            return@onCommandWithArgs
        }

        LocalityOverrideManager.addOverride(parts[0], parts[1])
        reply(message, "Override added: ${parts[0]} → ${parts[1]}")
    }

    onCommandWithArgs("removeoverride") { message, args ->
        val from = args.joinToString(" ").trim()
        if (from.isEmpty()) {
            reply(message, "Usage: /removeoverride <from>")
            return@onCommandWithArgs
        }

        if (LocalityOverrideManager.removeOverride(from)) {
            reply(message, "Override removed")
        } else {
            reply(message, "Override not found")
        }
    }

    onCommand("overrides") { message ->
        val overrides = LocalityOverrideManager.getOverrides()
        if (overrides.isEmpty()) {
            reply(message, "No locality overrides")
        } else {
            reply(
                message,
                "Locality overrides:\n" +
                    overrides.joinToString("\n") { (from, to) -> "$from → $to" }
            )
        }
    }
}
