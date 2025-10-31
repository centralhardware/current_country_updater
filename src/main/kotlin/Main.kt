import ChannelManager.registerHashtagHandler
import ChannelManager.updateChannelTitle
import commands.registerStatCommand
import commands.registerSubscribeCommand
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
            WebService.locationUpdates.collect { update ->
                LocationSubscriptionManager.cleanupExpiredSubscriptions()
                with(LocationSubscriptionManager) {
                    updateAllSubscribers(update.latitude, update.longitude)
                }
            }
        }

        launch {
            doInfinity(Config.CHANNEL_UPDATE_CRON) {
                updateChannelTitle(Config.CHANNEL_ID, Config.CHANEL_TITLE_PATTERN)
            }
        }

        // Set bot commands
        setMyCommands(
            BotCommand("stat", "show statistics"),
            BotCommand("subscribe", "subscribe to location updates")
        )

        registerHashtagHandler(Config.CHANNEL_ID)
        registerStatCommand()
        registerSubscribeCommand()
    }.second.join()

}
