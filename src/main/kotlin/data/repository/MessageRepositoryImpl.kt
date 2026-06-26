package data.repository

import domain.repository.MessageRepository
import data.database.tables.MessageReactionsTable
import data.database.tables.MessagesTable
import data.database.tables.UsersTable
import domain.model.Message
import domain.model.MessageStatus
import domain.model.MessageType
import domain.model.Reaction
import domain.model.ReplyInfo
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.UUID


class MessageRepositoryImpl : MessageRepository {
    private fun toMessage(row: ResultRow, reactions: List<Reaction>, replyInfo: ReplyInfo?) = Message(
        id = row[MessagesTable.id].toString(),
        conversationId = row[MessagesTable.conversationId].toString(),
        senderId = row[MessagesTable.senderId].toString(),
        senderName = row.getOrNull(UsersTable.name),
        senderAvatar = row.getOrNull(UsersTable.profileImageUrl),
        content = if (row[MessagesTable.isDeleted]) "🚫 تم حذف هذه الرسالة" else row[MessagesTable.content],
        type = MessageType.valueOf(row[MessagesTable.type]),
        status = MessageStatus.valueOf(row[MessagesTable.status]),
        replyTo = replyInfo,
        reactions = reactions,
        mediaUrl = row[MessagesTable.mediaUrl],
        mediaMimeType = row[MessagesTable.mediaMimeType],
        mediaThumbnailUrl = row[MessagesTable.mediaThumbnailUrl],
        isEdited = row[MessagesTable.isEdited],
        isDeleted = row[MessagesTable.isDeleted],
        createdAt = row[MessagesTable.createdAt].toString(),
        editedAt = row[MessagesTable.editedAt]?.toString()
    )

    private suspend fun loadReactions(messageId: UUID): List<Reaction> =
        newSuspendedTransaction {
            (MessageReactionsTable innerJoin UsersTable)
                .selectAll()
                .where { MessageReactionsTable.messageId eq messageId }
                .map { r ->
                    Reaction(
                        emoji = r[MessageReactionsTable.emoji],
                        userId = r[UsersTable.id].toString(),
                        userName = r[UsersTable.name]
                    )
                }
        }

    private suspend fun loadReplyInfo(replyToId: UUID?): ReplyInfo? {
        if (replyToId == null) return null
        return newSuspendedTransaction {
            val replyRow = (MessagesTable leftJoin UsersTable)
                .selectAll()
                .where { MessagesTable.id eq replyToId }
                .singleOrNull() ?: return@newSuspendedTransaction null
            ReplyInfo(
                messageId = replyToId.toString(),
                senderName = replyRow.getOrNull(UsersTable.name),
                previewContent = replyRow[MessagesTable.content].take(80)
            )
        }
    }

    override suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        content: String,
        type: MessageType,
        replyToId: String?,
        mediaUrl: String?,
        mediaMimeType: String?
    ): Message = newSuspendedTransaction {
        val msgId = MessagesTable.insert {
            it[MessagesTable.conversationId] = UUID.fromString(conversationId)
            it[MessagesTable.senderId]       = UUID.fromString(senderId)
            it[MessagesTable.content]        = content
            it[MessagesTable.type]           = type.name
            it[MessagesTable.status]         = MessageStatus.SENT.name
            it[MessagesTable.replyToId]      = replyToId?.let { r -> UUID.fromString(r) }
            it[MessagesTable.mediaUrl]       = mediaUrl
            it[MessagesTable.mediaMimeType]  = mediaMimeType
        } get MessagesTable.id

        val row = (MessagesTable leftJoin UsersTable)
            .selectAll().where { MessagesTable.id eq msgId }.single()
        val replyInfo = loadReplyInfo(row[MessagesTable.replyToId])
        toMessage(row, emptyList(), replyInfo)
    }

    override suspend fun getMessages(
        conversationId: String,
        page: Int,
        pageSize: Int
    ): List<Message> = newSuspendedTransaction {
        val convId = UUID.fromString(conversationId)
        val offset = ((page - 1) * pageSize).toLong()

        val rows = (MessagesTable leftJoin UsersTable)
            .selectAll()
            .where { MessagesTable.conversationId eq convId }
            .orderBy(MessagesTable.createdAt, SortOrder.DESC)
            .limit(pageSize).offset(offset)
            .toList()

        rows.map { row ->
            val msgId     = row[MessagesTable.id]
            val reactions = loadReactions(msgId)
            val replyInfo = loadReplyInfo(row[MessagesTable.replyToId])
            toMessage(row, reactions, replyInfo)
        }
    }

    override suspend fun editMessage(messageId: String, newContent: String, userId: String): Message? =
        newSuspendedTransaction {
            val mid = UUID.fromString(messageId)
            val uid = UUID.fromString(userId)

            val existing = MessagesTable.selectAll()
                .where { (MessagesTable.id eq mid) and (MessagesTable.senderId eq uid) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            if (existing[MessagesTable.isDeleted]) return@newSuspendedTransaction null

            MessagesTable.update({ MessagesTable.id eq mid }) {
                it[content]  = newContent
                it[isEdited] = true
                it[editedAt] = LocalDateTime.now()
            }

            val row = (MessagesTable leftJoin UsersTable)
                .selectAll().where { MessagesTable.id eq mid }.single()
            val reactions = loadReactions(mid)
            val replyInfo = loadReplyInfo(row[MessagesTable.replyToId])
            toMessage(row, reactions, replyInfo)
        }

    override suspend fun deleteMessage(messageId: String, userId: String): Boolean =
        newSuspendedTransaction {
            val mid = UUID.fromString(messageId)
            val uid = UUID.fromString(userId)
            val updated = MessagesTable.update({
                (MessagesTable.id eq mid) and (MessagesTable.senderId eq uid)
            }) {
                it[isDeleted] = true
                it[content]   = ""
            }
            updated > 0
        }

    override suspend fun addReaction(messageId: String, userId: String, emoji: String): Message? =
        newSuspendedTransaction {
            val mid = UUID.fromString(messageId)
            val uid = UUID.fromString(userId)

            // upsert - إذا كان موجوداً احذفه (toggle)
            val existing = MessageReactionsTable.selectAll().where {
                (MessageReactionsTable.messageId eq mid) and
                (MessageReactionsTable.userId eq uid) and
                (MessageReactionsTable.emoji eq emoji)
            }.count()

            if (existing > 0) {
                MessageReactionsTable.deleteWhere {
                    (MessageReactionsTable.messageId eq mid) and
                    (MessageReactionsTable.userId eq uid) and
                    (MessageReactionsTable.emoji eq emoji)
                }
            } else {
                MessageReactionsTable.insert {
                    it[MessageReactionsTable.messageId] = mid
                    it[MessageReactionsTable.userId]    = uid
                    it[MessageReactionsTable.emoji]     = emoji
                }
            }

            val row = (MessagesTable leftJoin UsersTable)
                .selectAll().where { MessagesTable.id eq mid }.singleOrNull() ?: return@newSuspendedTransaction null
            val reactions = loadReactions(mid)
            val replyInfo = loadReplyInfo(row[MessagesTable.replyToId])
            toMessage(row, reactions, replyInfo)
        }

    override suspend fun removeReaction(messageId: String, userId: String, emoji: String): Message? =
        newSuspendedTransaction {
            val mid = UUID.fromString(messageId)
            MessageReactionsTable.deleteWhere {
                (MessageReactionsTable.messageId eq mid) and
                (MessageReactionsTable.userId eq UUID.fromString(userId)) and
                (MessageReactionsTable.emoji eq emoji)
            }
            val row = (MessagesTable leftJoin UsersTable)
                .selectAll().where { MessagesTable.id eq mid }.singleOrNull() ?: return@newSuspendedTransaction null
            val reactions = loadReactions(mid)
            val replyInfo = loadReplyInfo(row[MessagesTable.replyToId])
            toMessage(row, reactions, replyInfo)
        }

    override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) =
        newSuspendedTransaction {
            MessagesTable.update({ MessagesTable.id eq UUID.fromString(messageId) }) {
                it[MessagesTable.status] = status.name
            }
            Unit
        }

    override suspend fun updateConversationMessagesDelivered(conversationId: String, userId: String) =
        newSuspendedTransaction {
            MessagesTable.update({
                (MessagesTable.conversationId eq UUID.fromString(conversationId)) and
                (MessagesTable.senderId neq UUID.fromString(userId)) and
                (MessagesTable.status eq MessageStatus.SENT.name)
            }) {
                it[status] = MessageStatus.DELIVERED.name
            }
            Unit
        }

    override suspend fun searchMessages(conversationId: String, query: String): List<Message> =
        newSuspendedTransaction {
            val convId = UUID.fromString(conversationId)
            (MessagesTable leftJoin UsersTable)
                .selectAll()
                .where {
                    (MessagesTable.conversationId eq convId) and
                    (MessagesTable.isDeleted eq false) and
                    (MessagesTable.content like "%$query%")
                }
                .orderBy(MessagesTable.createdAt, SortOrder.DESC)
                .limit(50)
                .map { row ->
                    val mid = row[MessagesTable.id]
                    val reactions = loadReactions(mid)
                    val replyInfo = loadReplyInfo(row[MessagesTable.replyToId])
                    toMessage(row, reactions, replyInfo)
                }
        }
}
