package app

import app.db.DatabaseFactory
import app.llm.OpenAIClient
import app.web.TelegramApi
import app.web.TelegramLongPolling
import app.logic.MemoryService
import kotlinx.coroutines.runBlocking

fun main() {
    val ai = OpenAIClient(AppConfig.openAiApiKey)

    if (!ai.healthCheck()) {
        println("FATAL: OpenAI недоступен (ключ/доступ/модель). Проверь OPENAI_API_KEY и модель в OpenAIClient.")
        // Можно не выходить, но тогда будешь получать только fallback.
    }

    try {
        val envHasToken = System.getenv("TELEGRAM_TOKEN") != null
        val envHasOpenAI = System.getenv("OPENAI_API_KEY") != null
        println("ENV: TELEGRAM_TOKEN=${if (envHasToken) "present" else "missing"}, OPENAI_API_KEY=${if (envHasOpenAI) "present" else "missing"}")

        println("BOOT: long-polling mode")

        DatabaseFactory.init()

        val tg = TelegramApi(AppConfig.telegramToken)
        val ai = OpenAIClient(AppConfig.openAiApiKey)

        if (!tg.getMe()) {
            println("FATAL: invalid TELEGRAM_TOKEN — бот не сможет отвечать.")
            return
        }

        println("Starting Telegram long polling…")
        runBlocking {
            TelegramLongPolling(
                token = AppConfig.telegramToken,
                tg = tg,
                ai = ai,
                mem = MemoryService
            ).start()
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}
