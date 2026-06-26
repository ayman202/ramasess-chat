package data.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

// ─────────────────────────────────────────────────────────────
// Users
// ─────────────────────────────────────────────────────────────
object UsersTable : Table("users") {
    val id             = uuid("id").autoGenerate()
    val phone          = varchar("phone", 20).uniqueIndex()
    val name           = varchar("name", 100).nullable()
    val bio            = text("bio").nullable()
    val profileImageUrl = text("profile_image_url").nullable()
    val isOnline       = bool("is_online").default(false)
    val lastSeen       = datetime("last_seen").default(LocalDateTime.now())
    val createdAt      = datetime("created_at").default(LocalDateTime.now())
    val isVerified     = bool("is_verified").default(false)
    val publicKey      = text("public_key").nullable()

    override val primaryKey = PrimaryKey(id)
}

// ─────────────────────────────────────────────────────────────
// Refresh Tokens
// ─────────────────────────────────────────────────────────────
object RefreshTokensTable : Table("refresh_tokens") {
    val id        = uuid("id").autoGenerate()
    val userId    = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val token     = text("token").uniqueIndex()
    val expiresAt = long("expires_at")    // unix timestamp
    val createdAt = datetime("created_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

// ─────────────────────────────────────────────────────────────
// Conversations
// ─────────────────────────────────────────────────────────────
object ConversationsTable : Table("conversations") {
    val id                 = uuid("id").autoGenerate()
    val type               = varchar("type", 20)   // PRIVATE | GROUP
    val name               = varchar("name", 100).nullable()
    val imageUrl           = text("image_url").nullable()
    val createdAt          = datetime("created_at").default(LocalDateTime.now())
    val lastMessageAt      = datetime("last_message_at").nullable()
    val lastMessagePreview = text("last_message_preview").nullable()
    val lastMessageType    = varchar("last_message_type", 20).nullable()

    override val primaryKey = PrimaryKey(id)
}

// ─────────────────────────────────────────────────────────────
// Conversation Participants
// ─────────────────────────────────────────────────────────────

object ConversationParticipantsTable : Table("conversation_participants") {
    val conversationId = uuid("conversation_id")
        .references(ConversationsTable.id, onDelete = ReferenceOption.CASCADE)
    val userId   = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val role     = varchar("role", 20).default("MEMBER")   // OWNER | ADMIN | MEMBER
    val joinedAt = datetime("joined_at").default(LocalDateTime.now())
    val isPinned = bool("is_pinned").default(false)
    val isMuted  = bool("is_muted").default(false)
    val lastReadAt = datetime("last_read_at").nullable()

    override val primaryKey = PrimaryKey(conversationId, userId)
}

// ─────────────────────────────────────────────────────────────
// Messages
// ─────────────────────────────────────────────────────────────
object MessagesTable : Table("messages") {
    val id              = uuid("id").autoGenerate()
    val conversationId  = uuid("conversation_id")
        .references(ConversationsTable.id, onDelete = ReferenceOption.CASCADE)
    val senderId        = uuid("sender_id")
        .references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val content         = text("content")
    val type            = varchar("type", 20).default("TEXT")
    val status          = varchar("status", 20).default("SENT")
    val replyToId       = uuid("reply_to_id").nullable()
    val mediaUrl        = text("media_url").nullable()
    val mediaMimeType   = varchar("media_mime_type", 100).nullable()
    val mediaThumbnailUrl = text("media_thumbnail_url").nullable()
    val isEdited        = bool("is_edited").default(false)
    val isDeleted       = bool("is_deleted").default(false)
    val createdAt       = datetime("created_at").default(LocalDateTime.now())
    val editedAt        = datetime("edited_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

// ─────────────────────────────────────────────────────────────
// Message Reactions
// ─────────────────────────────────────────────────────────────
object MessageReactionsTable : Table("message_reactions") {
    val messageId = uuid("message_id")
        .references(MessagesTable.id, onDelete = ReferenceOption.CASCADE)
    val userId    = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val emoji     = varchar("emoji", 10)
    val createdAt = datetime("created_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(messageId, userId, emoji)
}

// ─────────────────────────────────────────────────────────────
// Stories
// ─────────────────────────────────────────────────────────────
object StoriesTable : Table("stories") {
    val id        = uuid("id").autoGenerate()
    val userId    = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val mediaUrl  = text("media_url")
    val mediaType = varchar("media_type", 10)   // IMAGE | VIDEO
    val caption   = text("caption").nullable()
    val viewsCount = integer("views_count").default(0)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val expiresAt = datetime("expires_at")

    override val primaryKey = PrimaryKey(id)
}

// ─────────────────────────────────────────────────────────────
// Story Views
// ─────────────────────────────────────────────────────────────
object StoryViewsTable : Table("story_views") {
    val storyId  = uuid("story_id").references(StoriesTable.id, onDelete = ReferenceOption.CASCADE)
    val viewerId = uuid("viewer_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val viewedAt = datetime("viewed_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(storyId, viewerId)
}

// ─────────────────────────────────────────────────────────────
// Contacts
// ─────────────────────────────────────────────────────────────
object ContactsTable : Table("contacts") {
    val userId        = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val contactUserId = uuid("contact_user_id")
        .references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val isBlocked = bool("is_blocked").default(false)
    val addedAt   = datetime("added_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(userId, contactUserId)
}
