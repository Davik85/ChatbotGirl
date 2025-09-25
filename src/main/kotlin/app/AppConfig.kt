package app

import io.github.cdimascio.dotenv.dotenv

object AppConfig {
    private val env = dotenv { ignoreIfMissing = true }

    private fun read(key: String): String? = System.getenv(key) ?: env[key]

    val telegramToken: String by lazy {
        read("TELEGRAM_TOKEN") ?: error("TELEGRAM_TOKEN is required")
    }

    val openAiApiKey: String by lazy {
        read("OPENAI_API_KEY") ?: error("OPENAI_API_KEY is required")
    }

    val useLongPolling: Boolean by lazy {
        (read("USE_LONG_POLLING") ?: "true").toBooleanStrictOrNull() ?: true
    }


    const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
    const val TELEGRAM_BASE = "https://api.telegram.org"


    const val MAX_REPLY_CHARS = 1800
    const val LIMIT_REACHED_TEXT =
        "Сегодня лимит бесплатных сообщений исчерпан. Оформи подписку и общайся без ограничений ❤️"
    const val FALLBACK_REPLY =
        "Похоже, я устала… Давай продолжим чуть позже?"
}
