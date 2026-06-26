package core.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import data.database.tables.ContactsTable
import data.database.tables.ConversationParticipantsTable
import data.database.tables.ConversationsTable
import data.database.tables.MessageReactionsTable
import data.database.tables.MessagesTable
import data.database.tables.RefreshTokensTable
import data.database.tables.StoriesTable
import data.database.tables.StoryViewsTable
import data.database.tables.UsersTable
import io.ktor.server.config.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseConfig {

    private val log = LoggerFactory.getLogger("DatabaseConfig")

    fun init(config: ApplicationConfig) {
        val jdbcUrl  = config.property("database.url").getString()
        val user     = config.propertyOrNull("database.user")?.getString()
        val password = config.propertyOrNull("database.password")?.getString()
        val poolSize = config.propertyOrNull("database.maxPoolSize")?.getString()?.toInt() ?: 10

        // ── HikariCP ──────────────────────────────────────────
        val hikari = HikariConfig().apply {
            this.jdbcUrl         = jdbcUrl
            this.username        = user
            this.password        = password
            this.maximumPoolSize = poolSize
            this.isAutoCommit    = false
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            driverClassName = "org.postgresql.Driver"
            validate()
        }

        Database.connect(HikariDataSource(hikari))
        log.info("✅ Database connected → $jdbcUrl")

        // ── إنشاء الجداول ─────────────────────────────────────
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                RefreshTokensTable,
                ConversationsTable,
                ConversationParticipantsTable,
                MessagesTable,
                MessageReactionsTable,
                StoriesTable,
                StoryViewsTable,
                ContactsTable
            )
        }
        log.info("✅ Database tables ready")
    }
}
