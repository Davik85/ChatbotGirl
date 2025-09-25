package app.logic

import app.AppConfig
import app.db.UserStats
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

object RateLimiter {
    fun canSend(userId: Long): Boolean = transaction {
        val row = UserStats
            .select { UserStats.userId eq userId }
            .firstOrNull()

        if (row == null) return@transaction true

        val today = LocalDate.now().toString()
        val sameDay = row[UserStats.day] == today
        val count = row[UserStats.sentToday]

        !sameDay || count < AppConfig.FREE_DAILY_MSG_LIMIT
    }
    fun increment(userId: Long) = transaction {
        val today = LocalDate.now().toString()

        val existing = UserStats
            .select { UserStats.userId eq userId }
            .firstOrNull()

        if (existing == null) {
            UserStats.insert {
                it[UserStats.userId] = userId
                it[UserStats.day] = today
                it[UserStats.sentToday] = 1
            }
        } else {
            val sameDay = existing[UserStats.day] == today
            if (!sameDay) {
                UserStats.update({ UserStats.userId eq userId }) {
                    it[UserStats.day] = today
                    it[UserStats.sentToday] = 1
                }
            } else {
                val newCount = existing[UserStats.sentToday] + 1
                UserStats.update({ UserStats.userId eq userId }) {
                    it[UserStats.sentToday] = newCount
                }
            }
        }
    }
}
