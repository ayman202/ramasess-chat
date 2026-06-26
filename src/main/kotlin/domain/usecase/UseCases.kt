package domain.usecase

import core.security.JwtService
import core.security.OtpService
import core.utils.BadRequestException
import core.utils.UnauthorizedException
import domain.model.AuthTokens
import domain.model.OtpResult
import domain.model.User
import domain.repository.UserRepository
import domain.model.Message
import domain.model.MessageType
import core.utils.NotFoundException
import core.utils.ForbiddenException
import domain.repository.ConversationRepository
import domain.repository.MessageRepository
import domain.model.Contact
import domain.repository.ContactRepository
import domain.model.Story
import domain.model.StoryMediaType
import domain.repository.StoryRepository
import domain.model.Conversation



// ── SendOtpUseCase ────────────────────────────────────────────
class SendOtpUseCase(
    private val userRepo: UserRepository,
    private val otpService: OtpService
) {
    suspend operator fun invoke(phone: String): OtpResult {
        if (!phone.matches(Regex("^\\+[1-9]\\d{6,14}$")))
            throw BadRequestException("Invalid phone number format. Use E.164 format (+201234567890)")

        // أنشئ المستخدم إذا لم يكن موجوداً
        userRepo.createUser(phone)

        val otp = otpService.generateAndSend(phone)
        return OtpResult(
            phone      = phone,
            otpCode    = if (otpService.isMockMode()) otp else null,
            isMockMode = otpService.isMockMode()
        )
    }
}

// ── VerifyOtpUseCase ──────────────────────────────────────────
class VerifyOtpUseCase(
    private val userRepo: UserRepository,
    private val otpService: OtpService,
    private val jwtService: JwtService
) {
    suspend operator fun invoke(phone: String, otp: String): Pair<AuthTokens, User> {
        val valid = otpService.verify(phone, otp)
        if (!valid) throw BadRequestException("Invalid or expired OTP code")

        val user = userRepo.getUserByPhone(phone)
            ?: throw BadRequestException("User not found")

        val accessToken  = jwtService.generateAccessToken(user.id)
        val refreshToken = jwtService.generateRefreshToken(user.id)

        // احفظ الـ refresh token في الـ DB
        val expiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        userRepo.saveRefreshToken(user.id, refreshToken, expiresAt)

        return AuthTokens(accessToken, refreshToken, jwtService.getAccessExpirySeconds()) to user
    }
}

// ── RefreshTokenUseCase ───────────────────────────────────────
class RefreshTokenUseCase(
    private val userRepo: UserRepository,
    private val jwtService: JwtService
) {
    suspend operator fun invoke(refreshToken: String): AuthTokens {
        val userId = jwtService.verifyToken(refreshToken)
            ?: throw UnauthorizedException("Invalid refresh token")

        val stored = userRepo.getRefreshToken(userId)
        if (stored != refreshToken) throw UnauthorizedException("Refresh token revoked")

        val newAccess  = jwtService.generateAccessToken(userId)
        val newRefresh = jwtService.generateRefreshToken(userId)
        val expiresAt  = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        userRepo.saveRefreshToken(userId, newRefresh, expiresAt)

        return AuthTokens(newAccess, newRefresh, jwtService.getAccessExpirySeconds())
    }
}

// ── SetupProfileUseCase ───────────────────────────────────────
class SetupProfileUseCase(private val userRepo: UserRepository) {
    suspend operator fun invoke(
        userId: String,
        name: String,
        bio: String?,
        profileImageUrl: String?
    ): User {
        if (name.isBlank() || name.length < 2)
            throw BadRequestException("Name must be at least 2 characters")
        return userRepo.updateProfile(userId, name, bio, profileImageUrl)
            ?: throw BadRequestException("User not found")
    }
}

// ── LogoutUseCase ─────────────────────────────────────────────
class LogoutUseCase(private val userRepo: UserRepository) {
    suspend operator fun invoke(userId: String) {
        userRepo.deleteRefreshToken(userId)
        userRepo.updateOnlineStatus(userId, false)
        userRepo.updateLastSeen(userId)
    }
}

// =============================================================


class GetUserProfileUseCase(private val userRepo: UserRepository) {
    suspend operator fun invoke(userId: String): User =
        userRepo.getUserById(userId) ?: throw NotFoundException("User not found")
}

class UpdateUserProfileUseCase(private val userRepo: UserRepository) {
    suspend operator fun invoke(
        userId: String,
        name: String?,
        bio: String?,
        profileImageUrl: String?
    ): User = userRepo.updateProfile(userId, name, bio, profileImageUrl)
        ?: throw NotFoundException("User not found")
}

class SearchUsersUseCase(private val userRepo: UserRepository) {
    suspend operator fun invoke(query: String, currentUserId: String): List<User> {
        if (query.length < 2) return emptyList()
        return userRepo.searchUsers(query, currentUserId)
    }
}

// =============================================================



class SendMessageUseCase(
    private val messageRepo: MessageRepository,
    private val convRepo: ConversationRepository
) {
    suspend operator fun invoke(
        conversationId: String,
        senderId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        replyToId: String? = null,
        mediaUrl: String? = null,
        mediaMimeType: String? = null
    ): Message {
        if (!convRepo.isParticipant(conversationId, senderId))
            throw ForbiddenException("You are not a participant of this conversation")
        if (content.isBlank() && mediaUrl == null)
            throw BadRequestException("Message content cannot be empty")

        val message = messageRepo.sendMessage(
            conversationId, senderId, content, type, replyToId, mediaUrl, mediaMimeType
        )
        // تحديث آخر رسالة في المحادثة
        val preview = when (type) {
            MessageType.IMAGE    -> "📷 صورة"
            MessageType.VIDEO    -> "🎥 فيديو"
            MessageType.AUDIO    -> "🎵 صوت"
            MessageType.VOICE    -> "🎙️ رسالة صوتية"
            MessageType.DOCUMENT -> "📄 ملف"
            MessageType.STICKER  -> "😊 ملصق"
            else                 -> content.take(100)
        }
        convRepo.updateLastMessage(conversationId, preview, type)
        return message
    }
}

class GetMessagesUseCase(
    private val messageRepo: MessageRepository,
    private val convRepo: ConversationRepository
) {
    suspend operator fun invoke(
        conversationId: String,
        userId: String,
        page: Int,
        pageSize: Int
    ): List<Message> {
        if (!convRepo.isParticipant(conversationId, userId))
            throw ForbiddenException("Access denied")
        convRepo.markAsRead(conversationId, userId)
        messageRepo.updateConversationMessagesDelivered(conversationId, userId)
        return messageRepo.getMessages(conversationId, page, pageSize)
    }
}

class EditMessageUseCase(private val messageRepo: MessageRepository) {
    suspend operator fun invoke(messageId: String, newContent: String, userId: String): Message =
        messageRepo.editMessage(messageId, newContent, userId)
            ?: throw NotFoundException("Message not found or you don't have permission")
}

class DeleteMessageUseCase(private val messageRepo: MessageRepository) {
    suspend operator fun invoke(messageId: String, userId: String): Boolean =
        messageRepo.deleteMessage(messageId, userId).also {
            if (!it) throw NotFoundException("Message not found or you don't have permission")
        }
}

class ReactToMessageUseCase(private val messageRepo: MessageRepository) {
    suspend operator fun invoke(messageId: String, userId: String, emoji: String): Message =
        messageRepo.addReaction(messageId, userId, emoji)
            ?: throw NotFoundException("Message not found")
}

// =============================================================



class GetConversationsUseCase(private val convRepo: ConversationRepository) {
    suspend operator fun invoke(userId: String): List<Conversation> =
        convRepo.getUserConversations(userId)
}

class CreateConversationUseCase(
    private val convRepo: ConversationRepository,
    private val userRepo: UserRepository
) {
    // محادثة خاصة
    suspend fun createPrivate(currentUserId: String, targetUserId: String): Conversation {
        userRepo.getUserById(targetUserId) ?: throw NotFoundException("Target user not found")
        return convRepo.createPrivateConversation(currentUserId, targetUserId)
    }

    // محادثة جماعية
    suspend fun createGroup(
        creatorId: String,
        name: String,
        imageUrl: String?,
        participantIds: List<String>
    ): Conversation {
        if (name.isBlank()) throw BadRequestException("Group name required")
        return convRepo.createGroupConversation(name, imageUrl, creatorId, participantIds)
    }
}

class PinConversationUseCase(private val convRepo: ConversationRepository) {
    suspend operator fun invoke(conversationId: String, userId: String, isPinned: Boolean) =
        convRepo.pinConversation(conversationId, userId, isPinned)
}

class MuteConversationUseCase(private val convRepo: ConversationRepository) {
    suspend operator fun invoke(conversationId: String, userId: String, isMuted: Boolean) =
        convRepo.muteConversation(conversationId, userId, isMuted)
}

// =============================================================



class CreateStoryUseCase(private val storyRepo: StoryRepository) {
    suspend operator fun invoke(
        userId: String,
        mediaUrl: String,
        mediaType: StoryMediaType,
        caption: String?
    ): Story = storyRepo.createStory(userId, mediaUrl, mediaType, caption)
}

class GetStoriesUseCase(private val storyRepo: StoryRepository) {
    suspend operator fun invoke(viewerId: String): List<Story> =
        storyRepo.getActiveStoriesForUser(viewerId)
}

class DeleteStoryUseCase(private val storyRepo: StoryRepository) {
    suspend operator fun invoke(storyId: String, userId: String) {
        val deleted = storyRepo.deleteStory(storyId, userId)
        if (!deleted) throw NotFoundException("Story not found or access denied")
    }
}

class ViewStoryUseCase(private val storyRepo: StoryRepository) {
    suspend operator fun invoke(storyId: String, viewerId: String) =
        storyRepo.addView(storyId, viewerId)
}

// =============================================================




class SyncContactsUseCase(private val contactRepo: ContactRepository) {
    suspend operator fun invoke(userId: String, phones: List<String>): List<Contact> {
        if (phones.isEmpty()) return emptyList()
        return contactRepo.syncContacts(userId, phones)
    }
}

class GetContactsUseCase(private val contactRepo: ContactRepository) {
    suspend operator fun invoke(userId: String): List<Contact> =
        contactRepo.getUserContacts(userId)
}

class BlockContactUseCase(private val contactRepo: ContactRepository) {
    suspend fun block(userId: String, contactUserId: String) =
        contactRepo.blockContact(userId, contactUserId)

    suspend fun unblock(userId: String, contactUserId: String) =
        contactRepo.unblockContact(userId, contactUserId)
}
