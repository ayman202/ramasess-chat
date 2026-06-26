package domain.model

import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────
// User
// ─────────────────────────────────────────────────────────────
@Serializable
data class User(
    val id: String,
    val phone: String,
    val name: String?,
    val bio: String?,
    val profileImageUrl: String?,
    val isOnline: Boolean = false,
    val lastSeen: String?,          // ISO-8601 string للـ serialization
    val createdAt: String,
    val isVerified: Boolean = false,
    val publicKey: String? = null   // End-to-End encryption public key
)

// ─────────────────────────────────────────────────────────────
// Conversation
// ─────────────────────────────────────────────────────────────
@Serializable
data class Conversation(
    val id: String,
    val type: ConversationType,
    val name: String?,
    val imageUrl: String?,
    val participants: List<ConversationParticipant> = emptyList(),
    val lastMessage: MessagePreview? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val createdAt: String,
    val lastMessageAt: String?
)

@Serializable
data class ConversationParticipant(
    val userId: String,
    val name: String?,
    val profileImageUrl: String?,
    val role: ParticipantRole = ParticipantRole.MEMBER,
    val joinedAt: String
)

@Serializable
data class MessagePreview(
    val id: String,
    val content: String,
    val type: MessageType,
    val senderId: String,
    val timestamp: String
)

@Serializable
enum class ConversationType { PRIVATE, GROUP }

@Serializable
enum class ParticipantRole { OWNER, ADMIN, MEMBER }

// ─────────────────────────────────────────────────────────────
// Message
// ─────────────────────────────────────────────────────────────
@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String?,
    val senderAvatar: String?,
    val content: String,
    val type: MessageType,
    val status: MessageStatus,
    val replyTo: ReplyInfo? = null,
    val reactions: List<Reaction> = emptyList(),
    val mediaUrl: String? = null,
    val mediaMimeType: String? = null,
    val mediaThumbnailUrl: String? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: String,
    val editedAt: String? = null
)

@Serializable
data class ReplyInfo(
    val messageId: String,
    val senderName: String?,
    val previewContent: String
)

@Serializable
data class Reaction(
    val emoji: String,
    val userId: String,
    val userName: String?
)

@Serializable
enum class MessageType { TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT, STICKER, VOICE }

@Serializable
enum class MessageStatus { SENDING, SENT, DELIVERED, READ }

// ─────────────────────────────────────────────────────────────
// Story
// ─────────────────────────────────────────────────────────────
@Serializable
data class Story(
    val id: String,
    val userId: String,
    val userName: String?,
    val userAvatar: String?,
    val mediaUrl: String,
    val mediaType: StoryMediaType,
    val caption: String?,
    val viewsCount: Int = 0,
    val viewers: List<StoryViewer> = emptyList(),
    val isViewed: Boolean = false,
    val createdAt: String,
    val expiresAt: String
)

@Serializable
data class StoryViewer(
    val userId: String,
    val userName: String?,
    val viewedAt: String
)

@Serializable
enum class StoryMediaType { IMAGE, VIDEO }

// ─────────────────────────────────────────────────────────────
// Contact
// ─────────────────────────────────────────────────────────────
@Serializable
data class Contact(
    val id: String,
    val userId: String,
    val contactUserId: String,
    val name: String?,
    val phone: String,
    val profileImageUrl: String?,
    val isOnline: Boolean = false,
    val lastSeen: String?,
    val isBlocked: Boolean = false,
    val addedAt: String
)

// ─────────────────────────────────────────────────────────────
// Auth
// ─────────────────────────────────────────────────────────────
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long   // بالثواني
)

data class OtpResult(
    val phone: String,
    val otpCode: String? = null,  // يظهر فقط في الـ development
    val isMockMode: Boolean = false
)
