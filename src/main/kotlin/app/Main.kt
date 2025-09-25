package app

import app.db.DatabaseFactory
import app.llm.OpenAIClient
import app.web.TelegramApi
import app.web.TelegramLongPolling
import app.logic.MemoryService
import kotlinx.coroutines.runBlocking

fun main() {
    println("BOOT: USE_LONG_POLLING=${AppConfig.useLongPolling}")

    DatabaseFactory.init()

    val tg = TelegramApi(AppConfig.telegramToken)
    val ai = OpenAIClient(AppConfig.openAiApiKey)

    if (!AppConfig.useLongPolling) {
        error("This build runs without webhook. Set USE_LONG_POLLING=true.")
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
}
