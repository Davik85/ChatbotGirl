package app.llm

import app.AppConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

private const val MAX_ATTEMPTS = 3
private const val INITIAL_BACKOFF_MS = 400L

/**
 * Minimal OpenAI Chat Completions client.
 * We keep a tiny DTO set to reduce deps and stay explicit.
 */
class OpenAIClient(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini"
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(java.time.Duration.ofSeconds(30))
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .readTimeout(java.time.Duration.ofSeconds(30))
        .writeTimeout(java.time.Duration.ofSeconds(30))
        .build()

    private val mapper = jacksonObjectMapper()
    private val json = "application/json; charset=utf-8".toMediaType()

    // DTOs (keep small and explicit)
    data class ChatMessage(val role: String, val content: String)
    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.6,
        // IMPORTANT: OpenAI expects snake_case
        val max_tokens: Int? = 200
    )
    data class ChatChoice(val index: Int, val message: ChatMessage)
    data class ChatResponse(val choices: List<ChatChoice>)

    fun complete(messages: List<ChatMessage>): String {
        var backoff = INITIAL_BACKOFF_MS

        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val request = ChatRequest(
                    model = model,
                    messages = messages,
                    temperature = 0.6
                )
                val requestBody = mapper
                    .writeValueAsString(request)
                    .toRequestBody(json)

                val req = Request.Builder()
                    .url(AppConfig.OPENAI_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("OpenAI HTTP ${resp.code} ${resp.message}")
                    val body = resp.body?.string() ?: error("Empty response body")
                    val parsed: ChatResponse = mapper.readValue(body)
                    val text = parsed.choices.firstOrNull()?.message?.content.orEmpty()
                    return text.take(AppConfig.MAX_REPLY_CHARS)
                }
            } catch (e: Exception) {
                val isLast = attempt == MAX_ATTEMPTS - 1
                if (isLast) throw e
                Thread.sleep(backoff)
                backoff *= 2
            }
        }

        error("Unreachable: no response after $MAX_ATTEMPTS attempts")
    }
}
