package app

import app.AppConfig.LIMIT_REACHED_TEXT
import app.db.DatabaseFactory
import app.db.UserRepo
import app.llm.OpenAIClient
import app.llm.OpenAIClient.ChatMessage
import app.logic.MemoryService
import app.logic.PersonaPrompt
import app.logic.RateLimiter
import app.logic.Safety
import app.web.TelegramApi
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import app.db.UpdatesRepo
import app.db.PremiumRepo
import kotlinx.coroutines.runBlocking
import app.web.TelegramLongPolling

fun main() {
    println("ENV check: polling=${(System.getenv("USE_LONG_POLLING") ?: "true")} tg=${AppConfig.telegramToken.take(6)}***")

    DatabaseFactory.init()
    val config = AppConfig
    val tg = TelegramApi(config.telegramToken)
    val mapper = jacksonObjectMapper()
    val ai = OpenAIClient(config.openAiApiKey)

    val usePolling = (System.getenv("USE_LONG_POLLING") ?: "true").toBoolean()
    if (usePolling) {
        println("Starting Telegram long polling…")
        runBlocking {
            val poller = TelegramLongPolling(
                token = config.telegramToken,
                tg = tg,
                ai = ai,
                mem = MemoryService
            )
            poller.start()
        }
        return
    }

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        install(ContentNegotiation) { jackson() }

        routing {
            get("/") { call.respondText("OK") }
            get("/privacy") {
                call.respondText("Privacy Policy (stub). We process your data to provide the service. Not a medical service.")
            }
            get("/terms") {
                call.respondText("Terms of Service (stub). Subscriptions auto-renew until cancelled. Refund policy applies.")
            }

            post(config.telegramSecretPath) {
                val body = call.receiveText()
                val update: TgUpdate = mapper.readValue(body)
                // idempotency guard
                val updId = update.update_id
                if (UpdatesRepo.seen(updId)) {
                    call.respondText("dup"); return@post
                }
                UpdatesRepo.mark(updId)

                val msg = update.message
                if (msg?.text.isNullOrBlank()) {
                    call.respondText("ignored"); return@post
                }

                val userId = msg!!.from?.id ?: msg.chat.id
                UserRepo.upsert(userId, msg.from?.first_name, msg.from?.username)

                val text = msg.text!!.trim()

                if (Safety.isCrisis(text)) {
                    val safe = """
                        Похоже, тебе сейчас очень тяжело. Ты не один.
                        В экстренной ситуации звони 112. Бесплатные линии поддержки: 8-800-2000-122 (дети, подростки), 8-800-700-06-00 (круглосуточно).
                        Я рядом и готова поговорить столько, сколько нужно.
                    """.trimIndent()
                    tg.sendMessage(msg.chat.id, safe)
                    call.respondText("ok"); return@post
                }

                val isPremium = PremiumRepo.isPremium(userId)
                if (!isPremium && !RateLimiter.canSend(userId)) {
                    tg.sendMessage(msg.chat.id, LIMIT_REACHED_TEXT)
                    call.respondText("ok"); return@post
                }

                val system = ChatMessage("system", PersonaPrompt.system())
                val memoryNote = MemoryService.getNote(userId)?.let { ChatMessage("system", "Memory: $it") }
                val history = MemoryService.recentDialog(userId).flatMap { listOf(ChatMessage(it.first, it.second)) }

                val messages = buildList {
                    add(system)
                    if (memoryNote != null) add(memoryNote)
                    addAll(history)
                    add(ChatMessage("user", text))
                }

                val reply = ai.complete(messages)
                MemoryService.append(userId, "user", text, System.currentTimeMillis())
                MemoryService.append(userId, "assistant", reply, System.currentTimeMillis())
                RateLimiter.increment(userId)

                tg.sendMessage(msg.chat.id, reply)
                call.respondText("ok")
            }
        }
    }.start(wait = true)
}
