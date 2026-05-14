import ChannelManager.registerHashtagHandler
import ChannelManager.updateChannelTitle
import commands.registerMapCommand
import commands.registerStatCommand
import commands.registerTagCommands
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
                updateChannelTitle(Config.CHANNEL_ID, Config.CHANEL_TITLE_PATTERN)
            }
        }

        setMyCommands(
            BotCommand("stat", "show statistics"),
            BotCommand("map", "show last location on map"),
            BotCommand("addtag", "add temporary tag"),
            BotCommand("removetag", "remove temporary tag"),
            BotCommand("tags", "list active tags"),
            BotCommand("cleartags", "remove all tags")
        )

        registerHashtagHandler(Config.CHANNEL_ID)
        registerStatCommand()
        registerMapCommand()
        registerTagCommands()
    }.second.join()

}
