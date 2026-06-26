package presentation.chat.dto

import kotlinx.serialization.Serializable

// ── Requests ──────────────────────────────────────────────────

@Serializable
data class SendMessageRequest(
    val content: String        = "",
    val type: String           = "TEXT",
    val replyToId: String?     = null,
    val mediaUrl: String?      = null,
    val mediaMimeType: String? = null
)

@Serializable
data class EditMessageRequest(val content: String)

@Serializable
data class ReactRequest(val emoji: String)

@Serializable
data class CreatePrivateConvRequest(val userId: String)

@Serializable
data class CreateGroupConvRequest(
    val name: String,
    val imageUrl: String?      = null,
    val participantIds: List<String>
)

@Serializable
data class PinRequest(val isPinned: Boolean)

@Serializable
data class MuteRequest(val isMuted: Boolean)
