package app.logic

import app.db.MemoryNotes
import app.db.Messages
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

const val LIMIT_DIALOG = 12

/**
 * Minimal memory:
 * - Raw message log
 * - Compact note updated every N messages
 * - Daily message count for free-limit
 */
object MemoryService {
    fun append(userId: Long, role: String, text: String, ts: Long) = transaction {
        Messages.insert {
            it[Messages.userId] = userId
            it[Messages.role] = role
            it[Messages.content] = text
            it[Messages.ts] = ts
        }
    }

    fun recentDialog(userId: Column<Long>, limit: Int = LIMIT_DIALOG): List<Pair<String, String>> = transaction {
        Messages.select { Messages.userId eq userId }
            .orderBy(Messages.id, SortOrder.DESC)
            .limit(limit)
            .map { it[Messages.role] to it[Messages.content] }
            .reversed()
    }

    fun getNote(userId: Column<Long>): String? = transaction {
        MemoryNotes.select { MemoryNotes.userId eq userId }.firstOrNull()?.get(MemoryNotes.note)
    }

    fun upsertNote(userId: Long, note: String) = transaction {
        val exists = MemoryNotes.select { MemoryNotes.userId eq userId }.count() > 0
        if (exists) {
            MemoryNotes.update({ MemoryNotes.userId eq userId }) {
                it[MemoryNotes.note] = note
                it[updatedAt] = System.currentTimeMillis()
            }
        } else {
            MemoryNotes.insert {
                it[MemoryNotes.userId] = userId
                it[MemoryNotes.note] = note
                it[updatedAt] = System.currentTimeMillis()
            }
        }
    }

    fun dayKey(): String = LocalDate.now().toString()
}
