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

        // ── الخطوة 1: اقرأ الـ URL من الـ config ──────────────
        val rawUrl = config.property("database.url").getString()

        // ── الخطوة 2: حوّل الـ URL لصيغة JDBC ─────────────────
        // Railway بيبعت:  postgresql://user:pass@host:port/db
        // Exposed محتاج: jdbc:postgresql://user:pass@host:port/db
        val jdbcUrl = when {
            rawUrl.startsWith("jdbc:")         -> rawUrl
            rawUrl.startsWith("postgresql://") -> "jdbc:$rawUrl"
            rawUrl.startsWith("postgres://")   -> "jdbc:postgresql://" +
                    rawUrl.removePrefix("postgres://")
            else -> rawUrl
        }

        log.info("✅ Connecting to database...")

        // ── الخطوة 3: إعداد HikariCP ───────────────────────────
        val hikari = HikariConfig().apply {
            this.jdbcUrl     = jdbcUrl
            driverClassName  = "org.postgresql.Driver"
            maximumPoolSize  = 10
            isAutoCommit     = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        Database.connect(HikariDataSource(hikari))
        log.info("✅ Database connected!")

        // ── الخطوة 4: إنشاء الجداول لو مش موجودة ──────────────
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
        log.info("✅ Database tables ready!")
    }
}