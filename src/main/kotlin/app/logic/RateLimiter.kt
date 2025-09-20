package app.logic

import app.AppConfig
import app.db.UserStats
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

/**
 * Simple daily free message limiter.
 */
object RateLimiter {
    fun canSend(userId: Long): Boolean = transaction {
        val day = LocalDate.now().toString()
        val row = UserStats.select { UserStats.userId eq userId }.firstOrNull()
        if (row == null) return@transaction true
        val sameDay = row[UserStats.day] == day
        val count = row[UserStats.sentToday]
        !sameDay || count < AppConfig.FREE_DAILY_MSG_LIMIT
    }

    fun increment(userId: Long) = transaction {
        val day = LocalDate.now().toString()
        val existing = UserStats.select { UserStats.userId eq userId }.firstOrNull()
        if (existing == null) {
            UserStats.insert {
                it[UserStats.userId] = userId
                it[UserStats.day] = day
                it[sentToday] = 1
            }
        } else {
            if (existing[UserStats.day] != day) {
                UserStats.update({ UserStats.userId eq userId }) {
                    it[UserStats.day] = day
                    it[sentToday] = 1
                }
            } else {
                UserStats.update({ UserStats.userId eq userId }) {
                    it[sentToday] = existing[UserStats.sentToday] + 1
                }
            }
        }
    }
}
