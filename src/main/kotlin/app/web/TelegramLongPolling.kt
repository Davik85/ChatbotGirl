package app.web

import app.AppConfig
import app.llm.OpenAIClient
import app.logic.MemoryService
import app.logic.PersonaPrompt
import app.logic.RateLimiter
import app.db.PremiumRepo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration
import kotlin.math.max
import app.llm.OpenAIClient.ChatMessage
import app.logic.Safety


private const val LONG_POLL_TIMEOUT_SEC = 25
private const val MAX_ATTEMPTS = 3


private data class TgUpdate(val update_id: Long, val message: TgMessage?)
private data class TgMessage(
    val message_id: Long,
    val date: Long,
    val text: String?,
    val chat: TgChat,
    val from: TgUser?
)

private data class TgChat(val id: Long)
private data class TgUser(val id: Long, val first_name: String?, val username: String?)
private data class TgResp<T>(
    val ok: Boolean,
    val result: T?,
    val error_code: Int? = null,
    val description: String? = null,
    val parameters: TgErrParams? = null
)

private data class TgErrParams(val retry_after: Int? = null)

class TelegramLongPolling(
    private val token: String,
    private val tg: TelegramApi,
    private val ai: OpenAIClient,
    private val mem: MemoryService
) {
    private val http = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(35))
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(35))
        .build()
    private val mapper = jacksonObjectMapper()
    private val json = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var running = true

    suspend fun start() {
        runCatching { deleteWebhook() }

        var offset: Long = 0
        var backoff = 400L
        while (running) {
            try {
                val url =
                    "${AppConfig.TELEGRAM_BASE}/bot$token/getUpdates?timeout=$LONG_POLL_TIMEOUT_SEC&offset=$offset&allowed_updates=%5B%22message%22%5D"
                val req = Request.Builder().url(url).get().build()
                http.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("getUpdates HTTP ${resp.code} ${resp.message} body=$raw")
                    val data: TgResp<List<TgUpdate>> = mapper.readValue(raw)
                    if (!data.ok) {
                        val ra = data.parameters?.retry_after
                        if (data.error_code == 429 && ra != null) {
                            delay(ra * 1000L); return@use
                        }
                        error("getUpdates API ${data.error_code}: ${data.description}")
                    }

                    val updates = data.result.orEmpty()
                    if (updates.isEmpty()) {
                        backoff = 400L
                    } else {
                        for (u in updates) {
                            offset = max(offset, u.update_id + 1)
                            u.message?.let { handleMessage(it) }
                        }
                        backoff = 400L
                    }
                }
            } catch (e: Exception) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(15_000)
            }
        }
    }

    fun stop() {
        running = false
    }

    private suspend fun deleteWebhook() {
        val url = "${AppConfig.TELEGRAM_BASE}/bot$token/deleteWebhook?drop_pending_updates=true"
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().close()
    }

    private fun handleMessage(msg: TgMessage) {
        val userId = msg.from?.id ?: msg.chat.id
        val text = msg.text?.trim().orEmpty()
        if (text.isEmpty()) return

        if (Safety.isCrisis(text)) {
            val safe = """
            Похоже, тебе сейчас очень тяжело. Ты не один.
            В экстренной ситуации звони 112. Бесплатные линии поддержки: 8-800-2000-122 (дети, подростки), 8-800-700-06-00 (круглосуточно).
            Я рядом и готова поговорить столько, сколько нужно.
        """.trimIndent()
            tg.sendMessage(msg.chat.id, safe)
            return
        }

        val isPremium = app.db.PremiumRepo.isPremium(userId)
        if (!isPremium && !RateLimiter.canSend(userId)) {
            tg.sendMessage(msg.chat.id, app.AppConfig.LIMIT_REACHED_TEXT)
            return
        }

        val system = ChatMessage("system", PersonaPrompt.system())
        val memoryNote = MemoryService.getNote(userId)?.let { ChatMessage("system", "Memory: $it") }
        val history = MemoryService.recentDialog(userId).flatMap {
            listOf(ChatMessage(it.first, it.second))
        }

        val messages = buildList {
            add(system)
            if (memoryNote != null) add(memoryNote)
            addAll(history)
            add(ChatMessage("user", text))
        }

        val reply = try {
            ai.complete(messages)
        } catch (_: Exception) {
            "Похоже, я устала… Давай продолжим чуть позже?"
        }

        MemoryService.append(userId, "user", text, System.currentTimeMillis())
        MemoryService.append(userId, "assistant", reply, System.currentTimeMillis())
        RateLimiter.increment(userId)

        tg.sendMessage(msg.chat.id, reply)
    }
}


