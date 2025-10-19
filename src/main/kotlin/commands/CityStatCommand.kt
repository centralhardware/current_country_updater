package commands

import DatabaseService
import StatsFormatter
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand

fun BehaviourContext.registerCityStatCommand() {
    onCommand("citystat") { message ->
        val stats = DatabaseService.getCityStats()
        val msg = StatsFormatter.formatCityStats(stats)

        KSLog.info(stats)
        reply(message, msg)
    }
}
