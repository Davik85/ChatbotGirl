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

        // 1) Жёстко инициализируем AppConfig (с валидацией и санитайзом)
        val tgToken = runCatching { AppConfig.telegramToken }.getOrElse {
            println("FATAL: TELEGRAM_TOKEN invalid or missing. ${it.message}"); return
        }
        val aiKey = runCatching { AppConfig.openAiApiKey }.getOrElse {
            println("FATAL: OPENAI_API_KEY invalid or missing. ${it.message}"); return
        }
        println("TOKENS: tg=${mask(tgToken)} ai=${mask(aiKey)}")

        // 2) БД
        DatabaseFactory.init()

        // 3) Клиенты
        val tg = TelegramApi(tgToken)
        val ai = OpenAIClient(aiKey)

        // 4) Проверка доступности OpenAI, чтобы не падать в fallback
        if (!ai.healthCheck()) {
            println("FATAL: OpenAI недоступен (ключ/доступ/модель). Проверь OPENAI_API_KEY и права на модель в OpenAI.")
            // Можно выйти return, чтобы не запускать поллер без LLM
            return
        }

        // 5) Проверка токена Telegram
        if (!tg.getMe()) {
            println("FATAL: invalid TELEGRAM_TOKEN — бот не сможет отвечать.")
            return
        }

        // 6) Запуск поллера
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
