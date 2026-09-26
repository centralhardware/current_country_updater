package commands

import dev.inmo.tgbotapi.requests.abstracts.SimpleRequest
import dev.inmo.tgbotapi.requests.answers.AnswerGuestQuery
import dev.inmo.tgbotapi.types.GuestQueryId
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.abstracts.InlineQueryResult
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Encoder
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

    private object Serializer : SerializationStrategy<AnswerGuestQueryRaw> {
        override val descriptor: SerialDescriptor = AnswerGuestQuery.serializer().descriptor
        override fun serialize(encoder: Encoder, value: AnswerGuestQueryRaw) =
            encoder.encodeSerializableValue(AnswerGuestQuery.serializer(), value.request)
    }

    override fun method(): String = request.method()
    // The executor serializes the request object itself, so encode the wrapped one in its place.
    override val requestSerializer: SerializationStrategy<*> get() = Serializer
    override val resultDeserializer: DeserializationStrategy<JsonElement> get() = JsonElement.serializer()
}
