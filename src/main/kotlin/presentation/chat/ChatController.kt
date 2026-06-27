package presentation.chat

import domain.model.MessageType
import core.utils.BadRequestException
import core.utils.page
import core.utils.pageSize
import core.utils.pagedResponse
import core.utils.successResponse
import core.utils.userId
import domain.usecase.CreateConversationUseCase
import domain.usecase.DeleteMessageUseCase
import domain.usecase.EditMessageUseCase
import domain.usecase.GetConversationsUseCase
import domain.usecase.GetMessagesUseCase
import domain.usecase.SendMessageUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import presentation.chat.dto.CreateGroupConvRequest
import presentation.chat.dto.CreatePrivateConvRequest
import presentation.chat.dto.EditMessageRequest
import presentation.chat.dto.MuteRequest
import presentation.chat.dto.PinRequest
import presentation.chat.dto.ReactRequest
import presentation.chat.dto.SendMessageRequest

class ChatController(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val createConversationUseCase: CreateConversationUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val editMessageUseCase: EditMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase
) {

    // GET /conversations
    suspend fun getConversations(call: ApplicationCall) {
        val list = getConversationsUseCase(call.userId())
        call.respond(HttpStatusCode.OK, successResponse {
            putJsonArray("conversations") {
                list.forEach { conv ->
                    addJsonObject {
                        put("id",                conv.id)
                        put("type",              conv.type.name)
                        put("name",              conv.name)
                        put("imageUrl",          conv.imageUrl)
                        put("unreadCount",       conv.unreadCount)
                        put("isPinned",          conv.isPinned)
                        put("isMuted",           conv.isMuted)
                        put("createdAt",         conv.createdAt)
                        put("lastMessageAt",     conv.lastMessageAt)
                        conv.lastMessage?.let { lm ->
                            putJsonObject("lastMessage") {
                                put("id",        lm.id)
                                put("content",   lm.content)
                                put("type",      lm.type.name)
                                put("senderId",  lm.senderId)
                                put("timestamp", lm.timestamp)
                            }
                        }
                        putJsonArray("participants") {
                            conv.participants.forEach { p ->
                                addJsonObject {
                                    put("userId",          p.userId)
                                    put("name",            p.name)
                                    put("profileImageUrl", p.profileImageUrl)
                                    put("role",            p.role.name)
                                    put("joinedAt",        p.joinedAt)
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    // POST /conversations/private
    suspend fun createPrivateConversation(call: ApplicationCall) {
        val req  = call.receive<CreatePrivateConvRequest>()
        val conv = createConversationUseCase.createPrivate(call.userId(), req.userId)
        call.respond(HttpStatusCode.Created, successResponse {
            put("id",            conv.id)
            put("type",          conv.type.name)
            put("name",          conv.name)
            put("imageUrl",      conv.imageUrl)
            put("createdAt",     conv.createdAt)
            put("lastMessageAt", conv.lastMessageAt)
        })
    }

    // POST /conversations/group
    suspend fun createGroupConversation(call: ApplicationCall) {
        val req  = call.receive<CreateGroupConvRequest>()
        val conv = createConversationUseCase.createGroup(
            call.userId(), req.name, req.imageUrl, req.participantIds
        )
        call.respond(HttpStatusCode.Created, successResponse {
            put("id",        conv.id)
            put("type",      conv.type.name)
            put("name",      conv.name)
            put("imageUrl",  conv.imageUrl)
            put("createdAt", conv.createdAt)
        })
    }

    // GET /conversations/{id}/messages
    suspend fun getMessages(call: ApplicationCall) {
        val userId   = call.userId()
        val convId   = call.parameters["id"]
            ?: throw BadRequestException("Missing conversation id")
        val page     = call.page()
        val pageSize = call.pageSize()

        val messages = getMessagesUseCase(convId, userId, page, pageSize)

        call.respond(HttpStatusCode.OK, pagedResponse(
            total    = messages.size.toLong(),
            page     = page,
            pageSize = pageSize
        ) {
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("id",               msg.id)
                        put("conversationId",   msg.conversationId)
                        put("senderId",         msg.senderId)
                        put("senderName",       msg.senderName)
                        put("senderAvatar",     msg.senderAvatar)
                        put("content",          msg.content)
                        put("type",             msg.type.name)
                        put("status",           msg.status.name)
                        put("mediaUrl",         msg.mediaUrl)
                        put("mediaMimeType",    msg.mediaMimeType)
                        put("isEdited",         msg.isEdited)
                        put("isDeleted",        msg.isDeleted)
                        put("createdAt",        msg.createdAt)
                        put("editedAt",         msg.editedAt)
                        msg.replyTo?.let { r ->
                            putJsonObject("replyTo") {
                                put("messageId",     r.messageId)
                                put("senderName",    r.senderName)
                                put("previewContent",r.previewContent)
                            }
                        }
                        putJsonArray("reactions") {
                            msg.reactions.forEach { reaction ->
                                addJsonObject {
                                    put("emoji",    reaction.emoji)
                                    put("userId",   reaction.userId)
                                    put("userName", reaction.userName)
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    // POST /conversations/{id}/messages
    suspend fun sendMessage(call: ApplicationCall) {
        val userId = call.userId()
        val convId = call.parameters["id"]
            ?: throw BadRequestException("Missing conversation id")
        val req  = call.receive<SendMessageRequest>()
        val type = runCatching { MessageType.valueOf(req.type) }
            .getOrDefault(MessageType.TEXT)

        val message = sendMessageUseCase(
            conversationId = convId,
            senderId       = userId,
            content        = req.content,
            type           = type,
            replyToId      = req.replyToId,
            mediaUrl       = req.mediaUrl,
            mediaMimeType  = req.mediaMimeType
        )

        // إرسال الرسالة عبر WebSocket
        broadcastNewMessage(message, userId)

        call.respond(HttpStatusCode.Created, successResponse {
            put("id",             message.id)
            put("conversationId", message.conversationId)
            put("senderId",       message.senderId)
            put("content",        message.content)
            put("type",           message.type.name)
            put("status",         message.status.name)
            put("createdAt",      message.createdAt)
        })
    }

    // PUT /conversations/{id}/messages/{msgId}
    suspend fun editMessage(call: ApplicationCall) {
        val userId  = call.userId()
        val msgId   = call.parameters["msgId"]
            ?: throw BadRequestException("Missing message id")
        val req     = call.receive<EditMessageRequest>()
        val message = editMessageUseCase(msgId, req.content, userId)

        broadcastMessageEdited(message, userId)

        call.respond(HttpStatusCode.OK, successResponse {
            put("id",        message.id)
            put("content",   message.content)
            put("isEdited",  message.isEdited)
            put("editedAt",  message.editedAt)
        })
    }

    // DELETE /conversations/{id}/messages/{msgId}
    suspend fun deleteMessage(call: ApplicationCall) {
        val userId = call.userId()
        val convId = call.parameters["id"]
            ?: throw BadRequestException("Missing conversation id")
        val msgId  = call.parameters["msgId"]
            ?: throw BadRequestException("Missing message id")

        deleteMessageUseCase(msgId, userId)
        broadcastMessageDeleted(convId, msgId, userId)

        call.respond(HttpStatusCode.OK, successResponse {
            put("deleted", true)
        })
    }

    // POST /conversations/{id}/messages/{msgId}/react
    suspend fun reactToMessage(call: ApplicationCall) {
        val msgId = call.parameters["msgId"]
            ?: throw BadRequestException("Missing message id")
        val req   = call.receive<ReactRequest>()
        call.respond(HttpStatusCode.OK, successResponse {
            put("messageId", msgId)
            put("emoji",     req.emoji)
            put("reacted",   true)
        })
    }

    // POST /conversations/{id}/pin
    suspend fun pinConversation(call: ApplicationCall) {
        val req = call.receive<PinRequest>()
        call.respond(HttpStatusCode.OK, successResponse {
            put("isPinned", req.isPinned)
        })
    }

    // POST /conversations/{id}/mute
    suspend fun muteConversation(call: ApplicationCall) {
        val req = call.receive<MuteRequest>()
        call.respond(HttpStatusCode.OK, successResponse {
            put("isMuted", req.isMuted)
        })
    }

    // GET /conversations/{id}/messages/search
    suspend fun searchMessages(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, successResponse {
            putJsonArray("messages") {}
        })
    }
}

// ── WebSocket Broadcast Helpers ───────────────────────────────

private suspend fun broadcastNewMessage(
    message: domain.model.Message,
    senderId: String
) {
    ChatSessionManager.broadcast(
        WsNewMessage(
            messageId      = message.id,
            conversationId = message.conversationId,
            senderId       = message.senderId,
            senderName     = message.senderName,
            senderAvatar   = message.senderAvatar,
            content        = message.content,
            messageType    = message.type.name,
            replyToId      = message.replyTo?.messageId,
            mediaUrl       = message.mediaUrl,
            timestamp      = message.createdAt
        ),
        excludeUserId = senderId
    )
}

private suspend fun broadcastMessageEdited(
    message: domain.model.Message,
    senderId: String
) {
    ChatSessionManager.broadcast(
        WsMessageEdited(
            messageId      = message.id,
            conversationId = message.conversationId,
            newContent     = message.content,
            editedAt       = message.editedAt ?: ""
        ),
        excludeUserId = senderId
    )
}

private suspend fun broadcastMessageDeleted(
    convId: String,
    msgId: String,
    senderId: String
) {
    ChatSessionManager.broadcast(
        WsMessageDeleted(
            messageId      = msgId,
            conversationId = convId
        ),
        excludeUserId = senderId
    )
}
