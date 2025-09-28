package app.llm

import app.AppConfig
import app.llm.dto.ChatMessage
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

class OpenAIClient(
    private val apiKey: String,
    private val model: String = "gpt-4.1",
    private val maxCompletionTokens: Int = 400,
    /** Для моделей, которые поддерживают. Для 4.1/omni/o4 не отправляем вовсе. */
    private val temperature: Double? = null
) {
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val json = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(60))
        .callTimeout(Duration.ofSeconds(90))
        .build()

    fun healthCheck(): Boolean {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .apply {
                AppConfig.openAiOrg?.let { addHeader("OpenAI-Organization", it) }
                AppConfig.openAiProject?.let { addHeader("OpenAI-Project", it) }
            }
            .get()
            .build()
        http.newCall(req).execute().use { resp -> return resp.isSuccessful }
    }

    fun complete(messages: List<ChatMessage>): String {
        val body = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages,
            "max_completion_tokens" to maxCompletionTokens
        )

        // temperature отправляем ТОЛЬКО если модель это умеет и значение задано
        if (supportsTemperature(model) && temperature != null) {
            body["temperature"] = temperature
        }

        val req = Request.Builder()
            .url(AppConfig.OPENAI_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .apply {
                AppConfig.openAiOrg?.let { addHeader("OpenAI-Organization", it) }
                AppConfig.openAiProject?.let { addHeader("OpenAI-Project", it) }
            }
            .post(mapper.writeValueAsString(body).toRequestBody(json))
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("OpenAI HTTP ${resp.code}  body=$raw")
            val parsed: ChatResponse = mapper.readValue(raw)
            return parsed.choices.firstOrNull()?.message?.content?.trim()
                .takeUnless { it.isNullOrBlank() }
                ?: app.AppConfig.FALLBACK_REPLY
        }
    }

    private fun supportsTemperature(model: String): Boolean {
        val m = model.lowercase()
        // семейства, где temperature сейчас НЕ поддерживается в /chat/completions
        val noTemp = m.startsWith("gpt-4.1") || m.startsWith("o4") || m.contains("omni")
        return !noTemp
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ChatResponse(val id: String? = null, val choices: List<ChatChoice> = emptyList())
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ChatChoice(val index: Int? = null, val message: ChatMessage? = null, val finish_reason: String? = null)
}
