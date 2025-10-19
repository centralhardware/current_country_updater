package commands

import DatabaseService
import StatsFormatter
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand

fun BehaviourContext.registerStatCommand() {
    onCommand("stat") { message ->
        val stats = DatabaseService.getCountryStats()
        val currentCountry = DatabaseService.getCurrentCountryLength()
        val msg = StatsFormatter.formatCountryStats(stats, currentCountry)

        KSLog.info(stats)
        reply(message, msg)
    }
}
