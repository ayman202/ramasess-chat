package presentation.chat

import core.security.JwtService
import domain.repository.ContactRepository
import domain.repository.ConversationRepository
import domain.repository.UserRepository
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

private val log = LoggerFactory.getLogger("WebSocketHandler")

fun Application.configureWebSocketRoutes() {
    val jwtService    by inject<JwtService>()
    val userRepo      by inject<UserRepository>()
    val contactRepo   by inject<ContactRepository>()
    val convRepo      by inject<ConversationRepository>()

    routing {
        // ws://host/ws/chat?token=ACCESS_TOKEN
        webSocket("/ws/chat") {
            // ── 1. المصادقة ────────────────────────────────
            val token  = call.request.queryParameters["token"]
            val userId = token?.let { jwtService.verifyToken(it) }

            if (userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                return@webSocket
            }

            // ── 2. تسجيل الجلسة ───────────────────────────
            ChatSessionManager.addSession(userId, this)
            userRepo.updateOnlineStatus(userId, true)

            // إعلام الأصدقاء بأنه أونلاين
            notifyContactsStatus(userId, true, contactRepo)

            try {
                // ── 3. استقبال الرسائل ─────────────────────
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        handleIncomingFrame(frame.readText(), userId, convRepo)
                    }
                }
            } catch (e: Exception) {
                log.warn("WS error for $userId: ${e.message}")
            } finally {
                // ── 4. تنظيف عند قطع الاتصال ──────────────
                ChatSessionManager.removeSession(userId)
                userRepo.updateOnlineStatus(userId, false)
                userRepo.updateLastSeen(userId)
                notifyContactsStatus(userId, false, contactRepo)
            }
        }
    }
}

// ── معالجة الـ Frames الواردة من العميل ──────────────────────
private suspend fun DefaultWebSocketServerSession.handleIncomingFrame(
    text: String,
    userId: String,
    convRepo: ConversationRepository
) {
    try {
        val json   = Json { ignoreUnknownKeys = true }
        val obj    = json.parseToJsonElement(text).jsonObject
        val type   = obj["type"]?.jsonPrimitive?.content ?: return

        when (type) {
            // العميل يرسل ping → نرد بـ pong
            "ping" -> ChatSessionManager.sendTo(userId, WsPong())

            // مؤشر الكتابة
            "typing" -> {
                val convId   = obj["conversationId"]?.jsonPrimitive?.content ?: return
                val isTyping = obj["isTyping"]?.jsonPrimitive?.boolean ?: false

                // تأكد إن المستخدم مشترك في المحادثة
                if (!convRepo.isParticipant(convId, userId)) return

                val conv = convRepo.getConversationById(convId, userId) ?: return
                val participantIds = conv.participants.map { it.userId }.filter { it != userId }

                ChatSessionManager.sendToAll(
                    participantIds,
                    WsTyping(
                        conversationId = convId,
                        userId = userId,
                        userName = conv.participants.find { it.userId == userId }?.name,
                        isTyping = isTyping
                    )
                )
            }

            // قراءة الرسائل → تحديث الحالة لـ READ
            "mark_read" -> {
                val convId = obj["conversationId"]?.jsonPrimitive?.content ?: return
                if (!convRepo.isParticipant(convId, userId)) return

                val conv         = convRepo.getConversationById(convId, userId) ?: return
                val senderIds    = conv.participants.map { it.userId }.filter { it != userId }
                val lastMsgId    = obj["lastMessageId"]?.jsonPrimitive?.content ?: return

                ChatSessionManager.sendToAll(
                    senderIds,
                    WsMessageStatus(
                        messageId = lastMsgId,
                        conversationId = convId,
                        status = "READ"
                    )
                )
            }
        }
    } catch (e: Exception) {
        log.error("Error parsing WS frame from $userId: ${e.message}")
    }
}

// ── إعلام جهات الاتصال بتغيير الحالة ─────────────────────────
private suspend fun notifyContactsStatus(
    userId: String,
    isOnline: Boolean,
    contactRepo: ContactRepository
) {
    try {
        val contacts = contactRepo.getUserContacts(userId)
        val event    = WsOnlineStatus(
            userId = userId,
            isOnline = isOnline,
            lastSeen = if (!isOnline) LocalDateTime.now().toString() else null
        )
        ChatSessionManager.sendToAll(contacts.map { it.contactUserId }, event)
    } catch (e: Exception) {
        log.warn("Could not notify contacts for $userId: ${e.message}")
    }
}
