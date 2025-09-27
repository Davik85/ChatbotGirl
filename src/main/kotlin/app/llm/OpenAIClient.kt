package app.llm

import app.AppConfig
import app.common.Json
import app.llm.dto.ChatMessage
import app.llm.dto.ChatRequest
import app.llm.dto.ChatResponse
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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

    private val mapper = Json.mapper
    private val json = "application/json; charset=utf-8".toMediaType()

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

    fun healthCheck(): Boolean {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                println("OPENAI-HEALTH ERR ${resp.code} ${resp.message} body=$body")
                return false
            }
            println("OPENAI-HEALTH ok"); return true
        }
    }
}
