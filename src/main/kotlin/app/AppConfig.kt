package app

import io.github.cdimascio.dotenv.dotenv

/**
 * Loads configuration from environment variables (12-factor).
 * For local dev, .env is allowed. In production, rely on real env vars.
 */
object AppConfig {
    const val OPENAI_URL: String = "https://api.openai.com/v1/chat/completions"
    const val LIMIT_REACHED_TEXT: String =
        "Сегодня лимит бесплатных сообщений исчерпан. Оформи подписку и общайся без ограничений ❤️"

    const val CRISIS_TEXT_PREFIX: String =
        "Похоже, тебе сейчас очень тяжело. Ты не один."

    // Avoid magic numbers: keep constants here
    const val DEFAULT_PORT: Int = 8080
    const val FREE_DAILY_MSG_LIMIT: Int = 10
    const val CONTEXT_TOKEN_BUDGET: Int = 8000
    const val MEMORY_SUMMARIZE_EACH_N_MESSAGES: Int = 6
    const val MAX_REPLY_CHARS: Int = 800 // guardrail for runaway responses

    private val env = dotenv {
        ignoreIfMissing = true
    }

    val port: Int = (System.getenv("PORT") ?: env["PORT"] ?: DEFAULT_PORT.toString()).toInt()
    val telegramToken: String = System.getenv("TELEGRAM_TOKEN") ?: env["TELEGRAM_TOKEN"]
    ?: error("TELEGRAM_TOKEN is required")
    val telegramSecretPath: String = System.getenv("TELEGRAM_WEBHOOK_SECRET_PATH")
        ?: env["TELEGRAM_WEBHOOK_SECRET_PATH"] ?: "/telegram-webhook"

    val openAiApiKey: String = System.getenv("OPENAI_API_KEY") ?: env["OPENAI_API_KEY"]
    ?: error("OPENAI_API_KEY is required")

    // Persona constants
    const val PERSONA_NAME: String = "Полина"
    const val PERSONA_AGE: String = "26"
    const val PERSONA_STYLE: String = "Warm, empathetic, playful yet respectful"

    // Pricing / paywall placeholders (integrate YooKassa/Stars later)
    const val PREMIUM_DAYS: Int = 30
}
