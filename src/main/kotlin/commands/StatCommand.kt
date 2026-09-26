package commands

import DatabaseService
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onGuestRequestMessage
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.InlineQueryResultArticle
import dev.inmo.tgbotapi.types.InlineQueries.InputMessageContent.InputTextMessageContent
import dev.inmo.tgbotapi.types.InlineQueryId
import dev.inmo.tgbotapi.types.message.abstracts.FromUserMessage
import formatCountryStats

// The owner may run /stat anywhere, directly or via guest mode.
private const val OWNER_ID = 428985392L

// This user may run it only through guest mode in their private chat with the owner.
private const val GUEST_ID = 522104797L

private fun buildStats(): String {
    val stats = DatabaseService.getCountryStats()
    KSLog.info(stats)
    return formatCountryStats(stats, DatabaseService.getCurrentCountryLength())
}

fun BehaviourContext.registerStatCommand() {
    onCommand("stat", initialFilter = { (it as? FromUserMessage)?.from?.id?.chatId?.long == OWNER_ID }) { message ->
        reply(message, buildStats())
    }

    onGuestRequestMessage(initialFilter = { message ->
        val from = message.from.id.chatId.long
        val allowed = from == OWNER_ID ||
            (from == GUEST_ID && message.chat.id.chatId.long == OWNER_ID)
        allowed && message.text?.contains("/stat") == true
    }) { message ->
        reply(
            message,
            InlineQueryResultArticle(
                id = InlineQueryId(message.guestQueryId.string),
                title = "Country stats",
                inputMessageContent = InputTextMessageContent(buildStats()),
            ),
        )
    }
}
