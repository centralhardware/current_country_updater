package commands

import Config
import dev.inmo.tgbotapi.extensions.behaviour_builder.utils.SimpleFilter
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.abstracts.FromUserMessage
import dev.inmo.tgbotapi.types.message.content.TextContent

// The bot exposes the owner's location history, so every command answers the owner only.
internal val fromOwner: SimpleFilter<ContentMessage<TextContent>> = SimpleFilter {
    (it as? FromUserMessage)?.from?.id?.chatId?.long == Config.MAP_OWNER_ID
}
