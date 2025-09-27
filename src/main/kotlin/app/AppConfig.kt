package app

import io.github.cdimascio.dotenv.dotenv

object AppConfig {
    private val env = dotenv { ignoreIfMissing = true }
    private fun readRaw(key: String): String? = System.getenv(key) ?: env[key]

    // --- Санитайз: срезаем пробелы, кавычки, \r\n ---
    private fun clean(s: String?): String? {
        if (s == null) return null
        val trimmed = s.trim().trim('"', '\'')
        return trimmed.replace("\r", "").replace("\n", "")
    }

    private fun requireOrError(name: String, raw: String?, validator: (String) -> Boolean): String {
        val v = clean(raw)
        require(!v.isNullOrBlank()) { "$name is required" }
        require(validator(v)) { "$name has invalid format" }
        return v!!
    }

    // --- Валидаторы формата ---
    private val tgTokenRegex = Regex("""^\d+:[A-Za-z0-9_-]{30,}$""") // типичный формат токена BotFather
    private fun isValidTgToken(s: String) = tgTokenRegex.matches(s)

    // для API-ключей OpenAI допустимы и sk-..., и sk-proj-...
    private val openAiRegex = Regex("""^sk-[A-Za-z0-9_-]{10,}|^sk-proj-[A-Za-z0-9_-]{10,}""")
    private fun isValidOpenAi(s: String) = openAiRegex.containsMatchIn(s)

    // --- Публичные значения конфигурации ---
    val telegramToken: String by lazy {
        requireOrError("TELEGRAM_TOKEN", readRaw("TELEGRAM_TOKEN"), ::isValidTgToken)
    }
    val openAiApiKey: String by lazy {
        requireOrError("OPENAI_API_KEY", readRaw("OPENAI_API_KEY"), ::isValidOpenAi)
    }

    // только long-polling режим
    const val TELEGRAM_BASE = "https://api.telegram.org"
    const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"

    // UX/лимиты
    const val MAX_REPLY_CHARS = 1800
    const val FREE_DAILY_MSG_LIMIT = 10
    const val LIMIT_REACHED_TEXT =
        "Сегодня лимит бесплатных сообщений исчерпан. Оформи подписку и общайся без ограничений ❤️"
    const val FALLBACK_REPLY =
        "Похоже, я устала… Давай продолжим чуть позже?"
}
