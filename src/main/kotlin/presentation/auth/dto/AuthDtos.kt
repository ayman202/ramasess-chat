package presentation.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class SendOtpRequest(val phone: String)

@Serializable
data class VerifyOtpRequest(val phone: String, val otp: String)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class SetupProfileRequest(
    val name: String,
    val bio: String?             = null,
    val profileImageUrl: String? = null,
    val publicKey: String?       = null   // للـ E2E encryption
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val isNewUser: Boolean,
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: String,
    val phone: String,
    val name: String?,
    val bio: String?,
    val profileImageUrl: String?,
    val isVerified: Boolean
)
