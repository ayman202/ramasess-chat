package domain.repository

import domain.model.Contact
import domain.model.Conversation
import domain.model.Message
import domain.model.MessageStatus
import domain.model.MessageType
import domain.model.Story
import domain.model.StoryMediaType
import domain.model.User

// ─────────────────────────────────────────────────────────────
// UserRepository
// ─────────────────────────────────────────────────────────────
interface UserRepository {
    suspend fun createUser(phone: String): User
    suspend fun getUserById(id: String): User?
    suspend fun getUserByPhone(phone: String): User?
    suspend fun updateProfile(
        userId: String,
        name: String?,
        bio: String?,
        profileImageUrl: String?
    ): User?
    suspend fun updateOnlineStatus(userId: String, isOnline: Boolean)
    suspend fun updateLastSeen(userId: String)
    suspend fun searchUsers(query: String, currentUserId: String): List<User>
    suspend fun getUsersByIds(ids: List<String>): List<User>
    suspend fun savePublicKey(userId: String, publicKey: String)
    suspend fun saveRefreshToken(userId: String, token: String, expiresAt: Long)
    suspend fun getRefreshToken(userId: String): String?
    suspend fun deleteRefreshToken(userId: String)
}

// ─────────────────────────────────────────────────────────────
// ConversationRepository
// ─────────────────────────────────────────────────────────────
interface ConversationRepository {
    suspend fun createPrivateConversation(userId1: String, userId2: String): Conversation
    suspend fun createGroupConversation(
        name: String,
        imageUrl: String?,
        creatorId: String,
        participantIds: List<String>
    ): Conversation
    suspend fun getConversationById(id: String, currentUserId: String): Conversation?
    suspend fun getUserConversations(userId: String): List<Conversation>
    suspend fun updateLastMessage(conversationId: String, preview: String, type: MessageType)
    suspend fun getUnreadCount(conversationId: String, userId: String): Int
    suspend fun markAsRead(conversationId: String, userId: String)
    suspend fun pinConversation(conversationId: String, userId: String, isPinned: Boolean)
    suspend fun muteConversation(conversationId: String, userId: String, isMuted: Boolean)
    suspend fun deleteConversation(conversationId: String, userId: String)
    suspend fun getPrivateConversation(userId1: String, userId2: String): Conversation?
    suspend fun isParticipant(conversationId: String, userId: String): Boolean
}

// ─────────────────────────────────────────────────────────────
// MessageRepository
// ─────────────────────────────────────────────────────────────
interface MessageRepository {
    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        content: String,
        type: MessageType,
        replyToId: String?,
        mediaUrl: String?,
        mediaMimeType: String?
    ): Message
    suspend fun getMessages(
        conversationId: String,
        page: Int,
        pageSize: Int
    ): List<Message>
    suspend fun editMessage(messageId: String, newContent: String, userId: String): Message?
    suspend fun deleteMessage(messageId: String, userId: String): Boolean
    suspend fun addReaction(messageId: String, userId: String, emoji: String): Message?
    suspend fun removeReaction(messageId: String, userId: String, emoji: String): Message?
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)
    suspend fun updateConversationMessagesDelivered(conversationId: String, userId: String)
    suspend fun searchMessages(conversationId: String, query: String): List<Message>
}

// ─────────────────────────────────────────────────────────────
// StoryRepository
// ─────────────────────────────────────────────────────────────
interface StoryRepository {
    suspend fun createStory(
        userId: String,
        mediaUrl: String,
        mediaType: StoryMediaType,
        caption: String?
    ): Story
    suspend fun getActiveStoriesForUser(viewerId: String): List<Story>
    suspend fun getUserStories(userId: String): List<Story>
    suspend fun addView(storyId: String, viewerId: String)
    suspend fun deleteStory(storyId: String, userId: String): Boolean
    suspend fun deleteExpiredStories()
}

// ─────────────────────────────────────────────────────────────
// ContactRepository
// ─────────────────────────────────────────────────────────────
interface ContactRepository {
    suspend fun syncContacts(userId: String, phones: List<String>): List<Contact>
    suspend fun getUserContacts(userId: String): List<Contact>
    suspend fun blockContact(userId: String, contactUserId: String)
    suspend fun unblockContact(userId: String, contactUserId: String)
    suspend fun isBlocked(userId: String, contactUserId: String): Boolean
    suspend fun getContactPhones(userId: String): List<String>
}
