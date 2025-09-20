package app.llm

import app.AppConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Minimal OpenAI Chat Completions client.
 * We keep a tiny DTO set to reduce deps and stay explicit.
 */
class OpenAIClient(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini"
) {
    private val client: OkHttpClient = OkHttpClient()
    private val mapper = jacksonObjectMapper()
    private val json = "application/json; charset=utf-8".toMediaType()

    data class ChatMessage(val role: String, val content: String)
    data class ChatRequest(val model: String, val messages: List<ChatMessage>, val temperature: Double = 0.6)
    data class ChatChoice(val index: Int, val message: ChatMessage)
    data class ChatResponse(val choices: List<ChatChoice>)

    fun complete(messages: List<ChatMessage>): String {
        val requestBody = mapper.writeValueAsString(ChatRequest(model, messages))
            .toRequestBody(json)
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("OpenAI error: ${resp.code} ${resp.message}")
            val body = resp.body?.string() ?: error("Empty body")
            val parsed: ChatResponse = mapper.readValue(body)
            val text = parsed.choices.firstOrNull()?.message?.content ?: ""
            return text.take(AppConfig.MAX_REPLY_CHARS) // guardrail
        }
    }
}
