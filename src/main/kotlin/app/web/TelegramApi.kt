package app.web

import app.AppConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration
import kotlin.math.min

private const val TG_LIMIT = 4096

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

    private val mapper = jacksonObjectMapper()
    private val json = "application/json; charset=utf-8".toMediaType()

    fun sendMessage(chatId: Long, text: String) {
        for (chunk in chunks(text)) {
            val payload = mapper.writeValueAsString(TgSendMessage(chat_id = chatId, text = chunk))
            val req = Request.Builder()
                .url("${AppConfig.TELEGRAM_BASE}/bot$token/sendMessage")
                .post(payload.toRequestBody(json))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    println("SEND-ERR: code=${resp.code} msg=${resp.message} body=$body")
                } else {
                    println("SEND: 200 len=${chunk.length}")
                }
            }
        }
    }

    private fun chunks(s: String): List<String> {
        if (s.length <= TG_LIMIT) return listOf(s)
        val out = ArrayList<String>(s.length / TG_LIMIT + 1)
        var i = 0
        while (i < s.length) {
            val e = min(i + TG_LIMIT, s.length)
            out += s.substring(i, e)
            i = e
        }
        return out
    }
}
