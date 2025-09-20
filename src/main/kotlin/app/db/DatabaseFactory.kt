package app.db

import app.AppConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update

/**
 * Simple SQLite storage for MVP. Replace with Postgres later.
 */
object DatabaseFactory {
    fun init() {
        Database.connect("jdbc:sqlite:./bot.db", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users, Messages, MemoryNotes, UserStats)
        }
    }
}

object Users : org.jetbrains.exposed.sql.Table("users") {
    val id = long("id").uniqueIndex()
    val firstName = varchar("first_name", 64).nullable()
    val username = varchar("username", 64).nullable()
    val isPremiumUntil = long("premium_until").default(0L)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Messages : org.jetbrains.exposed.sql.Table("messages") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").index()
    val role = varchar("role", 16) // user/assistant/system
    val content = text("content")
    val ts = long("ts")
    override val primaryKey = PrimaryKey(id)
}

object MemoryNotes : org.jetbrains.exposed.sql.Table("memory_notes") {
    val userId = long("user_id").uniqueIndex()
    val note = text("note") // compact summary JSON/text
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object UserStats : org.jetbrains.exposed.sql.Table("user_stats") {
    val userId = long("user_id").uniqueIndex()
    val day = varchar("day", 10) // yyyy-MM-dd
    val sentToday = integer("sent_today").default(0)
    override val primaryKey = PrimaryKey(userId)
}

object UserRepo {
    fun upsert(userId: Long, firstName: String?, username: String?) = transaction {
        val exists = Users.select { Users.id eq userId }.count() > 0
        if (!exists) {
            Users.insert {
                it[id] = userId
                it[firstName] = firstName
                it[username] = username
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
