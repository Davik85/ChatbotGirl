package app

import app.db.DatabaseFactory
import app.llm.OpenAIClient
import app.web.TelegramApi
import app.web.TelegramLongPolling
import app.logic.MemoryService
import kotlinx.coroutines.runBlocking

private fun mask(s: String, head: Int = 6, tail: Int = 4): String =
    if (s.length <= head + tail) "*".repeat(s.length)
    else s.take(head) + "*".repeat(s.length - head - tail) + s.takeLast(tail)

fun main() {
    try {
        val rawTg = System.getenv("TELEGRAM_TOKEN")
        val rawAi = System.getenv("OPENAI_API_KEY")
        println("ENV: TELEGRAM_TOKEN=${if (rawTg.isNullOrBlank()) "missing" else "present"}, OPENAI_API_KEY=${if (rawAi.isNullOrBlank()) "missing" else "present"}")

        println("BOOT: long-polling mode")

        val tgToken = runCatching { AppConfig.telegramToken }.getOrElse {
            println("FATAL: TELEGRAM_TOKEN invalid or missing. ${it.message}"); return
        }
        val aiKey = runCatching { AppConfig.openAiApiKey }.getOrElse {
            println("FATAL: OPENAI_API_KEY invalid or missing. ${it.message}"); return
        }
        println("TOKENS: tg=${mask(tgToken)} ai=${mask(aiKey)}")

        DatabaseFactory.init()

        val tg = TelegramApi(tgToken)
        val ai = OpenAIClient(aiKey)

        if (!ai.healthCheck()) {
            println("FATAL: OpenAI недоступен (ключ/доступ/модель). Проверь OPENAI_API_KEY и права на модель в OpenAI.")
            return
        }

        if (!tg.getMe()) {
            println("FATAL: invalid TELEGRAM_TOKEN — бот не сможет отвечать.")
            return
        }

        println("Starting Telegram long polling…")
        runBlocking {
            TelegramLongPolling(
                token = tgToken,
                tg = tg,
                ai = ai,
                mem = MemoryService
            ).start()
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}
