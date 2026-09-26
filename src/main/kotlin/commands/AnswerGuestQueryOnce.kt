package commands

import dev.inmo.tgbotapi.AppConfig
import dev.inmo.tgbotapi.requests.answers.AnswerGuestQuery
import dev.inmo.tgbotapi.types.GuestQueryId
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.abstracts.InlineQueryResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val http = HttpClient(CIO)
private val json = Json {
    explicitNulls = false
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * Sends answerGuestQuery exactly once, outside the bot's request executor.
 * Telegram posts the guest message and still replies 429, and the executor
 * retries every 429 — each retry posted another copy of the location.
 * Returns the raw reply.
 */
suspend fun answerGuestQueryOnce(guestQueryId: GuestQueryId, result: InlineQueryResult): JsonObject {
    val body = json.encodeToString(AnswerGuestQuery.serializer(), AnswerGuestQuery(guestQueryId, result))
    val response = http.post("https://api.telegram.org/bot${AppConfig.botToken()}/answerGuestQuery") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    return json.parseToJsonElement(response.bodyAsText()).jsonObject
}
