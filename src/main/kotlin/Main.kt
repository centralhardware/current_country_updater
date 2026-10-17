import ChannelManager.registerHashtagHandler
import ChannelManager.updateChannelTitle
import commands.registerMapCommand
import commands.registerOverrideCommands
import commands.registerStatCommand
import commands.registerTagCommands
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.error
import dev.inmo.krontab.doInfinity
import dev.inmo.micro_utils.common.Warning
import dev.inmo.tgbotapi.AppConfig
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.longPolling
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.utils.RiskFeature
import kotlinx.coroutines.launch

@OptIn(Warning::class, RiskFeature::class)
suspend fun main() {
    WebService.start(80)

    longPolling ("CurrentCountryUpdater") {
        launch {
            doInfinity("0 /1 * * *") {
                // One bad tick (network, an unknown country) must not end the schedule.
                runCatching { updateChannelTitle(Config.CHANNEL_ID, Config.CHANEL_TITLE_PATTERN) }
                    .onFailure { KSLog.error("Channel title update failed", it) }
            }
        }

        setMyCommands(
            BotCommand("stat", "show statistics"),
            BotCommand("map", "share live location"),
            BotCommand("addtag", "add temporary tag"),
            BotCommand("removetag", "remove temporary tag"),
            BotCommand("tags", "list active tags"),
            BotCommand("cleartags", "remove all tags"),
            BotCommand("addoverride", "add locality override (from -> to)"),
            BotCommand("removeoverride", "remove locality override"),
            BotCommand("overrides", "list locality overrides")
        )

        registerHashtagHandler(Config.CHANNEL_ID)
        registerStatCommand()
        registerMapCommand()
        registerTagCommands()
        registerOverrideCommands()
    }.second.join()

}
