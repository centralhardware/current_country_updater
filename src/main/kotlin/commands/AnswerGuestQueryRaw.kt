package commands

import dev.inmo.tgbotapi.requests.abstracts.SimpleRequest
import dev.inmo.tgbotapi.requests.answers.AnswerGuestQuery
import dev.inmo.tgbotapi.types.GuestQueryId
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.abstracts.InlineQueryResult
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement

/**
 * answerGuestQuery that accepts whatever Telegram returns. The typed request
 * failed to read the reply even though the message had been sent, and the
 * failure was retried — posting the same location again every second.
 */
class AnswerGuestQueryRaw(
    guestQueryId: GuestQueryId,
    result: InlineQueryResult,
) : SimpleRequest<JsonElement> {
    private val request = AnswerGuestQuery(guestQueryId, result)

    override fun method(): String = request.method()
    override val requestSerializer: SerializationStrategy<*> get() = request.requestSerializer
    override val resultDeserializer: DeserializationStrategy<JsonElement> get() = JsonElement.serializer()
}
