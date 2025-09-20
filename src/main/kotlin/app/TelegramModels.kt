package app

// Minimal Telegram DTOs to avoid heavy libs. Only what we use.
data class TgUpdate(val update_id: Long, val message: TgMessage?)
data class TgMessage(
    val message_id: Long,
    val text: String?,
    val date: Long,
    val chat: TgChat,
    val from: TgUser?
)
data class TgChat(val id: Long, val type: String)
data class TgUser(val id: Long, val is_bot: Boolean, val first_name: String?, val username: String?)

data class TgSendMessage(val chat_id: Long, val text: String, val parse_mode: String? = "HTML")
