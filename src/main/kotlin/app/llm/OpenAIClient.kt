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

    data class ChatMessage(val role: String, val content: String)
    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.6,
        val max_tokens: Int? = 300
    )
    data class ChatChoice(val index: Int, val message: ChatMessage)
    data class ChatResponse(val choices: List<ChatChoice>)

    fun complete(messages: List<ChatMessage>): String {
        var backoff = INITIAL_BACKOFF_MS

        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val request = ChatRequest(model = model, messages = messages, temperature = 0.6)
                val body = mapper.writeValueAsString(request).toRequestBody(json)

                val req = Request.Builder()
                    .url(AppConfig.OPENAI_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("OpenAI HTTP ${resp.code} ${resp.message} body=$raw")
                    val parsed: ChatResponse = mapper.readValue(raw)
                    val text = parsed.choices.firstOrNull()?.message?.content.orEmpty()
                    return text.take(AppConfig.MAX_REPLY_CHARS)
                }
            } catch (e: Exception) {
                if (attempt == MAX_ATTEMPTS - 1) throw e
                Thread.sleep(backoff); backoff *= 2
            }
        }
        error("Unreachable: no response after $MAX_ATTEMPTS attempts")
    }
}
