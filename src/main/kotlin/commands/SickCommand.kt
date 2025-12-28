package commands

import DatabaseService
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import java.time.LocalDate
import java.time.ZonedDateTime

fun BehaviourContext.registerSickCommand() {
    onCommandWithArgs("sick") { message, args ->
        val severity = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 10) ?: 5

        val timezone = DatabaseService.getLastTimezone()
        val today = if (timezone != null) {
            ZonedDateTime.now(timezone).toLocalDate()
        } else {
            LocalDate.now()
        }

        DatabaseService.addSickDay(today, severity)
        reply(message, "🤒 Today marked as sick day (severity: $severity/10)")
        KSLog.info("Sick day added: $today, severity: $severity")
    }
}
