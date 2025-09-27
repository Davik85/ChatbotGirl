package app.web

import app.AppConfig
import app.common.Json
import app.db.PremiumRepo
import app.llm.OpenAIClient
import app.llm.dto.ChatMessage
import app.logic.MemoryService
import app.logic.PersonaPrompt
import app.logic.RateLimiter
import app.logic.Safety
import app.web.dto.LpMessage
import app.web.dto.LpResp
import app.web.dto.LpUpdate
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import kotlin.math.max

private const val LONG_POLL_TIMEOUT_SEC = 25

class TelegramLongPolling(
    private val token: String,
    private val tg: TelegramApi,
    private val ai: OpenAIClient,
    private val mem: MemoryService
) {
    private val http = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(LONG_POLL_TIMEOUT_SEC.toLong() + 10))
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(LONG_POLL_TIMEOUT_SEC.toLong() + 10))
        .build()

    private val mapper = Json.mapper
    @Volatile private var running = true

    suspend fun start() {
        tg.getMe() // просто лого, не фейлим старт

        var offset = 0L
        var backoff = 400L
        println("POLL: start (timeout=${LONG_POLL_TIMEOUT_SEC}s)")

        while (running) {
            try {
                val url = "${AppConfig.TELEGRAM_BASE}/bot$token/getUpdates" +
                        "?timeout=$LONG_POLL_TIMEOUT_SEC&offset=$offset&allowed_updates=%5B%22message%22%5D"
                val req = Request.Builder().url(url).get().build()

                http.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("getUpdates HTTP ${resp.code} ${resp.message} body=$raw")

                    val data: LpResp<List<LpUpdate>> = mapper.readValue(raw)
                    if (!data.ok) {
                        val ra = data.parameters?.retry_after
                        if (data.error_code == 429 && ra != null) {
                            println("POLL: 429 Too Many Requests → sleep ${ra}s")
                            delay(ra * 1000L); return@use
                        }
                        error("getUpdates API ${data.error_code}: ${data.description}")
                    }

                    val updates = data.result.orEmpty()
                    if (updates.isEmpty()) {
                        println("POLL: 0 updates (offset=$offset)")
                    } else {
                        println("POLL: got ${updates.size} updates (offset=$offset)")
                        for (u in updates) {
                            offset = max(offset, u.update_id + 1)
                            u.message?.let { handleMessage(it) }
                        }
                    }
                    backoff = 400L
                }
            } catch (e: Exception) {
                println("POLL-ERR: ${e.message}")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(15_000)
            }
        }
    }

    fun stop() { running = false }

    private fun handleMessage(msg: LpMessage) {
        val userId = msg.from?.id ?: msg.chat.id
        val text = msg.text?.trim().orEmpty()
        if (text.isEmpty()) return

        println("MSG: from=$userId text='${text.take(60)}'")

        if (text == "/start") {
            tg.sendMessage(msg.chat.id, "Привет! Я Ева — тёплая AI-подруга. Напиши, как проходит день 💬")
            return
        }

        if (Safety.isCrisis(text)) {
            val safe = """
                Похоже, тебе сейчас очень тяжело. Ты не один.
                В экстренной ситуации звони 112. Бесплатные линии поддержки: 8-800-2000-122, 8-800-700-06-00.
                Я рядом и готова поговорить столько, сколько нужно.
            """.trimIndent()
            tg.sendMessage(msg.chat.id, safe); return
        }

        val isPremium = PremiumRepo.isPremium(userId)
        if (!isPremium && !RateLimiter.canSend(userId)) {
            tg.sendMessage(msg.chat.id, app.AppConfig.LIMIT_REACHED_TEXT); return
        }

        val system = ChatMessage("system", PersonaPrompt.system())
        val memoryNote = mem.getNote(userId)?.let { ChatMessage("system", "Memory: $it") }
        val history = mem.recentDialog(userId).flatMap { listOf(ChatMessage(it.first, it.second)) }

        val messages = buildList {
            add(system)
            if (memoryNote != null) add(memoryNote)
            addAll(history)
            add(ChatMessage("user", text))
        }

        val reply = try { ai.complete(messages) } catch (e: Exception) {
            println("AI-ERR: ${e.message}"); app.AppConfig.FALLBACK_REPLY
        }

        mem.append(userId, "user", text, System.currentTimeMillis())
        mem.append(userId, "assistant", reply, System.currentTimeMillis())
        RateLimiter.increment(userId)

        val ok = tg.sendMessage(msg.chat.id, reply)
        if (!ok) println("SEND-WARN: message wasn't delivered (see SEND-ERR above).")
    }
}
