package data.repository

import domain.repository.ConversationRepository
import data.database.tables.ConversationParticipantsTable
import data.database.tables.ConversationsTable
import data.database.tables.MessagesTable
import data.database.tables.UsersTable
import domain.model.Conversation
import domain.model.ConversationParticipant
import domain.model.ConversationType
import domain.model.MessagePreview
import domain.model.MessageType
import domain.model.ParticipantRole
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.UUID

class ConversationRepositoryImpl : ConversationRepository {

    // ── Row → Domain ──────────────────────────────────────────
    private fun toConversation(
        row: ResultRow,
        participants: List<ConversationParticipant>,
        unread: Int,
        isPinned: Boolean,
        isMuted: Boolean
    ) = Conversation(
        id = row[ConversationsTable.id].toString(),
        type = ConversationType.valueOf(row[ConversationsTable.type]),
        name = row[ConversationsTable.name],
        imageUrl = row[ConversationsTable.imageUrl],
        participants = participants,
        unreadCount = unread,
        isPinned = isPinned,
        isMuted = isMuted,
        createdAt = row[ConversationsTable.createdAt].toString(),
        lastMessageAt = row[ConversationsTable.lastMessageAt]?.toString(),
        lastMessage = buildPreview(row)
    )

    private fun buildPreview(row: ResultRow): MessagePreview? {
        val preview = row[ConversationsTable.lastMessagePreview] ?: return null
        val type    = row[ConversationsTable.lastMessageType]?.let { MessageType.valueOf(it) } ?: return null
        return MessagePreview(
            id = "",
            content = preview,
            type = type,
            senderId = "",
            timestamp = row[ConversationsTable.lastMessageAt]?.toString() ?: ""
        )
    }

    private suspend fun loadParticipants(convId: UUID): List<ConversationParticipant> =
        newSuspendedTransaction {
            (ConversationParticipantsTable innerJoin UsersTable)
                .selectAll()
                .where { ConversationParticipantsTable.conversationId eq convId }
                .map { r ->
                    ConversationParticipant(
                        userId = r[UsersTable.id].toString(),
                        name = r[UsersTable.name],
                        profileImageUrl = r[UsersTable.profileImageUrl],
                        role = ParticipantRole.valueOf(r[ConversationParticipantsTable.role]),
                        joinedAt = r[ConversationParticipantsTable.joinedAt].toString()
                    )
                }
        }

    override suspend fun createPrivateConversation(userId1: String, userId2: String): Conversation {
        // تحقق من وجود محادثة سابقة بين الاثنين
        getPrivateConversation(userId1, userId2)?.let { return it }

        return newSuspendedTransaction {
            val id = ConversationsTable.insert {
                it[type]      = "PRIVATE"
                it[createdAt] = LocalDateTime.now()
            } get ConversationsTable.id

            listOf(userId1, userId2).forEach { uid ->
                ConversationParticipantsTable.insert {
                    it[conversationId] = id
                    it[userId]         = UUID.fromString(uid)
                    it[role]           = "MEMBER"
                }
            }

            val row = ConversationsTable.selectAll()
                .where { ConversationsTable.id eq id }.single()
            val participants = loadParticipants(id)
            toConversation(row, participants, 0, false, false)
        }
    }

    override suspend fun createGroupConversation(
        name: String,
        imageUrl: String?,
        creatorId: String,
        participantIds: List<String>
    ): Conversation = newSuspendedTransaction {
        val id = ConversationsTable.insert {
            it[ConversationsTable.type]     = "GROUP"
            it[ConversationsTable.name]     = name
            it[ConversationsTable.imageUrl] = imageUrl
        } get ConversationsTable.id

        // المنشئ كـ OWNER
        ConversationParticipantsTable.insert {
            it[conversationId] = id
            it[userId]         = UUID.fromString(creatorId)
            it[role]           = "OWNER"
        }
        // باقي الأعضاء
        participantIds.filter { it != creatorId }.forEach { uid ->
            ConversationParticipantsTable.insert {
                it[conversationId] = id
                it[userId]         = UUID.fromString(uid)
                it[role]           = "MEMBER"
            }
        }

        val row = ConversationsTable.selectAll().where { ConversationsTable.id eq id }.single()
        val participants = loadParticipants(id)
        toConversation(row, participants, 0, false, false)
    }

    override suspend fun getConversationById(id: String, currentUserId: String): Conversation? =
        newSuspendedTransaction {
            val uid  = UUID.fromString(id)
            val row  = ConversationsTable.selectAll().where { ConversationsTable.id eq uid }.singleOrNull() ?: return@newSuspendedTransaction null
            val pRow = ConversationParticipantsTable.selectAll().where {
                (ConversationParticipantsTable.conversationId eq uid) and
                (ConversationParticipantsTable.userId eq UUID.fromString(currentUserId))
            }.singleOrNull() ?: return@newSuspendedTransaction null
            val participants = loadParticipants(uid)
            val unread = getUnreadCount(id, currentUserId)
            toConversation(row, participants, unread, pRow[ConversationParticipantsTable.isPinned], pRow[ConversationParticipantsTable.isMuted])
        }

    override suspend fun getUserConversations(userId: String): List<Conversation> =
        newSuspendedTransaction {
            val uid = UUID.fromString(userId)
            val myConvIds = ConversationParticipantsTable.selectAll()
                .where { ConversationParticipantsTable.userId eq uid }
                .associate {
                    it[ConversationParticipantsTable.conversationId] to
                    Pair(it[ConversationParticipantsTable.isPinned], it[ConversationParticipantsTable.isMuted])
                }

            ConversationsTable.selectAll()
                .where { ConversationsTable.id inList myConvIds.keys }
                .orderBy(ConversationsTable.lastMessageAt, SortOrder.DESC_NULLS_LAST)
                .map { row ->
                    val convId = row[ConversationsTable.id]
                    val (pinned, muted) = myConvIds[convId] ?: Pair(false, false)
                    val participants = loadParticipants(convId)
                    val unread = MessagesTable.selectAll().where {
                        (MessagesTable.conversationId eq convId) and
                        (MessagesTable.senderId neq uid) and
                        (MessagesTable.isDeleted eq false)
                    }.count().toInt()
                    toConversation(row, participants, unread, pinned, muted)
                }
        }

    override suspend fun updateLastMessage(
        conversationId: String,
        preview: String,
        type: MessageType
    ) = newSuspendedTransaction {
        ConversationsTable.update({ ConversationsTable.id eq UUID.fromString(conversationId) }) {
            it[lastMessagePreview] = preview.take(100)
            it[lastMessageType]    = type.name
            it[lastMessageAt]      = LocalDateTime.now()
        }
        Unit
    }

    override suspend fun getUnreadCount(conversationId: String, userId: String): Int =
        newSuspendedTransaction {
            val uid   = UUID.fromString(userId)
            val convId = UUID.fromString(conversationId)
            val lastRead = ConversationParticipantsTable.selectAll().where {
                (ConversationParticipantsTable.conversationId eq convId) and
                (ConversationParticipantsTable.userId eq uid)
            }.singleOrNull()?.get(ConversationParticipantsTable.lastReadAt)

            MessagesTable.selectAll().where {
                (MessagesTable.conversationId eq convId) and
                (MessagesTable.senderId neq uid) and
                (MessagesTable.isDeleted eq false) and
                if (lastRead != null) (MessagesTable.createdAt greater lastRead) else Op.TRUE
            }.count().toInt()
        }

    override suspend fun markAsRead(conversationId: String, userId: String) =
        newSuspendedTransaction {
            ConversationParticipantsTable.update({
                (ConversationParticipantsTable.conversationId eq UUID.fromString(conversationId)) and
                (ConversationParticipantsTable.userId eq UUID.fromString(userId))
            }) {
                it[lastReadAt] = LocalDateTime.now()
            }
            Unit
        }

    override suspend fun pinConversation(conversationId: String, userId: String, isPinned: Boolean) =
        newSuspendedTransaction {
            ConversationParticipantsTable.update({
                (ConversationParticipantsTable.conversationId eq UUID.fromString(conversationId)) and
                (ConversationParticipantsTable.userId eq UUID.fromString(userId))
            }) { it[ConversationParticipantsTable.isPinned] = isPinned }
            Unit
        }

    override suspend fun muteConversation(conversationId: String, userId: String, isMuted: Boolean) =
        newSuspendedTransaction {
            ConversationParticipantsTable.update({
                (ConversationParticipantsTable.conversationId eq UUID.fromString(conversationId)) and
                (ConversationParticipantsTable.userId eq UUID.fromString(userId))
            }) { it[ConversationParticipantsTable.isMuted] = isMuted }
            Unit
        }

    override suspend fun deleteConversation(conversationId: String, userId: String) =
        newSuspendedTransaction {
            ConversationParticipantsTable.deleteWhere {
                (ConversationParticipantsTable.conversationId eq UUID.fromString(conversationId)) and
                (ConversationParticipantsTable.userId eq UUID.fromString(userId))
            }
            Unit
        }

    override suspend fun getPrivateConversation(userId1: String, userId2: String): Conversation? =
        newSuspendedTransaction {
            val uid1 = UUID.fromString(userId1)
            val uid2 = UUID.fromString(userId2)
            val ids1 = ConversationParticipantsTable.selectAll()
                .where { ConversationParticipantsTable.userId eq uid1 }
                .map { it[ConversationParticipantsTable.conversationId] }.toSet()
            val ids2 = ConversationParticipantsTable.selectAll()
                .where { ConversationParticipantsTable.userId eq uid2 }
                .map { it[ConversationParticipantsTable.conversationId] }.toSet()
            val shared = ids1.intersect(ids2)

            ConversationsTable.selectAll().where {
                (ConversationsTable.id inList shared) and
                (ConversationsTable.type eq "PRIVATE")
            }.singleOrNull()?.let { row ->
                val convId = row[ConversationsTable.id]
                val p = loadParticipants(convId)
                toConversation(row, p, 0, false, false)
            }
        }

    override suspend fun isParticipant(conversationId: String, userId: String): Boolean =
        newSuspendedTransaction {
            ConversationParticipantsTable.selectAll().where {
                (ConversationParticipantsTable.conversationId eq UUID.fromString(conversationId)) and
                (ConversationParticipantsTable.userId eq UUID.fromString(userId))
            }.count() > 0
        }
}
