package commands

import DatabaseService
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import java.time.LocalDate
import java.time.ZonedDateTime

fun BehaviourContext.registerSickCommand() {
    onCommand("sick") { message ->
        val args = message.content.text.split(" ").drop(1)
        val note = args.joinToString(" ")

        // Mark today as sick using user's timezone
        val timezone = DatabaseService.getLastTimezone()
        val today = if (timezone != null) {
            ZonedDateTime.now(timezone).toLocalDate()
        } else {
            LocalDate.now()
        }

        DatabaseService.addSickDay(today, note)
        val noteMsg = if (note.isNotEmpty()) " with note: $note" else ""
        reply(message, "🤒 Today marked as sick day$noteMsg")
        KSLog.info("Sick day added: $today, note: $note")
    }
}
