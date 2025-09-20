package app.web

import app.AppConfig
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
    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()
    private val json = "application/json; charset=utf-8".toMediaType()

    fun sendMessage(chatId: Long, text: String) {
        val payload = mapper.writeValueAsString(TgSendMessage(chatId, text))
        val req = Request.Builder()
            .url("https://api.telegram.org/bot$token/sendMessage")
            .post(payload.toRequestBody(json))
            .build()
        client.newCall(req).execute().use { /* ignore body, log errors if needed */ }
    }

    fun setWebhook(url: String) {
        val req = Request.Builder()
            .url("https://api.telegram.org/bot$token/setWebhook?url=$url")
            .get()
            .build()
        client.newCall(req).execute().use { /* log status */ }
    }
}
