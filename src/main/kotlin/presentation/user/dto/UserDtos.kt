package presentation.user.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val name: String?            = null,
    val bio: String?             = null,
    val profileImageUrl: String? = null
)

@Serializable
data class SavePublicKeyRequest(val publicKey: String)
