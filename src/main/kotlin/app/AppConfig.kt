package app

import io.github.cdimascio.dotenv.dotenv

object AppConfig {
    private val env = dotenv { ignoreIfMissing = true }
    private fun readRaw(key: String): String? = System.getenv(key) ?: env[key]

    // Санитайз: убираем кавычки, \r\n, NBSP, zero-width, управляющие символы
    private fun clean(s: String?): String? {
        if (s == null) return null
        var v = s.trim().trim('"', '\'')
        v = v.replace("\r", "").replace("\n", "")
        v = v.replace(Regex("[\\u0000-\\u001F\\u007F\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u3000]"), "")
        return if (v.isBlank()) null else v
    }

    private fun requireOrError(name: String, raw: String?, validator: (String) -> Boolean): String {
        val v = clean(raw)
        require(!v.isNullOrBlank()) { "$name is required" }
        require(validator(v)) { "$name has invalid format" }
        return v!!
    }

    // Валидаторы
    private val tgTokenRegex = Regex("""^\d+:[A-Za-z0-9_-]{30,}$""")
    private fun isValidTgToken(s: String) = tgTokenRegex.matches(s)

    private val openAiRegex = Regex("""^(sk-[A-Za-z0-9_-]{10,}|sk-proj-[A-Za-z0-9_-]{10,})$""")
    private fun isValidOpenAi(s: String) = openAiRegex.matches(s)

    // Конфиг
    val telegramToken: String by lazy {
        requireOrError("TELEGRAM_TOKEN", readRaw("TELEGRAM_TOKEN"), ::isValidTgToken)
    }
    val openAiApiKey: String by lazy {
        requireOrError("OPENAI_API_KEY", readRaw("OPENAI_API_KEY"), ::isValidOpenAi)
    }

    // Опционально: для project-ключей
    val openAiOrg: String? by lazy { clean(readRaw("OPENAI_ORG")) }
    val openAiProject: String? by lazy { clean(readRaw("OPENAI_PROJECT")) }

    // Константы
    const val TELEGRAM_BASE = "https://api.telegram.org"
    const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"

    const val MAX_REPLY_CHARS = 1800
    const val FREE_DAILY_MSG_LIMIT = 20
    const val LIMIT_REACHED_TEXT =
        "Сегодня лимит бесплатных сообщений исчерпан. Оформи подписку и общайся без ограничений ❤️"
    const val FALLBACK_REPLY =
        "Похоже, я устала… Давай продолжим чуть позже?"
}
