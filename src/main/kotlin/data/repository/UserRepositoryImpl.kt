package data.repository

import data.database.tables.RefreshTokensTable
import data.database.tables.UsersTable
import domain.model.User
import domain.repository.UserRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.UUID

class UserRepositoryImpl : UserRepository {

    // ── Row → Domain Model ────────────────────────────────────
    private fun toUser(row: ResultRow) = User(
        id              = row[UsersTable.id].toString(),
        phone           = row[UsersTable.phone],
        name            = row[UsersTable.name],
        bio             = row[UsersTable.bio],
        profileImageUrl = row[UsersTable.profileImageUrl],
        isOnline        = row[UsersTable.isOnline],
        lastSeen        = row[UsersTable.lastSeen].toString(),
        createdAt       = row[UsersTable.createdAt].toString(),
        isVerified      = row[UsersTable.isVerified],
        publicKey       = row[UsersTable.publicKey]
    )

    override suspend fun createUser(phone: String): User =
        newSuspendedTransaction {
            // إذا كان المستخدم موجوداً، أرجعه
            val existing = UsersTable.selectAll()
                .where { UsersTable.phone eq phone }
                .singleOrNull()
            if (existing != null) return@newSuspendedTransaction toUser(existing)

            val id = UsersTable.insert {
                it[UsersTable.phone] = phone
            } get UsersTable.id

            UsersTable.selectAll()
                .where { UsersTable.id eq id }
                .single()
                .let(::toUser)
        }

    override suspend fun getUserById(id: String): User? =
        newSuspendedTransaction {
            UsersTable.selectAll()
                .where { UsersTable.id eq UUID.fromString(id) }
                .singleOrNull()
                ?.let(::toUser)
        }

    override suspend fun getUserByPhone(phone: String): User? =
        newSuspendedTransaction {
            UsersTable.selectAll()
                .where { UsersTable.phone eq phone }
                .singleOrNull()
                ?.let(::toUser)
        }

    override suspend fun updateProfile(
        userId: String,
        name: String?,
        bio: String?,
        profileImageUrl: String?
    ): User? = newSuspendedTransaction {
        val uid = UUID.fromString(userId)
        UsersTable.update({ UsersTable.id eq uid }) {
            name?.let            { v -> it[UsersTable.name]            = v }
            bio?.let             { v -> it[UsersTable.bio]             = v }
            profileImageUrl?.let { v -> it[UsersTable.profileImageUrl] = v }
            it[UsersTable.isVerified] = true
        }
        UsersTable.selectAll().where { UsersTable.id eq uid }.singleOrNull()?.let(::toUser)
    }

    override suspend fun updateOnlineStatus(userId: String, isOnline: Boolean) =
        newSuspendedTransaction {
            UsersTable.update({ UsersTable.id eq UUID.fromString(userId) }) {
                it[UsersTable.isOnline] = isOnline
                if (!isOnline) it[UsersTable.lastSeen] = LocalDateTime.now()
            }
            Unit
        }

    override suspend fun updateLastSeen(userId: String) =
        newSuspendedTransaction {
            UsersTable.update({ UsersTable.id eq UUID.fromString(userId) }) {
                it[UsersTable.lastSeen] = LocalDateTime.now()
            }
            Unit
        }

    override suspend fun searchUsers(query: String, currentUserId: String): List<User> =
        newSuspendedTransaction {
            val uid = UUID.fromString(currentUserId)
            UsersTable.selectAll().where {
                (UsersTable.id neq uid) and
                (UsersTable.isVerified eq true) and
                ((UsersTable.name like "%$query%") or
                 (UsersTable.phone like "%$query%"))
            }
            .limit(20)
            .map(::toUser)
        }

    override suspend fun getUsersByIds(ids: List<String>): List<User> =
        newSuspendedTransaction {
            val uuids = ids.map { UUID.fromString(it) }
            UsersTable.selectAll()
                .where { UsersTable.id inList uuids }
                .map(::toUser)
        }

    override suspend fun savePublicKey(userId: String, publicKey: String) =
        newSuspendedTransaction {
            UsersTable.update({ UsersTable.id eq UUID.fromString(userId) }) {
                it[UsersTable.publicKey] = publicKey
            }
            Unit
        }

    override suspend fun saveRefreshToken(userId: String, token: String, expiresAt: Long) =
        newSuspendedTransaction {
            // احذف القديم
            RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq UUID.fromString(userId) }
            // أضف الجديد
            RefreshTokensTable.insert {
                it[RefreshTokensTable.userId]    = UUID.fromString(userId)
                it[RefreshTokensTable.token]     = token
                it[RefreshTokensTable.expiresAt] = expiresAt
            }
            Unit
        }

    override suspend fun getRefreshToken(userId: String): String? =
        newSuspendedTransaction {
            RefreshTokensTable.selectAll()
                .where { RefreshTokensTable.userId eq UUID.fromString(userId) }
                .singleOrNull()
                ?.get(RefreshTokensTable.token)
        }

    override suspend fun deleteRefreshToken(userId: String) =
        newSuspendedTransaction {
            RefreshTokensTable.deleteWhere {
                RefreshTokensTable.userId eq UUID.fromString(userId)
            }
            Unit
        }
}
