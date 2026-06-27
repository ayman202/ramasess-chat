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
import java.net.URI


object DatabaseConfig {

    private val log = LoggerFactory.getLogger("DatabaseConfig")

    fun init(config: ApplicationConfig) {

        val rawUrl = config.property("database.url").getString()
        log.info("Raw DB URL received: ${rawUrl.take(30)}...")

        val hikari = HikariConfig()
        hikari.driverClassName = "org.postgresql.Driver"

        // ── Railway بيبعت: postgresql://user:pass@host:port/db ──
        // ── لازم نقسّمها يدوياً ───────────────────────────────────
        if (rawUrl.startsWith("postgresql://") || rawUrl.startsWith("postgres://")) {

            val uri = URI(rawUrl)

            val host     = uri.host
            val port     = if (uri.port == -1) 5432 else uri.port
            val dbName   = uri.path.removePrefix("/")
            val userInfo = uri.userInfo?.split(":") ?: emptyList()
            val user     = userInfo.getOrNull(0) ?: ""
            val password = userInfo.getOrNull(1) ?: ""

            hikari.jdbcUrl  = "jdbc:postgresql://$host:$port/$dbName"
            hikari.username = user
            hikari.password = password

            log.info("✅ Parsed JDBC URL: jdbc:postgresql://$host:$port/$dbName")

        } else {
            // لو بالفعل jdbc:postgresql://... استخدمه مباشرة
            hikari.jdbcUrl = rawUrl
        }

        hikari.maximumPoolSize   = 10
        hikari.isAutoCommit      = false
        hikari.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        hikari.validate()

        Database.connect(HikariDataSource(hikari))
        log.info("✅ Database connected!")

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
        log.info("✅ Tables ready!")
    }
}