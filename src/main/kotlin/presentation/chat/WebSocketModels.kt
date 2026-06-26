package presentation.chat

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

// ─────────────────────────────────────────────────────────────
// WebSocket Message Types  (كل رسالة ليها type محدد)
// ─────────────────────────────────────────────────────────────
@Serializable
sealed class WsEvent {
    abstract val type: String
}

@Serializable
@SerialName("new_message")
data class WsNewMessage(
    override val type: String = "new_message",
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String?,
    val senderAvatar: String?,
    val content: String,
    val messageType: String,
    val replyToId: String?,
    val mediaUrl: String?,
    val timestamp: String
) : WsEvent()

@Serializable
@SerialName("message_edited")
data class WsMessageEdited(
    override val type: String = "message_edited",
    val messageId: String,
    val conversationId: String,
    val newContent: String,
    val editedAt: String
) : WsEvent()

@Serializable
@SerialName("message_deleted")
data class WsMessageDeleted(
    override val type: String = "message_deleted",
    val messageId: String,
    val conversationId: String
) : WsEvent()

@Serializable
@SerialName("message_reaction")
data class WsMessageReaction(
    override val type: String = "message_reaction",
    val messageId: String,
    val conversationId: String,
    val userId: String,
    val emoji: String,
    val isAdded: Boolean
) : WsEvent()

@Serializable
@SerialName("message_status")
data class WsMessageStatus(
    override val type: String = "message_status",
    val messageId: String,
    val conversationId: String,
    val status: String   // DELIVERED | READ
) : WsEvent()

@Serializable
@SerialName("typing")
data class WsTyping(
    override val type: String = "typing",
    val conversationId: String,
    val userId: String,
    val userName: String?,
    val isTyping: Boolean
) : WsEvent()

@Serializable
@SerialName("online_status")
data class WsOnlineStatus(
    override val type: String = "online_status",
    val userId: String,
    val isOnline: Boolean,
    val lastSeen: String?
) : WsEvent()

@Serializable
@SerialName("story_new")
data class WsNewStory(
    override val type: String = "story_new",
    val storyId: String,
    val userId: String,
    val userName: String?,
    val mediaUrl: String,
    val mediaType: String
) : WsEvent()

@Serializable
@SerialName("ping")
data class WsPing(override val type: String = "ping") : WsEvent()

@Serializable
@SerialName("pong")
data class WsPong(override val type: String = "pong") : WsEvent()

// ─────────────────────────────────────────────────────────────
// ChatSessionManager  (ConcurrentHashMap → thread-safe)
// ─────────────────────────────────────────────────────────────
object ChatSessionManager {

    private val log = LoggerFactory.getLogger("ChatSessionManager")
    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults    = true
    }

    fun addSession(userId: String, session: DefaultWebSocketServerSession) {
        sessions[userId] = session
        log.info("🟢 User $userId connected  (total: ${sessions.size})")
    }

    fun removeSession(userId: String) {
        sessions.remove(userId)
        log.info("🔴 User $userId disconnected  (total: ${sessions.size})")
    }

    fun isOnline(userId: String) = sessions.containsKey(userId)

    fun onlineCount() = sessions.size

    // ── إرسال حدث لمستخدم واحد ────────────────────────────
    suspend fun sendTo(userId: String, event: WsEvent): Boolean {
        val session = sessions[userId] ?: return false
        return try {
            session.send(Frame.Text(encodeEvent(event)))
            true
        } catch (e: Exception) {
            log.warn("Failed to send to $userId: ${e.message}")
            removeSession(userId)
            false
        }
    }

    // ── إرسال لقائمة مستخدمين ─────────────────────────────
    suspend fun sendToAll(userIds: Collection<String>, event: WsEvent) {
        val payload = encodeEvent(event)
        userIds.forEach { uid ->
            val session = sessions[uid] ?: return@forEach
            try {
                session.send(Frame.Text(payload))
            } catch (e: Exception) {
                log.warn("Broadcast failed for $uid: ${e.message}")
                removeSession(uid)
            }
        }
    }

    // ── Broadcast لكل المتصلين (للإشعارات العامة) ─────────
    suspend fun broadcast(event: WsEvent, excludeUserId: String? = null) {
        val payload = encodeEvent(event)
        sessions.forEach { (uid, session) ->
            if (uid == excludeUserId) return@forEach
            try {
                session.send(Frame.Text(payload))
            } catch (e: Exception) {
                removeSession(uid)
            }
        }
    }

    private fun encodeEvent(event: WsEvent): String = when (event) {
        is WsNewMessage     -> json.encodeToString(WsNewMessage.serializer(), event)
        is WsMessageEdited  -> json.encodeToString(WsMessageEdited.serializer(), event)
        is WsMessageDeleted -> json.encodeToString(WsMessageDeleted.serializer(), event)
        is WsMessageReaction -> json.encodeToString(WsMessageReaction.serializer(), event)
        is WsMessageStatus  -> json.encodeToString(WsMessageStatus.serializer(), event)
        is WsTyping         -> json.encodeToString(WsTyping.serializer(), event)
        is WsOnlineStatus   -> json.encodeToString(WsOnlineStatus.serializer(), event)
        is WsNewStory       -> json.encodeToString(WsNewStory.serializer(), event)
        is WsPing           -> json.encodeToString(WsPing.serializer(), event)
        is WsPong           -> json.encodeToString(WsPong.serializer(), event)
    }
}
