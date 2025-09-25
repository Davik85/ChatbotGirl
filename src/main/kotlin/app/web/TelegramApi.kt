package app.web

import app.AppConfig
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

private const val TG_LIMIT = 4096

@JsonIgnoreProperties(ignoreUnknown = true)
data class TgApiUserMe(
    val id: Long,
    val is_bot: Boolean,
    val first_name: String? = null,
    val username: String? = null
)

data class TgApiErrorParams(val retry_after: Int? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TgApiResp<T>(
    val ok: Boolean,
    val result: T? = null,
    val error_code: Int? = null,
    val description: String? = null,
    val parameters: TgApiErrorParams? = null
)

data class TgSendMessage(
    val chat_id: Long,
    val text: String,
    val parse_mode: String? = null,
    val disable_web_page_preview: Boolean? = null,
    val disable_notification: Boolean? = null,
    val reply_to_message_id: Long? = null
)

class TelegramApi(private val token: String) {
    private val client = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(30))
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(30))
        .build()

    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        setSerializationInclusion(JsonInclude.Include.NON_NULL) // <<< не сериализуем null-поля
    }
    private val json = "application/json; charset=utf-8".toMediaType()

    fun getMe(): Boolean {
        val url = "${AppConfig.TELEGRAM_BASE}/bot$token/getMe"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                println("GETME-HTTP-ERR: code=${resp.code} msg=${resp.message} body=$raw")
                return false
            }
            val parsed: TgApiResp<TgApiUserMe> = mapper.readValue(raw)
            if (!parsed.ok) {
                println("GETME-API-ERR: code=${parsed.error_code} desc=${parsed.description}")
                return false
            }
            val me = parsed.result!!
            println("GETME: ok id=${me.id} username=@${me.username ?: "unknown"}")
            return true
        }
    }

    fun sendMessage(chatId: Long, text: String): Boolean {
        var allOk = true
        for (chunk in chunks(text)) {
            val payload = mapper.writeValueAsString(TgSendMessage(chat_id = chatId, text = chunk))
            val req = Request.Builder()
                .url("${AppConfig.TELEGRAM_BASE}/bot$token/sendMessage")
                .post(payload.toRequestBody(json))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    allOk = false
                    println("SEND-ERR: code=${resp.code} msg=${resp.message} body=$body")
                } else {
                    println("SEND: 200 len=${chunk.length}")
                }
            }
        }
        return allOk
    }

    private fun chunks(s: String): List<String> {
        if (s.length <= TG_LIMIT) return listOf(s)
        val out = ArrayList<String>(s.length / TG_LIMIT + 1)
        var i = 0
        while (i < s.length) {
            val e = kotlin.math.min(i + TG_LIMIT, s.length)
            out += s.substring(i, e)
            i = e
        }
        return out
    }
}
