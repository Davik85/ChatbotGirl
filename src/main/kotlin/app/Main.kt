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

/**
 * Ktor webhook that handles Telegram updates and replies through OpenAI.
 * Paywall integration can be added as a middleware (check premium flag before generation).
 */
fun main() {
    DatabaseFactory.init()
    val config = AppConfig
    val tg = TelegramApi(config.telegramToken)
    val mapper = jacksonObjectMapper()
    val ai = OpenAIClient(config.openAiApiKey)

    // Optional: set webhook automatically on boot (use your public HTTPS URL)
    // val publicUrl = "https://your.domain${config.telegramSecretPath}"
    // tg.setWebhook(publicUrl)

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        install(ContentNegotiation) { jackson() }

        routing {
            get("/") { call.respondText("OK") }

            post(config.telegramSecretPath) {
                val body = call.receiveText()
                val update: TgUpdate = mapper.readValue(body)
                val msg = update.message
                if (msg?.text.isNullOrBlank()) {
                    call.respondText("ignored"); return@post
                }

                val userId = msg!!.from?.id ?: msg.chat.id
                UserRepo.upsert(userId, msg.from?.first_name, msg.from?.username)

                val text = msg.text!!.trim()

                // Crisis and NSFW handling come first
                if (Safety.isCrisis(text)) {
                    val safe = """
                        Похоже, тебе сейчас очень тяжело. Ты не один.
                        В экстренной ситуации звони 112. Бесплатные линии поддержки: 8-800-2000-122 (дети, подростки), 8-800-700-06-00 (круглосуточно).
                        Я рядом и готова поговорить столько, сколько нужно.
                    """.trimIndent()
                    TelegramApi(AppConfig.telegramToken).sendMessage(msg.chat.id, safe)
                    call.respondText("ok"); return@post
                }

                // Rate limit free users
                if (!RateLimiter.canSend(userId)) {
                    val paywall = LIMIT_REACHED_TEXT
                    tg.sendMessage(msg.chat.id, paywall)
                    call.respondText("ok"); return@post
                }

                // Build context: persona system + memory note + last messages
                val system = ChatMessage("system", PersonaPrompt.system())
                val memoryNote = MemoryService.getNote(userId)?.let { ChatMessage("system", "Memory: $it") }
                val history = MemoryService.recentDialog(userId).flatMap {
                    listOf(ChatMessage(it.first, it.second))
                }

                val messages = buildList {
                    add(system)
                    if (memoryNote != null) add(memoryNote)
                    history.forEach { add(it) }
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
