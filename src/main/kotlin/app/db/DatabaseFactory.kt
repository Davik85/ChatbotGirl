package app.db

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

object Users : Table("users") {
    val id = long("id")
    val firstName = varchar("first_name", 64).nullable()
    val username = varchar("username", 64).nullable()
    val isPremiumUntil = long("premium_until").default(0L)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Messages : Table("messages") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").index()
    val role = varchar("role", 16) // user/assistant/system
    val content = text("content")
    val ts = long("ts")
    override val primaryKey = PrimaryKey(id)
}

object MemoryNotes : Table("memory_notes") {
    val userId = long("user_id")
    val note = text("note")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object UserStats : Table("user_stats") {
    val userId = long("user_id")
    val day = varchar("day", 10) // yyyy-MM-dd
    val sentToday = integer("sent_today").default(0)
    override val primaryKey = PrimaryKey(userId)
}

object ProcessedUpdates : Table("processed_updates") {
    val updateId = long("update_id")
    val ts = long("ts")
    override val primaryKey = PrimaryKey(updateId)
}

object DatabaseFactory {
    fun init() {
        Database.connect("jdbc:sqlite:./bot.db", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users, Messages, MemoryNotes, UserStats, ProcessedUpdates
            )
            cleanupLegacyIndexes()
        }
    }
    private fun cleanupLegacyIndexes() {
        val drops = listOf(
            "DROP INDEX IF EXISTS users_id",
            "DROP INDEX IF EXISTS memory_notes_user_id",
            "DROP INDEX IF EXISTS user_stats_user_id",
            "DROP INDEX IF EXISTS processed_updates_update_id"
        )

        drops.forEach { sql ->
            try {
                TransactionManager.current().exec(sql)
            } catch (_: Exception) {
            }
        }
    }
}

object UserRepo {
    fun upsert(userId: Long, firstName: String?, username: String?) = transaction {
        val exists = Users
            .slice(Users.id)
            .select { Users.id eq userId }
            .limit(1)
            .any()

        if (!exists) {
            Users.insert {
                it[id] = userId
                it[Users.firstName] = firstName
                it[Users.username] = username
                it[createdAt] = System.currentTimeMillis()
            }
        } else {
            Users.update({ Users.id eq userId }) {
                it[Users.firstName] = firstName
                it[Users.username] = username
            }
        }
    }
}

object UpdatesRepo {
    fun seen(updateId: Long): Boolean = transaction {
        !ProcessedUpdates
            .select { ProcessedUpdates.updateId eq updateId }
            .limit(1)
            .empty()
    }

    fun mark(updateId: Long) = transaction {
        ProcessedUpdates.insert {
            it[ProcessedUpdates.updateId] = updateId
            it[ts] = System.currentTimeMillis()
        }
    }
}

object PremiumRepo {
    fun isPremium(userId: Long): Boolean = transaction {
        Users
            .slice(Users.isPremiumUntil)
            .select { Users.id eq userId }
            .firstOrNull()
            ?.get(Users.isPremiumUntil)
            ?.let { it > System.currentTimeMillis() } == true
    }
}
