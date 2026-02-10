import ChannelManager.registerHashtagHandler
import ChannelManager.updateChannelTitle
import commands.registerMapCommand
import commands.registerStatCommand
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
    AppConfig.init("current_country_updater")
    WebService.start(80)

    longPolling({ restrictAccess(EnvironmentVariableUserAccessChecker()) }) {
        launch {
            doInfinity(Config.CHANNEL_UPDATE_CRON) {
                updateChannelTitle(Config.CHANNEL_ID, Config.CHANEL_TITLE_PATTERN)
            }
        }

        // Set bot commands
        setMyCommands(
            BotCommand("stat", "show statistics"),
            BotCommand("map", "show last location on map")
        )

        registerHashtagHandler(Config.CHANNEL_ID)
        registerStatCommand()
        registerMapCommand()
    }.second.join()

}
