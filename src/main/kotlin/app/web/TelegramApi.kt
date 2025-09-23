package app.web

import app.AppConfig.TELEGRAM_BASE
import app.TgSendMessage
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimal Telegram API client for sendMessage.
 */

class TelegramApi(private val token: String) {
    private companion object {
        private const val TG_LIMIT = 4096
    }

    private fun chunks(s: String): List<String> {
        if (s.length <= TG_LIMIT) return listOf(s)
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val end = minOf(i + TG_LIMIT, s.length)
            out += s.substring(i, end)
            i = end
        }
        return out
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(duration = java.time.Duration.ofSeconds(15))
        .connectTimeout(duration = java.time.Duration.ofSeconds(10))
        .readTimeout(duration = java.time.Duration.ofSeconds(15))
        .build()
    private val mapper = jacksonObjectMapper()
    private val json = "application/json; charset=utf-8".toMediaType()

    fun sendMessage(chatId: Long, text: String) {
        val payloadMaker = { t: String ->
            mapper.writeValueAsString(TgSendMessage(chat_id = chatId, text = t))
        }
        for (chunk in chunks(text)) {
            val payload = payloadMaker(chunk)
            val req = Request.Builder()
                .url("$TELEGRAM_BASE/bot$token/sendMessage")
                .post(payload.toRequestBody(json))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string()
                    println("sendMessage failed: ${resp.code} ${resp.message} body=$body")
                }
            }
        }
    }

    fun setWebhook(url: String) {
        val req = Request.Builder()
            .url("$TELEGRAM_BASE/bot$token/setWebhook?url=$url")
            .get()
            .build()
        client.newCall(req).execute().use { /* log status */ }
    }
}
